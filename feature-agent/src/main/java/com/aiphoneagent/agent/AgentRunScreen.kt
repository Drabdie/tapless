package com.aiphoneagent.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiphoneagent.common.ActionLogEntry
import com.aiphoneagent.common.ActionLogRepository
import com.aiphoneagent.common.AgentRunner
import com.aiphoneagent.common.PlanStep
import com.aiphoneagent.common.RunState
import com.aiphoneagent.common.TaskRepository
import com.aiphoneagent.ui.AgentLogRow
import com.aiphoneagent.ui.AgentStatus
import com.aiphoneagent.ui.DangerButton
import com.aiphoneagent.ui.PrimaryButton
import com.aiphoneagent.ui.SecondaryButton
import com.aiphoneagent.ui.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class AgentRunViewModel @Inject constructor(
    private val runner: AgentRunner,
    private val tasks: TaskRepository,
    logs: ActionLogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val taskId: Long = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L
    val state = runner.state
    val log: Flow<List<ActionLogEntry>> = logs.observe(taskId)

    init {
        viewModelScope.launch {
            val task = tasks.get(taskId) ?: return@launch
            if (task.status == "CREATED") {
                tasks.updateStatus(taskId, "RUNNING")
                val end = runner.run(taskId, task.prompt)
                val status = when (end) {
                    is RunState.Done -> "DONE"
                    is RunState.Cancelled -> "CANCELLED"
                    is RunState.Failed -> "FAILED"
                    else -> "DONE"
                }
                tasks.updateStatus(taskId, status, System.currentTimeMillis())
            }
        }
    }

    fun confirm() = runner.confirm()
    fun deny() {
        runner.deny()
    }
    fun stop() {
        runner.stop()
        viewModelScope.launch { tasks.updateStatus(taskId, "CANCELLED", System.currentTimeMillis()) }
    }
}

@Composable
fun AgentRunRoute(
    onBack: () -> Unit,
    viewModel: AgentRunViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle(emptyList())
    AgentRunScreen(
        state = state,
        taskId = viewModel.taskId,
        log = log,
        onBack = onBack,
        onStop = viewModel::stop,
        onConfirm = viewModel::confirm,
        onDeny = viewModel::deny,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentRunScreen(
    state: RunState,
    taskId: Long,
    log: List<ActionLogEntry>,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
) {
    val relevant = when (state) {
        is RunState.Executing -> if (state.taskId == taskId) state else null
        is RunState.AwaitingConfirmation -> if (state.taskId == taskId) state else null
        is RunState.Recovering -> if (state.taskId == taskId) state else null
        is RunState.Done -> if (state.taskId == taskId) state else null
        is RunState.Failed -> if (state.taskId == taskId) state else null
        is RunState.Planning -> if (state.taskId == taskId) state else null
        is RunState.Cancelled -> if (state.taskId == taskId) state else null
        RunState.Idle -> null
    }
    val chip = when (relevant) {
        is RunState.Done -> AgentStatus.Done
        is RunState.Failed -> AgentStatus.Error
        is RunState.Cancelled -> AgentStatus.Paused
        is RunState.AwaitingConfirmation -> AgentStatus.Paused
        null, RunState.Idle -> AgentStatus.Idle
        else -> AgentStatus.Running
    }
    val steps: List<PlanStep> = when (relevant) {
        is RunState.Done -> relevant.steps
        is RunState.Failed -> relevant.steps
        else -> emptyList()
    }
    val active = when (relevant) {
        is RunState.Executing -> relevant.stepIndex
        is RunState.Recovering -> relevant.stepIndex
        is RunState.AwaitingConfirmation -> relevant.stepIndex
        else -> -1
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Text("Выполнение", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusChip(chip)
        }
        Text(
            text = when (relevant) {
                is RunState.Executing -> relevant.description
                is RunState.Recovering -> "Повтор, попытка ${relevant.attempt}"
                is RunState.AwaitingConfirmation -> relevant.description
                is RunState.Failed -> relevant.reason
                is RunState.Done -> "Команда выполнена"
                is RunState.Cancelled -> "Остановлено"
                is RunState.Planning -> "Составляю план"
                else -> "Нет активного запуска"
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(steps) { step ->
                val mark = when {
                    active < 0 && relevant is RunState.Done -> "✓"
                    step.index < active -> "✓"
                    step.index == active -> "…"
                    else -> "•"
                }
                Text(
                    "$mark  ${step.description}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    fontWeight = if (step.index == active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            items(log, key = { it.id }) { entry ->
                AgentLogRow(entry.action, entry.payloadJson, entry.result)
            }
        }
        DangerButton(
            text = "STOP",
            onClick = onStop,
            modifier = Modifier.navigationBarsPadding().padding(16.dp),
        )
    }
    val confirm = relevant as? RunState.AwaitingConfirmation
    if (confirm != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = onDeny, sheetState = sheetState) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Нужно подтверждение", style = MaterialTheme.typography.titleLarge)
                Text("Агент хочет выполнить: ${confirm.warning}")
                PrimaryButton("Подтвердить", onConfirm)
                SecondaryButton("Отменить", onDeny)
            }
        }
    }
}
