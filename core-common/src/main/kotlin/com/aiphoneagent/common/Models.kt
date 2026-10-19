package com.aiphoneagent.common

data class UiRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
}

data class UiNode(
    val id: Int? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val resourceId: String? = null,
    val bounds: UiRect = UiRect(0, 0, 0, 0),
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val focused: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false,
    val children: List<UiNode> = emptyList(),
) {
    fun flatten(): List<UiNode> = listOf(this) + children.flatMap { it.flatten() }

    fun matches(selector: ElementSelector): Boolean = when (selector) {
        is ElementSelector.ByText -> {
            val value = text.orEmpty()
            selector.text.isNotBlank() && if (selector.exact) {
                value.equals(selector.text, ignoreCase = true)
            } else {
                value.contains(selector.text, ignoreCase = true)
            }
        }
        is ElementSelector.ByResourceId -> {
            val id = resourceId.orEmpty()
            id.equals(selector.id, ignoreCase = true) ||
                id.endsWith("/${selector.id}", ignoreCase = true) ||
                id.endsWith(selector.id, ignoreCase = true)
        }
        is ElementSelector.ByContentDescription -> {
            val value = contentDescription.orEmpty()
            selector.desc.isNotBlank() && value.contains(selector.desc, ignoreCase = true)
        }
        is ElementSelector.ByCoordinates -> bounds.contains(selector.x, selector.y)
        is ElementSelector.ByAnyText -> selector.options.any { option ->
            option.isNotBlank() && (
                text?.contains(option, ignoreCase = true) == true ||
                    contentDescription?.contains(option, ignoreCase = true) == true
                )
        }
        ElementSelector.ByFocusedEditable -> editable && focused
    }

    fun find(selector: ElementSelector): UiNode? {
        if (matches(selector)) return this
        children.forEach { child ->
            child.find(selector)?.let { return it }
        }
        return null
    }

    fun findEditable(selector: ElementSelector): UiNode? {
        if (selector is ElementSelector.ByFocusedEditable) {
            return flatten().firstOrNull { it.editable && it.focused }
                ?: flatten().firstOrNull { it.editable }
        }
        val direct = find(selector)
        if (direct?.editable == true) return direct
        return flatten().firstOrNull { it.editable && it.focused }
            ?: flatten().firstOrNull { it.editable }
    }
}

data class ScreenSnapshot(
    val packageName: String,
    val root: UiNode,
    val timestamp: Long,
    val width: Int = 0,
    val height: Int = 0,
) {
    fun fingerprint(): String {
        val texts = root.flatten().mapNotNull { it.text?.take(48) }.take(80)
        return "$packageName|${root.flatten().size}|${texts.joinToString("§")}"
    }

    fun containsText(value: String): Boolean {
        if (value.isBlank()) return true
        return root.flatten().any {
            it.text?.contains(value, ignoreCase = true) == true ||
                it.contentDescription?.contains(value, ignoreCase = true) == true
        }
    }
}

sealed class ElementSelector {
    data class ByText(val text: String, val exact: Boolean = false) : ElementSelector()
    data class ByResourceId(val id: String) : ElementSelector()
    data class ByContentDescription(val desc: String) : ElementSelector()
    data class ByCoordinates(val x: Int, val y: Int) : ElementSelector()
    data class ByAnyText(val options: List<String>) : ElementSelector()
    data object ByFocusedEditable : ElementSelector()
}

sealed class AgentAction {
    data class Tap(val selector: ElementSelector) : AgentAction()
    data class LongPress(val selector: ElementSelector, val durationMs: Long = 600) : AgentAction()
    data class SetText(val selector: ElementSelector, val text: String) : AgentAction()
    data class Swipe(
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
        val durationMs: Long = 300,
    ) : AgentAction()
    data class Scroll(val selector: ElementSelector, val forward: Boolean = true) : AgentAction()
    data class WaitFor(
        val selector: ElementSelector,
        val timeoutMs: Long = 5_000,
        val disappear: Boolean = false,
    ) : AgentAction()
    data class LaunchApp(val query: String) : AgentAction()
    data object ImeEnter : AgentAction()
    data object Back : AgentAction()
    data object Home : AgentAction()
    data object Recents : AgentAction()
    data object OpenNotifications : AgentAction()

    fun label(): String = when (this) {
        is Tap -> "tap"
        is LongPress -> "long_press"
        is SetText -> "set_text"
        is Swipe -> "swipe"
        is Scroll -> "scroll"
        is WaitFor -> "wait"
        is LaunchApp -> "launch"
        ImeEnter -> "ime_enter"
        Back -> "back"
        Home -> "home"
        Recents -> "recents"
        OpenNotifications -> "notifications"
    }

    fun payload(): String = when (this) {
        is Tap -> selector.describe()
        is LongPress -> selector.describe()
        is SetText -> "${selector.describe()} text=${text.take(80)}"
        is Swipe -> "$fromX,$fromY->$toX,$toY"
        is Scroll -> "forward=$forward ${selector.describe()}"
        is WaitFor -> selector.describe()
        is LaunchApp -> query
        else -> label()
    }
}

fun ElementSelector.describe(): String = when (this) {
    is ElementSelector.ByText -> "text:$text"
    is ElementSelector.ByResourceId -> "id:$id"
    is ElementSelector.ByContentDescription -> "desc:$desc"
    is ElementSelector.ByCoordinates -> "xy:$x,$y"
    is ElementSelector.ByAnyText -> "any:${options.joinToString("|")}"
    ElementSelector.ByFocusedEditable -> "focused_editable"
}

enum class TaskLanguage { RU, EN, AUTO }

data class ParsedTask(
    val raw: String,
    val intent: String,
    val language: TaskLanguage,
    val entities: Map<String, String>,
)

data class PlanStep(
    val index: Int,
    val description: String,
    val action: AgentAction,
    val isIrreversible: Boolean = false,
)

data class Plan(
    val steps: List<PlanStep>,
    val irreversibleIndexes: Set<Int>,
)

sealed class RecoveryDecision {
    data class Retry(val action: AgentAction) : RecoveryDecision()
    data class ScrollAndRetry(val scroll: AgentAction, val retry: AgentAction) : RecoveryDecision()
    data class BackAndRetry(val retry: AgentAction) : RecoveryDecision()
    data object AskUser : RecoveryDecision()
    data object Abort : RecoveryDecision()
}

enum class GlobalGesture { BACK, HOME, RECENTS, NOTIFICATIONS }

data class FoundTarget(val bounds: UiRect, val text: String?)
