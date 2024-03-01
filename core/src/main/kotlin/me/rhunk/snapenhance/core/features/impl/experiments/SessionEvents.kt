package me.rhunk.snapenhance.core.features.impl.experiments

import me.rhunk.snapenhance.common.data.SessionMessageEvent
import me.rhunk.snapenhance.common.data.SessionEvent
import me.rhunk.snapenhance.common.data.SessionEventType
import me.rhunk.snapenhance.common.data.FriendPresenceState
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.wrapper.impl.toSnapUUID
import me.rhunk.snapenhance.nativelib.NativeLib
import java.lang.reflect.Method
import java.nio.ByteBuffer

class SessionEvents : Feature("Session Events", loadParams = FeatureLoadParams.INIT_SYNC) {
    private val conversationPresenceState = mutableMapOf<String, MutableMap<String, FriendPresenceState?>>() // conversationId -> (userId -> state)

    private fun handleVolatileEvent(protoReader: ProtoReader) {
        context.log.verbose("volatile event\n$protoReader")
    }

    private fun onConversationPresenceUpdate(conversationId: String, userId: String, oldState: FriendPresenceState?, currentState: FriendPresenceState?) {
        context.log.verbose("presence state for $userId in conversation $conversationId\n$currentState")
    }

    private fun onConversationMessagingEvent(event: SessionEvent) {
        context.log.verbose("conversation messaging event\n${event.type} in ${event.conversationId} from ${event.authorUserId}")
    }

    private fun handlePresenceEvent(protoReader: ProtoReader) {
        val conversationId = protoReader.getString(6) ?: return

        val presenceMap = conversationPresenceState.getOrPut(conversationId) { mutableMapOf() }.toMutableMap()
        val userIds = mutableSetOf<String>()

        protoReader.eachBuffer(4) {
            val participantUserId = getString(1)?.takeIf { it.contains(":") }?.substringBefore(":") ?: return@eachBuffer
            userIds.add(participantUserId)
            if (participantUserId == context.database.myUserId) return@eachBuffer
            val stateMap = getVarInt(2, 1)?.toString(2)?.padStart(16, '0')?.reversed()?.map { it == '1' } ?: return@eachBuffer

            presenceMap[participantUserId] = FriendPresenceState(
                bitmojiPresent = stateMap[0],
                typing = stateMap[4],
                wasTyping = stateMap[5],
                speaking = stateMap[6] && stateMap[4],
                peeking = stateMap[8]
            )
        }

        presenceMap.keys.filterNot { it in userIds }.forEach { presenceMap[it] = null }

        presenceMap.forEach { (userId, state) ->
            val oldState = conversationPresenceState[conversationId]?.get(userId)
            if (oldState != state) {
                onConversationPresenceUpdate(conversationId, userId, oldState, state)
            }
        }

        conversationPresenceState[conversationId] = presenceMap
    }

