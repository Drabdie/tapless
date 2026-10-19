package com.aiphoneagent.accessibility

import android.content.Context
import com.aiphoneagent.common.ActionExecutor
import com.aiphoneagent.common.ActionExecutorEngine
import com.aiphoneagent.common.ActionLogRepository
import com.aiphoneagent.common.AgentAction
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.AppDispatchers
import com.aiphoneagent.common.KillSwitch
import com.aiphoneagent.common.ScreenObserver
import com.aiphoneagent.common.TaskContextProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class AccessibilityActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logs: ActionLogRepository,
    private val taskContext: TaskContextProvider,
    private val killSwitch: KillSwitch,
    private val observer: ScreenObserver,
) : ActionExecutor {
    override suspend fun execute(action: AgentAction): AgentResult<Unit> = withContext(AppDispatchers.main) {
        if (AccessibilityBridge.service == null && action !is AgentAction.LaunchApp) {
            return@withContext AgentResult.Failure(
                com.aiphoneagent.common.AgentError.Unknown,
                "Включите службу специальных возможностей AI Phone Agent",
            )
        }
        ActionExecutorEngine(
            nodes = LiveNodeSource(),
            gestures = ServiceGestureRunner(context),
            logs = logs,
            taskContext = taskContext,
            killSwitch = killSwitch,
            observer = observer,
        ).execute(action)
    }
}
