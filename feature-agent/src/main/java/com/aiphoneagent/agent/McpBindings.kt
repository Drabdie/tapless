package com.aiphoneagent.agent

import android.content.Context
import android.content.Intent
import com.aiphoneagent.common.ActionExecutor
import com.aiphoneagent.common.AgentRunner
import com.aiphoneagent.common.RunState
import com.aiphoneagent.common.ScreenObserver
import com.aiphoneagent.common.ScreenshotProvider
import com.aiphoneagent.common.TaskRepository
import com.aiphoneagent.mcp.AppCatalog
import com.aiphoneagent.mcp.McpServer
import com.aiphoneagent.mcp.McpTaskRunner
import com.aiphoneagent.mcp.McpToolRegistry
import com.aiphoneagent.mcp.McpTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Lists launchable apps for the MCP `list_apps` tool. */
class AndroidAppCatalog(private val context: Context) : AppCatalog {
    override fun installedApps(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { runCatching { it.loadLabel(context.packageManager).toString() }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}

/** Runs a natural-language task through the on-device agent for the MCP `run_task` tool. */
class AgentMcpTaskRunner(
    private val runner: AgentRunner,
    private val tasks: TaskRepository,
) : McpTaskRunner {
    override suspend fun run(prompt: String): String {
        val language = if (prompt.any { it in '\u0410'..'\u044f' }) "RU" else "EN"
        val id = tasks.create(prompt, language)
        val state = runner.run(id, prompt)
        tasks.updateStatus(id, state.javaClass.simpleName, System.currentTimeMillis())
        return when (state) {
            is RunState.Done -> "done: ${state.steps.size} step(s)"
            is RunState.Failed -> "failed: ${state.reason}"
            is RunState.Cancelled -> "cancelled"
            else -> "finished: ${state.javaClass.simpleName}"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object McpBindings {
    @Provides
    @Singleton
    fun appCatalog(@ApplicationContext context: Context): AppCatalog = AndroidAppCatalog(context)

    @Provides
    @Singleton
    fun taskRunner(runner: AgentRunner, tasks: TaskRepository): McpTaskRunner =
        AgentMcpTaskRunner(runner, tasks)

    @Provides
    @Singleton
    fun registry(
        observer: ScreenObserver,
        executor: ActionExecutor,
        screenshot: ScreenshotProvider,
        catalog: AppCatalog,
        taskRunner: McpTaskRunner,
    ): McpToolRegistry = McpToolRegistry(
        McpTools.default(observer, executor, screenshot, catalog, taskRunner),
    )

    @Provides
    @Singleton
    fun mcpServer(registry: McpToolRegistry): McpServer = McpServer(registry)
}
