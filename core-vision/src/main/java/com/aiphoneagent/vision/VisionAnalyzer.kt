package com.aiphoneagent.vision

import com.aiphoneagent.common.AgentError
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.CapturedFrame
import com.aiphoneagent.common.SafeLogger
import com.aiphoneagent.network.ApiKeyStore
import com.aiphoneagent.network.LlmClient
import com.aiphoneagent.network.LlmClientFactory
import com.aiphoneagent.network.LlmMessage
import com.aiphoneagent.network.LlmRequest
import com.aiphoneagent.network.Provider
import com.aiphoneagent.network.ResponseFormat
import com.aiphoneagent.network.Role
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Sends a screenshot to a multimodal LLM and parses the structured UI description.
 *
 * The class depends only on the [LlmClient] interface so it is easy to unit test with a
 * fake client, and so it works with every provider (OpenAI, Anthropic, Gemini, ...).
 */
class VisionAnalyzer(
    private val client: LlmClient,
    private val model: String,
    private val providerLabel: String,
) {
    suspend fun analyze(frame: CapturedFrame, task: String? = null): AgentResult<VisionAnalysis> {
        if (frame.base64Jpeg.isBlank()) {
            return AgentResult.Failure(AgentError.Unknown, "Пустой кадр")
        }
        val request = LlmRequest(
            model = model,
            messages = listOf(
                LlmMessage(Role.SYSTEM, VisionPrompts.SYSTEM),
                LlmMessage(Role.USER, VisionPrompts.userPrompt(task), imageBase64 = frame.base64Jpeg),
            ),
            responseFormat = ResponseFormat.JSON,
            maxTokens = 1500,
        )
        return when (val result = client.complete(request)) {
            is AgentResult.Success -> AgentResult.Success(
                parse(result.value.content, providerLabel, model),
            )
            is AgentResult.Failure -> result
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Parse the model output into a [VisionAnalysis]; never throws. */
        fun parse(raw: String, provider: String, model: String): VisionAnalysis {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) {
                return VisionAnalysis(
                    summary = raw.trim().take(400),
                    elements = emptyList(),
                    rawText = raw,
                    provider = provider,
                    model = model,
                    usedVision = false,
                )
            }
            val body = raw.substring(start, end + 1)
            return try {
                val root = json.parseToJsonElement(body).jsonObject
                val summary = root["summary"]?.jsonPrimitive?.content.orEmpty()
                val elements = root["elements"]?.jsonArray?.mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        VisionElement(
                            label = obj["label"]?.jsonPrimitive?.content.orEmpty(),
                            type = obj["type"]?.jsonPrimitive?.content ?: "unknown",
                            x = obj["x"]?.jsonPrimitive?.int ?: 0,
                            y = obj["y"]?.jsonPrimitive?.int ?: 0,
                            w = obj["w"]?.jsonPrimitive?.int ?: 0,
                            h = obj["h"]?.jsonPrimitive?.int ?: 0,
                            confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            actionable = obj["actionable"]?.jsonPrimitive?.booleanOrNull ?: false,
                        )
                    }.getOrNull()
                }.orEmpty()
                VisionAnalysis(
                    summary = summary,
                    elements = elements,
                    rawText = raw,
                    provider = provider,
                    model = model,
                    usedVision = true,
                )
            } catch (error: Exception) {
                SafeLogger.d("VisionAnalyzer", "parse failed: ${error.message}")
                VisionAnalysis(
                    summary = raw.trim().take(400),
                    elements = emptyList(),
                    rawText = raw,
                    provider = provider,
                    model = model,
                    usedVision = false,
                )
            }
        }

        fun toJson(analysis: VisionAnalysis): JsonObject = JsonObject(emptyMap())
    }
}

/** Builds [VisionAnalyzer] instances for the currently configured provider. */
@Singleton
class VisionAnalyzerFactory @Inject constructor(
    private val factory: LlmClientFactory,
    private val keys: ApiKeyStore,
) {
    fun create(): VisionAnalyzer {
        val provider = keys.getProvider()
        return VisionAnalyzer(
            client = factory.create(provider),
            model = keys.getModel(provider),
            providerLabel = provider.label(),
        )
    }

    /** Vision needs a multimodal model; Ollama/local models are assumed capable if present. */
    fun isVisionLikelySupported(): Boolean {
        val provider = keys.getProvider()
        if (provider == Provider.OLLAMA) return true
        return !keys.getKey(provider).isNullOrBlank()
    }
}
