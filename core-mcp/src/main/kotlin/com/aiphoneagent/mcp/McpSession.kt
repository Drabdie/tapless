package com.aiphoneagent.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Handles a single MCP conversation: dispatches JSON-RPC methods and invokes tools.
 * Pure logic (no I/O), so it is fully unit-testable.
 */
class McpSession(
    private val registry: McpToolRegistry,
    private val serverInfo: McpServerInfo = McpServerInfo(McpProtocol.SERVER_NAME, McpProtocol.SERVER_VERSION),
    private val protocolVersion: String = McpProtocol.PROTOCOL_VERSION,
) {
    suspend fun handle(request: JsonRpcRequest): JsonRpcResponse? {
        return when (request.method) {
            McpProtocol.METHOD_INITIALIZE -> ok(request.id, buildInitialize())
            McpProtocol.METHOD_INITIALIZED -> null
            McpProtocol.METHOD_PING -> ok(request.id, JsonObject(emptyMap()))
            McpProtocol.METHOD_TOOLS_LIST -> ok(request.id, buildToolsList())
            McpProtocol.METHOD_TOOLS_CALL -> handleToolCall(request)
            else -> err(request.id, McpProtocol.METHOD_NOT_FOUND, "Unknown method: ${request.method}")
        }
    }

    /** Parse + dispatch a raw JSON-RPC body. Returns the JSON response, or null for notifications. */
    suspend fun handleRaw(body: String): String? {
        val request = try {
            McpJson.decodeFromString(JsonRpcRequest.serializer(), body)
        } catch (_: Exception) {
            return McpJson.encodeToString(
                JsonRpcResponse.serializer(),
                err(null, McpProtocol.PARSE_ERROR, "Parse error"),
            )
        }
        val response = try {
            handle(request)
        } catch (error: Exception) {
            err(request.id, McpProtocol.INTERNAL_ERROR, error.message ?: "Internal error")
        } ?: return null
        return McpJson.encodeToString(JsonRpcResponse.serializer(), response)
    }

    private fun buildInitialize(): JsonElement {
        val result = McpInitializeResult(
            protocolVersion = protocolVersion,
            capabilities = buildJsonObject { putJsonObject("tools") {} },
            serverInfo = serverInfo,
        )
        return McpJson.encodeToJsonElement(McpInitializeResult.serializer(), result)
    }

    private fun buildToolsList(): JsonElement = buildJsonObject {
        putJsonArray("tools") {
            registry.list().forEach { def ->
                add(McpJson.encodeToJsonElement(McpToolDef.serializer(), def))
            }
        }
    }

    private suspend fun handleToolCall(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params
        val name = params?.get("name")?.jsonPrimitive?.contentOrNull
            ?: return err(request.id, McpProtocol.INVALID_PARAMS, "Missing tool name")
        val args = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
        val tool = registry.find(name)
            ?: return err(request.id, McpProtocol.INVALID_PARAMS, "Unknown tool: $name")
        val result = try {
            tool.invoke(args)
        } catch (error: Exception) {
            McpToolResult.error(error.message ?: "Tool failed")
        }
        return ok(request.id, McpJson.encodeToJsonElement(McpToolResult.serializer(), result))
    }

    private fun ok(id: JsonElement?, result: JsonElement) = JsonRpcResponse(id = id, result = result)

    private fun err(id: JsonElement?, code: Int, message: String) =
        JsonRpcResponse(id = id, error = JsonRpcError(code, message))
}
