package com.aiphoneagent.agent

import com.aiphoneagent.common.AgentAction
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.ElementSelector
import com.aiphoneagent.common.Plan
import com.aiphoneagent.common.PlanStep
import com.aiphoneagent.common.Planner
import com.aiphoneagent.common.ParsedTask
import com.aiphoneagent.common.RuleBasedPlanner
import com.aiphoneagent.common.ScreenSnapshot
import com.aiphoneagent.common.SecurityPolicy
import com.aiphoneagent.network.LlmClientFactory
import com.aiphoneagent.network.LlmMessage
import com.aiphoneagent.network.LlmRequest
import com.aiphoneagent.network.Provider
import com.aiphoneagent.network.ResponseFormat
import com.aiphoneagent.network.Role
import com.aiphoneagent.network.ApiKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class HybridPlanner @Inject constructor(
    private val rules: RuleBasedPlanner,
    private val factory: LlmClientFactory,
    private val keys: ApiKeyStore,
) : Planner {
    override suspend fun plan(task: ParsedTask, currentScreen: ScreenSnapshot?): AgentResult<Plan> {
        val provider = keys.getProvider()
        if (provider != Provider.OLLAMA && keys.getKey(provider).isNullOrBlank()) {
            return AgentResult.Success(rules.build(task))
        }
        val summary = summarize(currentScreen?.let(SecurityPolicy::sanitizeForLlm))
        val request = LlmRequest(
            model = keys.getModel(provider),
            messages = listOf(
                LlmMessage(Role.SYSTEM, SYSTEM_PROMPT),
                LlmMessage(Role.USER, "Task: ${task.raw}\nScreen:\n$summary"),
            ),
            responseFormat = ResponseFormat.JSON,
            maxTokens = 1200,
        )
        return when (val response = factory.create(provider).complete(request)) {
            is AgentResult.Success -> AgentResult.Success(PlanJson.parse(response.value.content) ?: rules.build(task))
            is AgentResult.Failure -> AgentResult.Success(rules.build(task))
        }
    }

    private fun summarize(screen: ScreenSnapshot?): String {
        if (screen == null) return "screen: unknown"
        return buildString {
            append("package=").append(screen.packageName).append('\n')
            screen.root.flatten().take(60).forEach { node ->
                val label = node.text ?: node.contentDescription ?: return@forEach
                append("- ")
                if (node.clickable) append("clickable ")
                if (node.editable) append("editable ")
                node.resourceId?.let { append("id=").append(it).append(' ') }
                append(label.take(80)).append('\n')
            }
        }
    }

    private companion object {
        const val SYSTEM_PROMPT = """
You plan Android accessibility actions. Return ONLY JSON:
{"steps":[{"description":"...","irreversible":false,"action":{"type":"launch|tap|set_text|ime_enter|scroll|back|home|wait","query":"","text":"","input":"","exact":false,"forward":true}}]}
Never plan banking, payments, passwords, or deletes. Prefer launch by app name, then tap by visible text.
"""
    }
}

object PlanJson {
    fun parse(raw: String): Plan? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val root = JSONObject(raw.substring(start, end + 1))
            val array = root.optJSONArray("steps") ?: return null
            val steps = mutableListOf<PlanStep>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val action = parseAction(item.optJSONObject("action") ?: continue) ?: continue
                val irreversible = item.optBoolean("irreversible", false) || SecurityPolicy.isIrreversible(action, null)
                steps += PlanStep(
                    index = steps.size,
                    description = item.optString("description", action.label()),
                    action = action,
                    isIrreversible = irreversible,
                )
            }
            if (steps.isEmpty()) null else Plan(
                steps,
                steps.mapIndexedNotNull { index, step -> if (step.isIrreversible) index else null }.toSet(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAction(json: JSONObject): AgentAction? {
        return when (json.optString("type")) {
            "tap" -> AgentAction.Tap(selector(json))
            "long_press" -> AgentAction.LongPress(selector(json))
            "set_text" -> AgentAction.SetText(selector(json), json.optString("input", json.optString("text")))
            "scroll" -> AgentAction.Scroll(selector(json), json.optBoolean("forward", true))
            "wait" -> AgentAction.WaitFor(selector(json))
            "launch" -> AgentAction.LaunchApp(json.optString("query", json.optString("text")))
            "ime_enter" -> AgentAction.ImeEnter
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "recents" -> AgentAction.Recents
            "notifications" -> AgentAction.OpenNotifications
            else -> null
        }
    }

    private fun selector(json: JSONObject): ElementSelector {
        val id = json.optString("resourceId")
        if (id.isNotBlank()) return ElementSelector.ByResourceId(id)
        val text = json.optString("text")
        if (text.isNotBlank()) return ElementSelector.ByText(text, json.optBoolean("exact", false))
        val desc = json.optString("contentDescription")
        if (desc.isNotBlank()) return ElementSelector.ByContentDescription(desc)
        return ElementSelector.ByFocusedEditable
    }
}
