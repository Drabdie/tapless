package com.aiphoneagent.common

class RecoveryEngine {
    fun recover(failed: AgentAction, attempt: Int, screen: ScreenSnapshot?): RecoveryDecision {
        if (SecurityPolicy.isIrreversible(failed, screen)) return RecoveryDecision.Abort
        if (failed is AgentAction.LaunchApp || failed is AgentAction.Back || failed is AgentAction.Home) {
            return if (attempt >= 2) RecoveryDecision.Abort else RecoveryDecision.Retry(failed)
        }
        return when (attempt) {
            1 -> {
                val alternative = alternate(failed)
                if (alternative != null) {
                    RecoveryDecision.Retry(alternative)
                } else {
                    RecoveryDecision.ScrollAndRetry(scroll(failed), failed)
                }
            }
            2 -> RecoveryDecision.ScrollAndRetry(scroll(failed), baseAction(failed))
            3 -> RecoveryDecision.BackAndRetry(baseAction(failed))
            else -> RecoveryDecision.Abort
        }
    }

    private fun scroll(action: AgentAction): AgentAction {
        val selector = when (action) {
            is AgentAction.Tap -> action.selector
            is AgentAction.SetText -> action.selector
            is AgentAction.Scroll -> action.selector
            else -> ElementSelector.ByAnyText(emptyList())
        }
        return AgentAction.Scroll(selector, forward = true)
    }

    private fun baseAction(action: AgentAction): AgentAction = when (action) {
        is AgentAction.Tap -> action
        is AgentAction.SetText -> AgentAction.SetText(ElementSelector.ByFocusedEditable, action.text)
        else -> action
    }

    private fun alternate(action: AgentAction): AgentAction? = when (action) {
        is AgentAction.Tap -> alternateSelector(action.selector)?.let { AgentAction.Tap(it) }
        is AgentAction.LongPress -> alternateSelector(action.selector)?.let { AgentAction.LongPress(it, action.durationMs) }
        is AgentAction.SetText -> when (action.selector) {
            ElementSelector.ByFocusedEditable -> null
            else -> AgentAction.SetText(ElementSelector.ByFocusedEditable, action.text)
        }
        else -> null
    }

    private fun alternateSelector(selector: ElementSelector): ElementSelector? = when (selector) {
        is ElementSelector.ByText -> if (selector.exact) {
            ElementSelector.ByText(selector.text, exact = false)
        } else {
            ElementSelector.ByContentDescription(selector.text)
        }
        is ElementSelector.ByContentDescription -> ElementSelector.ByText(selector.desc, exact = false)
        is ElementSelector.ByResourceId -> ElementSelector.ByText(selector.id.substringAfterLast('/'), exact = false)
        is ElementSelector.ByAnyText -> selector.options.firstOrNull()?.let { ElementSelector.ByContentDescription(it) }
        else -> null
    }
}
