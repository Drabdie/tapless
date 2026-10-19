package com.aiphoneagent.mcp

import com.aiphoneagent.common.ActionExecutor
import com.aiphoneagent.common.AgentAction
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.ElementSelector
import com.aiphoneagent.common.ScreenObserver
import com.aiphoneagent.common.ScreenshotProvider
import com.aiphoneagent.common.UiTextRenderer
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Supplies the list of installed apps (implemented on Android via PackageManager). */
interface AppCatalog {
    fun installedApps(): List<String>
}

/** Runs a full natural-language task with the on-device agent. */
interface McpTaskRunner {
    suspend fun run(prompt: String): String
}

// ---------------------------------------------------------------------------
// JSON schema helpers
// ---------------------------------------------------------------------------

private fun strProp(desc: String) = buildJsonObject {
    put("type", JsonPrimitive("string")); put("description", JsonPrimitive(desc))
}

private fun intProp(desc: String) = buildJsonObject {
    put("type", JsonPrimitive("integer")); put("description", JsonPrimitive(desc))
}

private fun schema(
    vararg props: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("properties", buildJsonObject { props.forEach { (key, value) -> put(key, value) } })
    if (required.isNotEmpty()) {
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

private fun AgentResult<Unit>.toResult(success: String): McpToolResult = when (this) {
    is AgentResult.Success -> McpToolResult.ok(success)
    is AgentResult.Failure -> McpToolResult.error(message)
}

// ---------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------

class GetScreenTool(private val observer: ScreenObserver) : McpToolHandler {
    override val definition = McpToolDef(
        name = "get_screen",
        description = "Return the current screen's accessibility tree: elements, text and pixel bounds.",
        inputSchema = schema(),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val screen = observer.currentScreen.value
            ?: return McpToolResult.ok("No screen captured yet. Enable the accessibility service.")
        return McpToolResult.ok(UiTextRenderer.renderDetailed(screen))
    }
}

class TapTool(private val executor: ActionExecutor) : McpToolHandler {
    override val definition = McpToolDef(
        name = "tap",
        description = "Tap an element. Provide visible 'text' to search for, or explicit 'x'/'y' pixels.",
        inputSchema = schema(
            "text" to strProp("Visible text or content description of the element to tap"),
            "x" to intProp("X coordinate in pixels"),
            "y" to intProp("Y coordinate in pixels"),
        ),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val text = args.str("text")
        val selector = if (!text.isNullOrBlank()) {
            ElementSelector.ByAnyText(listOf(text))
        } else {
            val x = args.int("x")
            val y = args.int("y")
            if (x == null || y == null) return McpToolResult.error("Provide 'text' or both 'x' and 'y'")
            ElementSelector.ByCoordinates(x, y)
        }
        return executor.execute(AgentAction.Tap(selector)).toResult("tapped")
    }
}

class SetTextTool(private val executor: ActionExecutor) : McpToolHandler {
    override val definition = McpToolDef(
        name = "set_text",
        description = "Type text into the focused input field, optionally focusing a field by its label first.",
        inputSchema = schema(
            "text" to strProp("Text to type"),
            "field" to strProp("Optional label of the input field to focus first"),
            required = listOf("text"),
        ),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val text = args.str("text") ?: return McpToolResult.error("Missing 'text'")
        val field = args.str("field")
        if (!field.isNullOrBlank()) {
            executor.execute(AgentAction.Tap(ElementSelector.ByAnyText(listOf(field))))
            delay(150)
        }
        return executor.execute(AgentAction.SetText(ElementSelector.ByFocusedEditable, text))
            .toResult("text set")
    }
}

class ScrollTool(private val executor: ActionExecutor) : McpToolHandler {
    override val definition = McpToolDef(
        name = "scroll",
        description = "Scroll the current screen up or down.",
        inputSchema = schema("direction" to strProp("'down' (default) or 'up'")),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val forward = args.str("direction")?.lowercase() != "up"
        return executor.execute(AgentAction.Scroll(ElementSelector.ByFocusedEditable, forward))
            .toResult("scrolled")
    }
}

class GlobalActionTool(private val executor: ActionExecutor) : McpToolHandler {
    override val definition = McpToolDef(
        name = "global_action",
        description = "Perform a system action: back, home, recents or notifications.",
        inputSchema = schema(
            "action" to strProp("back | home | recents | notifications"),
            required = listOf("action"),
        ),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val action = when (args.str("action")?.lowercase()) {
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "recents" -> AgentAction.Recents
            "notifications" -> AgentAction.OpenNotifications
            else -> return McpToolResult.error("Unknown action")
        }
        return executor.execute(action).toResult("done")
    }
}

class LaunchAppTool(private val executor: ActionExecutor) : McpToolHandler {
    override val definition = McpToolDef(
        name = "launch_app",
        description = "Launch an app by name/package, or open a settings screen (e.g. 'wifi', 'bluetooth').",
        inputSchema = schema(
            "query" to strProp("App name, package or setting keyword"),
            required = listOf("query"),
        ),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val query = args.str("query") ?: return McpToolResult.error("Missing 'query'")
        return executor.execute(AgentAction.LaunchApp(query)).toResult("launched $query")
    }
}

class ScreenshotTool(private val provider: ScreenshotProvider) : McpToolHandler {
    override val definition = McpToolDef(
        name = "screenshot",
        description = "Capture the current screen and return it as a base64 JPEG image.",
        inputSchema = schema(),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        if (!provider.isAvailable()) return McpToolResult.error("Screenshot not available on this device")
        return when (val result = provider.capture()) {
            is AgentResult.Success -> McpToolResult(
                listOf(McpContent.image(result.value.base64Jpeg)),
                isError = false,
            )
            is AgentResult.Failure -> McpToolResult.error(result.message)
        }
    }
}

class ListAppsTool(private val catalog: AppCatalog) : McpToolHandler {
    override val definition = McpToolDef(
        name = "list_apps",
        description = "List installed launchable applications.",
        inputSchema = schema(),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult =
        McpToolResult.ok(catalog.installedApps().joinToString("\n"))
}

class RunTaskTool(private val runner: McpTaskRunner) : McpToolHandler {
    override val definition = McpToolDef(
        name = "run_task",
        description = "Run a natural-language task with the on-device AI agent (multi-step automation).",
        inputSchema = schema(
            "prompt" to strProp("The task in natural language"),
            required = listOf("prompt"),
        ),
    )

    override suspend fun invoke(args: JsonObject): McpToolResult {
        val prompt = args.str("prompt") ?: return McpToolResult.error("Missing 'prompt'")
        return McpToolResult.ok(runner.run(prompt))
    }
}

/** Assembles the default tool set for the phone-control MCP server. */
object McpTools {
    fun default(
        observer: ScreenObserver,
        executor: ActionExecutor,
        screenshot: ScreenshotProvider,
        catalog: AppCatalog,
        taskRunner: McpTaskRunner,
    ): List<McpToolHandler> = listOf(
        GetScreenTool(observer),
        TapTool(executor),
        SetTextTool(executor),
        ScrollTool(executor),
        GlobalActionTool(executor),
        LaunchAppTool(executor),
        ScreenshotTool(screenshot),
        ListAppsTool(catalog),
        RunTaskTool(taskRunner),
    )
}
