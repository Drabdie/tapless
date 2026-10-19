package com.aiphoneagent.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Shared JSON configuration: tolerant of extra fields and lenient input. */
val McpJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String = "",
    val params: JsonObject? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

@Serializable
data class McpContent(
    val type: String = "text",
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
) {
    companion object {
        fun text(value: String) = McpContent(type = "text", text = value)
        fun image(base64: String, mime: String = "image/jpeg") =
            McpContent(type = "image", data = base64, mimeType = mime)
    }
}

@Serializable
data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false,
) {
    companion object {
        fun ok(text: String) = McpToolResult(listOf(McpContent.text(text)), isError = false)
        fun error(text: String) = McpToolResult(listOf(McpContent.text(text)), isError = true)
    }
}

@Serializable
data class McpServerInfo(val name: String, val version: String)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: JsonObject,
    val serverInfo: McpServerInfo,
)
