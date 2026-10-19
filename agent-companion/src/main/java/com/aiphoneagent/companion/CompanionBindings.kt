package com.aiphoneagent.companion

import android.content.Context
import android.content.Intent
import com.aiphoneagent.common.ActionExecutor
import com.aiphoneagent.common.ActionLogRepository
import com.aiphoneagent.common.DefaultKillSwitch
import com.aiphoneagent.common.KillSwitch
import com.aiphoneagent.common.MemoryActionLogRepository
import com.aiphoneagent.common.ScreenObserver
import com.aiphoneagent.common.ScreenshotProvider
import com.aiphoneagent.common.TaskContextProvider
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
class CompanionAppCatalog(private val context: Context) : AppCatalog {
    override fun installedApps(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { runCatching { it.loadLabel(context.packageManager).toString() }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}

/**
 * The companion exposes only atomic device-control tools; higher-level natural-language
 * planning lives in the main app (which owns the LLM key). External MCP clients such as
 * Claude Desktop perform the reasoning themselves and drive the atomic tools directly.
 */
class CompanionTaskRunner : McpTaskRunner {
    override suspend fun run(prompt: String): String =
        "run_task is handled by the main AI Phone Agent app. In companion mode, call the " +
            "atomic tools (get_screen, tap, set_text, scroll, global_action, launch_app) " +
            "directly and do the planning in your MCP client."
}

@Module
@InstallIn(SingletonComponent::class)
object CompanionBindings {
    @Provides
    @Singleton
    fun killSwitch(): KillSwitch = DefaultKillSwitch()

    @Provides
    @Singleton
    fun taskContext(): TaskContextProvider = TaskContextProvider()

    @Provides
    @Singleton
    fun actionLogs(): ActionLogRepository = MemoryActionLogRepository()

    @Provides
    @Singleton
    fun appCatalog(@ApplicationContext context: Context): AppCatalog = CompanionAppCatalog(context)

    @Provides
    @Singleton
    fun taskRunner(): McpTaskRunner = CompanionTaskRunner()

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