    private fun handleMessagingEvent(protoReader: ProtoReader) {
        // read receipts
        protoReader.followPath(12) {
            val conversationId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@followPath

            followPath(7) readReceipts@{
                val senderId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@readReceipts
                val serverMessageId = getVarInt(2, 2) ?: return@readReceipts

                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.MESSAGE_READ_RECEIPTS,
                        conversationId,
                        senderId,
                        serverMessageId,
                    )
                )
            }
        }

        protoReader.followPath(6, 2) {
            val conversationId = getByteArray(1, 1)?.toSnapUUID()?.toString() ?: return@followPath
            val senderId = getByteArray(3, 1)?.toSnapUUID()?.toString() ?: return@followPath
            val serverMessageId = getVarInt(2) ?: return@followPath

            if (contains(4)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.SNAP_OPENED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(13)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (getVarInt(13, 1) == 2L) SessionEventType.SNAP_REPLAYED_TWICE else SessionEventType.SNAP_REPLAYED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(6) || contains(7)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (contains(6)) SessionEventType.MESSAGE_SAVED else SessionEventType.MESSAGE_UNSAVED,
                        conversationId,
                        senderId,
                        serverMessageId
                    )
                )
            }

            if (contains(11) || contains(12)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        if (contains(11)) SessionEventType.SNAP_SCREENSHOT else SessionEventType.SNAP_SCREEN_RECORD,
                        conversationId,
                        senderId,
                        serverMessageId,
                    )
                )
            }

            followPath(16) {
                onConversationMessagingEvent(
                    SessionMessageEvent(
                        SessionEventType.MESSAGE_REACTION_ADD, conversationId, senderId, serverMessageId, reactionId = getVarInt(1, 1, 1)?.toInt() ?: -1
                    )
                )
            }

            if (contains(17)) {
                onConversationMessagingEvent(
                    SessionMessageEvent(SessionEventType.MESSAGE_REACTION_REMOVE, conversationId, senderId, serverMessageId)
                )
            }

            followPath(8) {
                onConversationMessagingEvent(
                    SessionMessageEvent(SessionEventType.MESSAGE_DELETED, conversationId, senderId, serverMessageId, messageData = getByteArray(1))
                )
            }
        }
    }

    override fun init() {
        val sessionEventsConfig = context.config.experimental.sessionEvents
        if (sessionEventsConfig.globalState != true) return

        if (sessionEventsConfig.allowRunningInBackground.get()) {
            findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy").apply {
                // prevent disabling events when the app is inactive
                hook("appStateChanged", HookStage.BEFORE) { param ->
                    if (param.arg<Any>(0).toString() == "INACTIVE") param.setResult(null)
                }
                // allow events when a notification is received
                hookConstructor(HookStage.AFTER) { param ->
                    methods.first { it.name == "appStateChanged" }.let { method ->
                        method.invoke(param.thisObject(), method.parameterTypes[0].enumConstants.first { it.toString() == "ACTIVE" })
                    }
                }
            }
        }

        if (sessionEventsConfig.captureDuplexEvents.get()) {
            val messageHandlerClass = findClass("com.snapchat.client.duplex.MessageHandler\$CppProxy").apply {
                hook("onReceive", HookStage.BEFORE) { param ->
                    param.setResult(null)

                    val byteBuffer = param.arg<ByteBuffer>(0)
                    val content = byteBuffer.let {
                        val bytes = ByteArray(it.limit())
                        it.get(bytes)
                        bytes
                    }
                    val reader = ProtoReader(content)
                    reader.getString(1, 1)?.let {
                        val eventData = reader.followPath(1, 2) ?: return@let
                        if (it == "volatile") {
                            handleVolatileEvent(eventData)
                            return@hook
                        }

                        if (it == "presence") {
                            handlePresenceEvent(eventData)
                            return@hook
                        }
                    }
                    handleMessagingEvent(reader)
                }
                hook("nativeDestroy", HookStage.BEFORE) { it.setResult(null) }
            }


            findClass("com.snapchat.client.messaging.Session").hook("create", HookStage.BEFORE) { param ->
                if (!NativeLib.initialized) {
                    context.log.warn("Can't register duplex message handler, native lib not initialized")
                    return@hook
                }

                val method = param.method() as Method
                val duplexClient = method.parameterTypes.indexOfFirst { it.name.endsWith("DuplexClient") }.let {
                    param.arg<Any>(it)
                }
                val dispatchQueue = method.parameterTypes.indexOfFirst { it.name.endsWith("DispatchQueue") }.let {
                    param.arg<Any>(it)
                }
                for (channel in arrayOf("pcs", "mcs")) {
                    duplexClient::class.java.methods.first {
                        it.name == "registerHandler"
                    }.invoke(
                        duplexClient,
                        channel,
                        messageHandlerClass.declaredConstructors.first().also { it.isAccessible = true }.newInstance(-1),
                        dispatchQueue
                    )
                }
            }
        }
    }
}