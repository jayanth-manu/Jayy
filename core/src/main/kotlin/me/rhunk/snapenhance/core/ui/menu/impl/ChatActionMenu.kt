package me.rhunk.snapenhance.core.ui.menu.impl

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.core.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.core.features.impl.experiments.ConvertMessageLocally
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.ViewTagState
import me.rhunk.snapenhance.core.ui.applyTheme
import me.rhunk.snapenhance.core.ui.menu.AbstractMenu
import me.rhunk.snapenhance.core.ui.triggerCloseTouchEvent
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.ktx.getDimens
import me.rhunk.snapenhance.core.util.ktx.vibrateLongPress


class ChatActionMenu : AbstractMenu() {
    private val viewTagState = ViewTagState()
    private val defaultGap by lazy { context.resources.getDimens("default_gap") }
    private val chatActionMenuItemMargin by lazy { context.resources.getDimens("chat_action_menu_item_margin") }
    private val actionMenuItemHeight by lazy { context.resources.getDimens("action_menu_item_height") }

    private fun createContainer(viewGroup: ViewGroup): LinearLayout {
        val parent = viewGroup.parent.parent as ViewGroup

        return LinearLayout(viewGroup.context).apply layout@{
            orientation = LinearLayout.VERTICAL
            layoutParams = MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                applyTheme(parent.width, true)
                setMargins(chatActionMenuItemMargin, 0, chatActionMenuItemMargin, defaultGap)
            }
        }
    }

    override fun init() {
        runCatching {
            if (!context.config.downloader.downloadContextMenu.get() && context.config.messaging.messageLogger.globalState != true && !context.isDeveloper) return
            context.androidContext.classLoader.loadClass("com.snap.messaging.chat.features.actionmenu.ActionMenuChatItemContainer")
                .hook("onMeasure", HookStage.BEFORE) { param ->
                    param.setArg(1,
                        View.MeasureSpec.makeMeasureSpec((context.resources.displayMetrics.heightPixels * 0.25).toInt(), View.MeasureSpec.AT_MOST)
                    )
                }
        }.onFailure {
            context.log.error("Failed to hook ActionMenuChatItemContainer: $it")
        }
    }

    override fun inject(parent: ViewGroup, view: View, viewConsumer: (View) -> Unit) {
        val viewGroup = parent.parent.parent as? ViewGroup ?: return
        if (viewTagState[viewGroup]) return
        //close the action menu using a touch event
        val closeActionMenu = {
            context.runOnUiThread {
                parent.triggerCloseTouchEvent()
            }
        }

        val messaging = context.feature(Messaging::class)
        val messageLogger = context.feature(MessageLogger::class)

        val buttonContainer = createContainer(viewGroup)

        val injectButton = { button: Button ->
            if (buttonContainer.childCount > 0) {
                buttonContainer.addView(View(viewGroup.context).apply {
                    layoutParams = MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        height = 1
                    }
                    setBackgroundColor(0x1A000000)
                })
            }

            with(button) {
                applyTheme(viewGroup.width, true)
                layoutParams = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    height = actionMenuItemHeight + defaultGap
                }
                buttonContainer.addView(this)
            }
        }

        if (context.config.downloader.downloadContextMenu.get()) {
            val mediaDownloader = context.feature(MediaDownloader::class)

            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.preview_button"]
                setOnClickListener {
                    closeActionMenu()
                    mediaDownloader.onMessageActionMenu(true)
                }
            })

            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.download_button"]
                setOnClickListener {
                    closeActionMenu()
                    mediaDownloader.onMessageActionMenu(false)
                }
                setOnLongClickListener {
                    closeActionMenu()
                    context.vibrateLongPress()
                    mediaDownloader.onMessageActionMenu(isPreviewMode = false, forceAllowDuplicate = true)
                    true
                }
            })
        }

        //delete logged message button
        if (context.config.messaging.messageLogger.globalState == true) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.delete_logged_message_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        messageLogger.deleteMessage(messaging.openedConversationUUID.toString(), messaging.lastFocusedMessageId)
                    }
                }
            })
        }

        if (context.config.experimental.editMessage.get() && messaging.conversationManager?.isEditMessageSupported() == true) {
            injectButton(Button(viewGroup.context).apply button@{
                text = "Edit Message"
                setOnClickListener {
                    messaging.conversationManager?.fetchMessage(
                        messaging.openedConversationUUID.toString(),
                        messaging.lastFocusedMessageId,
                        onSuccess = onSuccess@{ message ->
                            closeActionMenu()
                            if (message.senderId.toString() != this@ChatActionMenu.context.database.myUserId) {
                                this@ChatActionMenu.context.shortToast("You can only edit your own messages")
                                return@onSuccess
                            }

                            val editText = EditText(viewGroup.context).apply {
                                setText(ProtoReader(message.messageContent?.content ?: return@apply).getString(2, 1) ?: run {
                                    this@ChatActionMenu.context.shortToast("You can only edit text messages")
                                    return@onSuccess
                                })
                                setTextColor(resources.getColor(android.R.color.white, context.theme))
                                postDelayed({
                                    requestFocus()
                                    setSelection(text.length)
                                    context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                        .let { it as android.view.inputmethod.InputMethodManager }
                                        .showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                }, 200)
                            }

                            this@ChatActionMenu.context.runOnUiThread {
                                ViewAppearanceHelper.newAlertDialogBuilder(this@ChatActionMenu.context.mainActivity!!)
                                    .setPositiveButton("Save") { _, _ ->
                                        val newMessageContent = ProtoWriter().apply {
                                            from(2) { addString(1, editText.text.toString()) }
                                        }.toByteArray()
                                        message.messageContent?.content = newMessageContent
                                        messaging.conversationManager?.editMessage(
                                            message.messageDescriptor?.conversationId.toString(),
                                            message.messageDescriptor?.messageId ?: return@setPositiveButton,
                                            newMessageContent,
                                            onSuccess = {
                                                this@ChatActionMenu.context.coroutineScope.launch(Dispatchers.Main) {
                                                    message.messageMetadata?.isEdited = true
                                                    messaging.localUpdateMessage(
                                                        message.messageDescriptor?.conversationId.toString(),
                                                        message,
                                                        forceUpdate = true
                                                    )
                                                }
                                            },
                                            onError = {
                                                this@ChatActionMenu.context.shortToast("Failed to edit message: $it")
                                            }
                                        )
                                    }
                                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                                    .setView(editText)
                                    .setTitle("Edit message content")
                                    .show()
                            }
                        }
                    )
                }
            })
        }

        if (context.config.experimental.convertMessageLocally.get()) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.convert_message"]
                setOnClickListener {
                    closeActionMenu()
                    messaging.conversationManager?.fetchMessage(
                        messaging.openedConversationUUID.toString(),
                        messaging.lastFocusedMessageId,
                        onSuccess = {
                            this@ChatActionMenu.context.runOnUiThread {
                                runCatching {
                                    this@ChatActionMenu.context.feature(ConvertMessageLocally::class)
                                        .convertMessageInterface(it)
                                }.onFailure {
                                    this@ChatActionMenu.context.log.verbose("Failed to convert message: $it")
                                    this@ChatActionMenu.context.shortToast("Failed to edit message: $it")
                                }
                            }
                        },
                        onError = {
                            this@ChatActionMenu.context.shortToast("Failed to fetch message: $it")
                        }
                    )
                }
            })
        }


        viewGroup.addView(buttonContainer)
    }
}
