package me.rhunk.snapenhance.core.features.impl.ui

import android.content.ContentValues
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.RuleState
import me.rhunk.snapenhance.core.database.DatabaseType
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID

class No1BFFConversations : MessagingRuleFeature("No1BFFConversations", MessagingRuleType.PINNO1BFF_CONVERSATION, loadParams = FeatureLoadParams.INIT_SYNC) {
    override fun init() {
        // TODO: Figure out how to toggle this feature on and off from within Snapchat by pressing the "Pin [user] as #1 BFF" button.
        //  I can't find the function that is called when the button is pressed. If you can, PLEASE assist with this!

        // TODO: Fix the fact that this hook isn't triggered until multiple "Clear Snapchat Caches" are ran.
        context.classCache.composerFriend.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val usr = param.arg<Any>(0)
            val conversationUUID = SnapUUID(instance.getObjectField("_conversationId"))

            // Debugging purposes.
            context.log.info("Friend constructor called")
            context.log.info(usr.getObjectField("_displayName"))

            // TODO: Check if the user does have the "#1 BFF" option enabled in SnapEnhance - steal this code from the "PinConversations" feature <3
            //  The reason we don't check this right now is because of debugging purposes. We want to get #1 BFF working first, then we can check if the user has the option enabled for this user.

            // TODO: Get this to actually work.
            //  So far, it doesn't work. Probably because the UI reads user details from database, and this isn't in database so the UI doesn't show the user as #1 BFF.
            instance.setObjectField("_isPinnedBestFriend", true)

            // TODO: Add the conversation to the database so it shows up in the UI.
            //  I'm not even sure where to start with this yet... 😬
        }

    }

    override fun getRuleState() = RuleState.WHITELIST
}