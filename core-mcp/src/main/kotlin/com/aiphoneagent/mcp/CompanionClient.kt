package com.aiphoneagent.mcp

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Client used by the main app (or any other app) to talk to a running companion agent
 * over its localhost MCP endpoint. Dependency-free (java.net) so it works everywhere.
 */
class CompanionClient(
    private val baseUrl: String = "http://127.0.0.1:${McpProtocol.DEFAULT_PORT}",
) {
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            connection.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listTools(): List<McpToolDef> = withContext(Dispatchers.IO) {
        val response = post("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
            ?: return@withContext emptyList()
        val root = runCatching { McpJson.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return@withContext emptyList()
        val tools = root["result"]?.jsonObject?.get("tools")?.jsonArray ?: return@withContext emptyList()
        tools.mapNotNull {
            runCatching { McpJson.decodeFromJsonElement(McpToolDef.serializer(), it) }.getOrNull()
        }
    }

    suspend fun callTool(
        name: String,
        args: JsonObject = JsonObject(emptyMap()),
    ): McpToolResult? = withContext(Dispatchers.IO) {
        val payload = JsonObject(
            mapOf(
                "jsonrpc" to kotlinx.serialization.json.JsonPrimitive("2.0"),
                "id" to kotlinx.serialization.json.JsonPrimitive(2),
                "method" to kotlinx.serialization.json.JsonPrimitive("tools/call"),
                "params" to JsonObject(
                    mapOf(
                        "name" to kotlinx.serialization.json.JsonPrimitive(name),
                        "arguments" to args,
                    ),
                ),
            ),
        )
        val response = post(McpJson.encodeToString(JsonObject.serializer(), payload))
            ?: return@withContext null
        val root = runCatching { McpJson.parseToJsonElement(response).jsonObject }.getOrNull()
            ?: return@withContext null
        root["result"]?.let {
            runCatching { McpJson.decodeFromJsonElement(McpToolResult.serializer(), it) }.getOrNull()
        }
    }

    private fun post(body: String): String? {
        return try {
            val connection = (URL("$baseUrl/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (_: Exception) {
            null
        }
    }
}
