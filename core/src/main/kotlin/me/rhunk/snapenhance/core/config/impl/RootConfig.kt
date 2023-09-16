package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.FeatureNotice

class RootConfig : ConfigContainer() {
    val downloader = container("downloader", DownloaderConfig()) { icon = "Download"}
    val userInterface = container("user_interface", UserInterfaceTweaks()) { icon = "RemoveRedEye"}
    val messaging = container("messaging", MessagingTweaks()) { icon = "Send" }
    val global = container("global", Global()) { icon = "MiscellaneousServices" }
    val rules = container("rules", Rules()) { icon = "Rule" }
    val camera = container("camera", Camera()) { icon = "Camera"}
    val streaksReminder = container("streaks_reminder", StreaksReminderConfig()) { icon = "Alarm" }
    val experimental = container("experimental", Experimental()) {
        icon = "Science"; addNotices(FeatureNotice.UNSTABLE)
    }
    val scripting = container("scripting", Scripting()) { icon = "DataObject" }
}