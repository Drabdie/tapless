package com.aiphoneagent.network

import com.aiphoneagent.common.AgentError
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.AppDispatchers
import com.aiphoneagent.common.SecurityPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Provider {
    OPENAI, ANTHROPIC, GEMINI, OPENROUTER, GROQ, OLLAMA;

    fun defaultModel(): String = when (this) {
        OPENAI -> "gpt-4o-mini"
        ANTHROPIC -> "claude-3-5-haiku-20241022"
        GEMINI -> "gemini-2.0-flash"
        OPENROUTER -> "openai/gpt-4o-mini"
        GROQ -> "llama-3.3-70b-versatile"
        OLLAMA -> "llama3.2"
    }

    fun label(): String = when (this) {
        OPENAI -> "OpenAI"
        ANTHROPIC -> "Anthropic"
        GEMINI -> "Gemini"
        OPENROUTER -> "OpenRouter"
        GROQ -> "Groq"
        OLLAMA -> "Ollama"
    }
}

enum class Role { SYSTEM, USER, ASSISTANT }

enum class ResponseFormat { TEXT, JSON }

data class LlmMessage(val role: Role, val content: String, val imageBase64: String? = null)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Float = 0.2f,
    val responseFormat: ResponseFormat = ResponseFormat.JSON,
    val maxTokens: Int = 2000,
)

data class Usage(val inputTokens: Int?, val outputTokens: Int?)

data class LlmResponse(val content: String, val usage: Usage?)

interface LlmClient {
    suspend fun complete(request: LlmRequest): AgentResult<LlmResponse>
}

class FilteringLlmClient(
    private val delegate: LlmClient,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): AgentResult<LlmResponse> {
        val safe = request.copy(
            messages = request.messages.map { message ->
                message.copy(content = SecurityPolicy.sanitizeText(message.content))
            },
        )
        return delegate.complete(safe)
    }
}

class HttpLlmClient(
    private val http: OkHttpClient,
    private val provider: Provider,
    private val apiKey: String?,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): AgentResult<LlmResponse> = withContext(AppDispatchers.io) {
        if (provider != Provider.OLLAMA && apiKey.isNullOrBlank()) {
            return@withContext AgentResult.Failure(AgentError.LlmError, "Нет API-ключа")
        }
        try {
            val built = buildRequest(request)
            http.newCall(built).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AgentResult.Failure(
                        AgentError.LlmError,
                        "HTTP ${response.code}: ${raw.take(180)}",
                    )
                }
                val text = extract(raw)
                    ?: return@withContext AgentResult.Failure(AgentError.LlmError, "Пустой ответ модели")
                AgentResult.Success(LlmResponse(text, null))
            }
        } catch (error: Exception) {
            AgentResult.Failure(AgentError.LlmError, error.message ?: "Сеть недоступна")
        }
    }

    private fun buildRequest(request: LlmRequest): Request {
        val json = JSONObject()
        val url: String
        val builder = Request.Builder()
        when (provider) {
            Provider.ANTHROPIC -> {
                url = "https://api.anthropic.com/v1/messages"
                json.put("model", request.model)
                json.put("max_tokens", request.maxTokens)
                json.put("temperature", request.temperature)
                val messages = JSONArray()
                request.messages.filter { it.role != Role.SYSTEM }.forEach { message ->
                    messages.put(
                        JSONObject()
                            .put("role", message.role.name.lowercase())
                            .put("content", message.content),
                    )
                }
                json.put("messages", messages)
                request.messages.firstOrNull { it.role == Role.SYSTEM }?.let {
                    json.put("system", it.content)
                }
                builder.header("x-api-key", apiKey.orEmpty())
                builder.header("anthropic-version", "2023-06-01")
            }
            Provider.GEMINI -> {
                url = "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent?key=$apiKey"
                val contents = JSONArray()
                request.messages.filter { it.role != Role.SYSTEM }.forEach { message ->
                    contents.put(
                        JSONObject()
                            .put("role", if (message.role == Role.ASSISTANT) "model" else "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", message.content))),
                    )
                }
                json.put("contents", contents)
                request.messages.firstOrNull { it.role == Role.SYSTEM }?.let {
                    json.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it.content))))
                }
                json.put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", request.temperature)
                        .put("maxOutputTokens", request.maxTokens),
                )
            }
            else -> {
                url = when (provider) {
                    Provider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                    Provider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                    Provider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                    Provider.OLLAMA -> "http://127.0.0.1:11434/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                json.put("model", request.model)
                json.put("temperature", request.temperature)
                json.put("max_tokens", request.maxTokens)
                if (request.responseFormat == ResponseFormat.JSON && provider != Provider.OLLAMA) {
                    json.put("response_format", JSONObject().put("type", "json_object"))
                }
                val messages = JSONArray()
                request.messages.forEach { message ->
                    val content = if (message.imageBase64.isNullOrBlank()) {
                        message.content
                    } else {
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", message.content))
                            .put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put(
                                        "image_url",
                                        JSONObject().put("url", "data:image/jpeg;base64,${message.imageBase64}"),
                                    ),
                            )
                    }
                    messages.put(
                        JSONObject()
                            .put("role", message.role.name.lowercase())
                            .put("content", content),
                    )
                }
                json.put("messages", messages)
                if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
            }
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        return builder.url(url).post(body).build()
    }

    private fun extract(raw: String): String? {
        val json = JSONObject(raw)
        return when (provider) {
            Provider.ANTHROPIC -> json.optJSONArray("content")?.optJSONObject(0)?.optString("text")
            Provider.GEMINI -> json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
            else -> json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
        }?.takeIf { it.isNotBlank() }
    }
}

@Singleton
class LlmClientFactory @Inject constructor(
    private val store: ApiKeyStore,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun create(provider: Provider = store.getProvider(), model: String = store.getModel(provider)): LlmClient {
        return FilteringLlmClient(HttpLlmClient(http, provider, store.getKey(provider)))
    }
}
