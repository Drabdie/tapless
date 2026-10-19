package com.aiphoneagent.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

interface Planner {
    suspend fun plan(task: ParsedTask, currentScreen: ScreenSnapshot?): AgentResult<Plan>
}

interface ScreenObserver {
    val currentScreen: StateFlow<ScreenSnapshot?>
    suspend fun await(
        predicate: (ScreenSnapshot) -> Boolean,
        timeoutMs: Long,
        pollMs: Long = 100,
    ): ScreenSnapshot?

    suspend fun awaitElement(selector: ElementSelector, timeoutMs: Long = 5_000): UiNode?
}

class ScreenObserverImpl(
    override val currentScreen: StateFlow<ScreenSnapshot?>,
) : ScreenObserver {
    override suspend fun await(
        predicate: (ScreenSnapshot) -> Boolean,
        timeoutMs: Long,
        pollMs: Long,
    ): ScreenSnapshot? {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started <= timeoutMs) {
            val screen = currentScreen.value
            if (screen != null && predicate(screen)) return screen
            if (System.currentTimeMillis() - started >= timeoutMs) break
            delay(pollMs)
        }
        return null
    }

    override suspend fun awaitElement(selector: ElementSelector, timeoutMs: Long): UiNode? {
        return await({ it.root.find(selector) != null }, timeoutMs)?.root?.find(selector)
    }
}

interface NodeSource {
    fun packageName(): String?
    fun centerOf(selector: ElementSelector): FoundTarget?
    fun setText(selector: ElementSelector, text: String): Boolean
    fun scroll(selector: ElementSelector, forward: Boolean): Boolean
    fun imeEnter(): Boolean
}

interface GestureRunner {
    suspend fun tap(x: Int, y: Int, durationMs: Long): Boolean
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Boolean
    suspend fun global(which: GlobalGesture): Boolean
    suspend fun launch(query: String): AgentResult<Unit>
    fun screenSize(): Pair<Int, Int>
}

interface ActionExecutor {
    suspend fun execute(action: AgentAction): AgentResult<Unit>
}

class ActionExecutorEngine(
    private val nodes: NodeSource,
    private val gestures: GestureRunner,
    private val logs: ActionLogRepository,
    private val taskContext: TaskContextProvider,
    private val killSwitch: KillSwitch,
    private val observer: ScreenObserver,
) : ActionExecutor {
    override suspend fun execute(action: AgentAction): AgentResult<Unit> {
        if (killSwitch.isKilled.value) {
            return fail(action, AgentError.Cancelled, "Остановлено")
        }
        val pkg = nodes.packageName()
        val escapes = action is AgentAction.Back || action is AgentAction.Home || action is AgentAction.LaunchApp
        if (!escapes && pkg != null && SecurityPolicy.isPackageBlocked(pkg)) {
            return fail(action, AgentError.Blocked, "Пакет заблокирован: $pkg")
        }
        val result = perform(action)
        log(action, result)
        return result
    }

    private suspend fun perform(action: AgentAction): AgentResult<Unit> = when (action) {
        is AgentAction.Tap -> tap(action.selector, 60)
        is AgentAction.LongPress -> tap(action.selector, action.durationMs)
        is AgentAction.SetText -> {
            val ok = nodes.setText(action.selector, action.text)
            if (ok) ok() else missing(action.selector)
        }
        is AgentAction.Swipe -> {
            val ok = gestures.swipe(action.fromX, action.fromY, action.toX, action.toY, action.durationMs)
            if (ok) ok() else AgentResult.Failure(AgentError.Unknown, "Жест отклонён")
        }
        is AgentAction.Scroll -> {
            val scrolled = nodes.scroll(action.selector, action.forward)
            if (scrolled) {
                ok()
            } else {
                val (width, height) = gestures.screenSize()
                val fromY = if (action.forward) (height * 0.72f).toInt() else (height * 0.32f).toInt()
                val toY = if (action.forward) (height * 0.32f).toInt() else (height * 0.72f).toInt()
                val okSwipe = gestures.swipe(width / 2, fromY, width / 2, toY, 320)
                if (okSwipe) ok() else AgentResult.Failure(AgentError.ElementNotFound, "Нечего прокручивать")
            }
        }
        is AgentAction.WaitFor -> {
            val found = withTimeoutOrNull(action.timeoutMs) {
                observer.await(
                    predicate = { snapshot ->
                        val present = snapshot.root.find(action.selector) != null
                        if (action.disappear) !present else present
                    },
                    timeoutMs = action.timeoutMs,
                )
            }
            if (found != null) ok() else AgentResult.Failure(AgentError.Timeout, "Элемент не дождались")
        }
        is AgentAction.LaunchApp -> gestures.launch(action.query)
        AgentAction.ImeEnter -> {
            if (nodes.imeEnter()) ok() else AgentResult.Failure(AgentError.ElementNotFound, "Нет поля ввода")
        }
        AgentAction.Back -> global(GlobalGesture.BACK)
        AgentAction.Home -> global(GlobalGesture.HOME)
        AgentAction.Recents -> global(GlobalGesture.RECENTS)
        AgentAction.OpenNotifications -> global(GlobalGesture.NOTIFICATIONS)
    }

    private suspend fun tap(selector: ElementSelector, durationMs: Long): AgentResult<Unit> {
        val target = nodes.centerOf(selector) ?: return missing(selector)
        val ok = gestures.tap(target.bounds.centerX, target.bounds.centerY, durationMs)
        return if (ok) ok() else AgentResult.Failure(AgentError.Unknown, "Жест отклонён")
    }

    private suspend fun global(which: GlobalGesture): AgentResult<Unit> {
        return if (gestures.global(which)) ok() else AgentResult.Failure(AgentError.Unknown, "Глобальное действие недоступно")
    }

    private fun missing(selector: ElementSelector) =
        AgentResult.Failure(AgentError.ElementNotFound, "Не найдено: ${selector.describe()}")

    private fun ok() = AgentResult.Success(Unit)

    private suspend fun log(action: AgentAction, result: AgentResult<Unit>) {
        val taskId = taskContext.taskId
        if (taskId <= 0) return
        val text = when (result) {
            is AgentResult.Success -> "ok"
            is AgentResult.Failure -> "fail:${result.message}"
        }
        logs.append(
            ActionLogEntry(
                taskId = taskId,
                stepIndex = taskContext.stepIndex,
                action = action.label(),
                payloadJson = SafeLogger.redact(action.payload()),
                result = SafeLogger.redact(text),
            ),
        )
    }

    private suspend fun fail(action: AgentAction, error: AgentError, message: String): AgentResult<Unit> {
        val result = AgentResult.Failure(error, message)
        log(action, result)
        return result
    }
}
