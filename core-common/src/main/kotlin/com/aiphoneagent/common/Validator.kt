package com.aiphoneagent.common

class Validator {
    fun verify(action: AgentAction, before: ScreenSnapshot, after: ScreenSnapshot): Boolean {
        return when (action) {
            is AgentAction.LaunchApp,
            AgentAction.Back,
            AgentAction.Home,
            AgentAction.Recents,
            AgentAction.OpenNotifications,
            AgentAction.ImeEnter,
            -> true
            is AgentAction.SetText -> after.containsText(action.text) || after.fingerprint() != before.fingerprint()
            is AgentAction.WaitFor -> {
                val present = after.root.find(action.selector) != null
                if (action.disappear) !present else present
            }
            is AgentAction.Tap,
            is AgentAction.LongPress,
            is AgentAction.Swipe,
            is AgentAction.Scroll,
            -> after.fingerprint() != before.fingerprint() || after.packageName != before.packageName
        }
    }
}
