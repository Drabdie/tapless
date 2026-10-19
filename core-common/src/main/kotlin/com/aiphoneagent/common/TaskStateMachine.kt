package com.aiphoneagent.common

enum class MachineState {
    Idle,
    Planning,
    Executing,
    Verifying,
    Recovering,
    AwaitingConfirmation,
    Done,
    Failed,
    Cancelled,
}

sealed class MachineEvent {
    data object Plan : MachineEvent()
    data object Planned : MachineEvent()
    data object StartStep : MachineEvent()
    data object Verify : MachineEvent()
    data object Verified : MachineEvent()
    data object Recover : MachineEvent()
    data object NeedConfirm : MachineEvent()
    data object Confirmed : MachineEvent()
    data object Succeed : MachineEvent()
    data object Fail : MachineEvent()
    data object Cancel : MachineEvent()
    data object Timeout : MachineEvent()
}

class TaskStateMachine(
    private val budgetMs: Long = 120_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    var state: MachineState = MachineState.Idle
        private set
    private val startedAt = now()

    fun isExpired(): Boolean {
        return state != MachineState.Idle &&
            state != MachineState.Done &&
            state != MachineState.Cancelled &&
            state != MachineState.Failed &&
            now() - startedAt > budgetMs
    }

    fun reduce(event: MachineEvent): MachineState {
        if (event is MachineEvent.Cancel) {
            state = MachineState.Cancelled
            return state
        }
        if (event is MachineEvent.Timeout || (event !is MachineEvent.Succeed && isExpired())) {
            state = MachineState.Failed
            return state
        }
        state = when (state) {
            MachineState.Idle -> when (event) {
                MachineEvent.Plan -> MachineState.Planning
                else -> state
            }
            MachineState.Planning -> when (event) {
                MachineEvent.Planned, MachineEvent.StartStep -> MachineState.Executing
                MachineEvent.Fail -> MachineState.Failed
                else -> state
            }
            MachineState.Executing -> when (event) {
                MachineEvent.Verify -> MachineState.Verifying
                MachineEvent.NeedConfirm -> MachineState.AwaitingConfirmation
                MachineEvent.Recover -> MachineState.Recovering
                MachineEvent.Succeed -> MachineState.Done
                MachineEvent.Fail -> MachineState.Failed
                else -> state
            }
            MachineState.Verifying -> when (event) {
                MachineEvent.Verified, MachineEvent.StartStep -> MachineState.Executing
                MachineEvent.Recover -> MachineState.Recovering
                MachineEvent.Succeed -> MachineState.Done
                MachineEvent.Fail -> MachineState.Failed
                else -> state
            }
            MachineState.Recovering -> when (event) {
                MachineEvent.Verify -> MachineState.Verifying
                MachineEvent.StartStep -> MachineState.Executing
                MachineEvent.Fail -> MachineState.Failed
                MachineEvent.Succeed -> MachineState.Done
                else -> state
            }
            MachineState.AwaitingConfirmation -> when (event) {
                MachineEvent.Confirmed, MachineEvent.StartStep -> MachineState.Executing
                MachineEvent.Fail -> MachineState.Failed
                else -> state
            }
            MachineState.Done, MachineState.Failed, MachineState.Cancelled -> state
        }
        return state
    }
}
