package com.aiphoneagent.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class RunState {
    data object Idle : RunState()
    data class Planning(val taskId: Long) : RunState()
    data class Executing(
        val taskId: Long,
        val stepIndex: Int,
        val total: Int,
        val description: String,
    ) : RunState()
    data class AwaitingConfirmation(
        val taskId: Long,
        val stepIndex: Int,
        val description: String,
        val warning: String,
    ) : RunState()
    data class Recovering(val taskId: Long, val stepIndex: Int, val attempt: Int) : RunState()
    data class Done(val taskId: Long, val steps: List<PlanStep>) : RunState()
    data class Failed(val taskId: Long, val reason: String, val steps: List<PlanStep> = emptyList()) : RunState()
    data class Cancelled(val taskId: Long) : RunState()
}

class AgentRunner(
    private val parser: TaskParser,
    private val planner: Planner,
    private val executor: ActionExecutor,
    private val observer: ScreenObserver,
    private val validator: Validator,
    private val recovery: RecoveryEngine,
    private val killSwitch: KillSwitch,
    private val logs: ActionLogRepository,
    private val taskContext: TaskContextProvider,
) {
    private val mutex = Mutex()
    private val states = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = states.asStateFlow()
    private var confirmDeferred: CompletableDeferred<Boolean>? = null

    fun confirm() {
        confirmDeferred?.complete(true)
    }

    fun deny() {
        confirmDeferred?.complete(false)
    }

    fun stop() {
        killSwitch.kill()
        confirmDeferred?.complete(false)
        val current = states.value
        val taskId = when (current) {
            is RunState.Planning -> current.taskId
            is RunState.Executing -> current.taskId
            is RunState.AwaitingConfirmation -> current.taskId
            is RunState.Recovering -> current.taskId
            is RunState.Failed -> current.taskId
            is RunState.Done -> current.taskId
            is RunState.Cancelled -> current.taskId
            RunState.Idle -> -1L
        }
        if (taskId > 0) states.value = RunState.Cancelled(taskId)
    }

    suspend fun run(taskId: Long, prompt: String): RunState = mutex.withLock {
        killSwitch.reset()
        val machine = TaskStateMachine()
        machine.reduce(MachineEvent.Plan)
        states.value = RunState.Planning(taskId)
        taskContext.taskId = taskId
        val parsed = parser.parse(prompt)
        if (killSwitch.isKilled.value) return cancel(taskId)
        val plan = when (val planned = planner.plan(parsed, observer.currentScreen.value?.let(SecurityPolicy::sanitizeForLlm))) {
            is AgentResult.Success -> planned.value
            is AgentResult.Failure -> {
                states.value = RunState.Failed(taskId, planned.message)
                return states.value
            }
        }
        machine.reduce(MachineEvent.Planned)
        if (plan.steps.isEmpty()) {
            states.value = RunState.Failed(taskId, "План пуст")
            return states.value
        }
        for (step in plan.steps) {
            if (killSwitch.isKilled.value) return cancel(taskId)
            if (machine.isExpired()) {
                machine.reduce(MachineEvent.Timeout)
                states.value = RunState.Failed(taskId, "Превышен лимит времени", plan.steps)
                return states.value
            }
            val screen = observer.currentScreen.value
            if (step.isIrreversible || SecurityPolicy.requiresConfirmation(step.action, screen)) {
                machine.reduce(MachineEvent.NeedConfirm)
                states.value = RunState.AwaitingConfirmation(
                    taskId = taskId,
                    stepIndex = step.index,
                    description = step.description,
                    warning = step.description,
                )
                val deferred = CompletableDeferred<Boolean>()
                confirmDeferred = deferred
                val allowed = deferred.await()
                confirmDeferred = null
                if (!allowed || killSwitch.isKilled.value) return cancel(taskId)
                machine.reduce(MachineEvent.Confirmed)
            }
            states.value = RunState.Executing(taskId, step.index, plan.steps.size, step.description)
            machine.reduce(MachineEvent.StartStep)
            taskContext.stepIndex = step.index
            val performed = performWithRecovery(taskId, step)
            if (performed is AgentResult.Failure) {
                if (performed.error is AgentError.Cancelled) return cancel(taskId)
                machine.reduce(MachineEvent.Fail)
                states.value = RunState.Failed(taskId, performed.message, plan.steps)
                return states.value
            }
            logs.append(
                ActionLogEntry(
                    taskId = taskId,
                    stepIndex = step.index,
                    action = "step_ok",
                    payloadJson = step.description,
                    result = "ok",
                ),
            )
        }
        machine.reduce(MachineEvent.Succeed)
        states.value = RunState.Done(taskId, plan.steps)
        states.value
    }

    private suspend fun performWithRecovery(taskId: Long, step: PlanStep): AgentResult<Unit> {
        var action = step.action
        var attempt = 0
        while (true) {
            if (killSwitch.isKilled.value) return AgentResult.Failure(AgentError.Cancelled, "Остановлено")
            val before = observer.currentScreen.value
            val result = executor.execute(action)
            delay(200)
            val after = observer.currentScreen.value
            val verified = result is AgentResult.Success && (
                before == null || after == null || validator.verify(action, before, after)
                )
            if (verified) return AgentResult.Success(Unit)
            attempt += 1
            if (attempt > 3) {
                return if (result is AgentResult.Failure) result
                else AgentResult.Failure(AgentError.Unknown, "Шаг не подтвердился: ${step.description}")
            }
            states.value = RunState.Recovering(taskId, step.index, attempt)
            when (val decision = recovery.recover(step.action, attempt, after ?: before)) {
                is RecoveryDecision.Retry -> action = decision.action
                is RecoveryDecision.ScrollAndRetry -> {
                    executor.execute(decision.scroll)
                    delay(250)
                    action = decision.retry
                }
                is RecoveryDecision.BackAndRetry -> {
                    executor.execute(AgentAction.Back)
                    delay(300)
                    action = decision.retry
                }
                RecoveryDecision.AskUser -> return AgentResult.Failure(AgentError.Unknown, "Нужна помощь: ${step.description}")
                RecoveryDecision.Abort -> {
                    return if (result is AgentResult.Failure) result
                    else AgentResult.Failure(AgentError.Unknown, "Остановлено политикой восстановления")
                }
            }
        }
    }

    private fun cancel(taskId: Long): RunState {
        states.value = RunState.Cancelled(taskId)
        return states.value
    }
}

object UiTreeLimiter {
    const val MAX_DEPTH = 40
    const val MAX_NODES = 2000

    fun pruneAndLimit(node: UiNode, maxNodes: Int = MAX_NODES, maxDepth: Int = MAX_DEPTH): UiNode {
        var count = 0
        fun walk(current: UiNode, depth: Int): UiNode? {
            if (depth > maxDepth || count >= maxNodes) return null
            if (!current.visible && current.children.isEmpty()) return null
            if (current.bounds.width <= 0 && current.bounds.height <= 0 && current.children.isEmpty()) return null
            count += 1
            val children = mutableListOf<UiNode>()
            for (child in current.children) {
                if (count >= maxNodes) break
                walk(child, depth + 1)?.let(children::add)
            }
            return current.copy(children = children)
        }
        return walk(node, 0) ?: node.copy(children = emptyList())
    }
}
