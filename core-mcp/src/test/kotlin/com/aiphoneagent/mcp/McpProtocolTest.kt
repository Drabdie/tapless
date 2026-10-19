package com.aiphoneagent.mcp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

class McpProtocolTest {

    private class EchoTool : McpToolHandler {
        override val definition = McpToolDef(
            name = "echo",
            description = "Echoes the given text",
            inputSchema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject { put("text", buildJsonObject { put("type", "string") }) })
            },
        )

        override suspend fun invoke(args: JsonObject): McpToolResult {
            val text = args["text"]?.jsonPrimitive?.content ?: return McpToolResult.error("missing text")
            return McpToolResult.ok("echo: $text")
        }
    }

    private class BoomTool : McpToolHandler {
        override val definition = McpToolDef("boom", "Always throws", buildJsonObject { put("type", "object") })
        override suspend fun invoke(args: JsonObject): McpToolResult = throw IllegalStateException("kaboom")
    }

    private fun registry() = McpToolRegistry(listOf(EchoTool(), BoomTool()))

    @Test fun registryListsAndFindsTools() {
        val reg = registry()
        assertThat(reg.size).isEqualTo(2)
        assertThat(reg.list().map { it.name }).containsExactly("echo", "boom")
        assertThat(reg.find("echo")).isNotNull()
        assertThat(reg.find("missing")).isNull()
    }

    @Test fun initializeReturnsServerInfo() = runTest {
        val session = McpSession(registry())
        val response = session.handle(
            JsonRpcRequest(id = JsonPrimitive(1), method = McpProtocol.METHOD_INITIALIZE),
        )
        assertThat(response).isNotNull()
        assertThat(response!!.error).isNull()
        val result = response.result!!.jsonObject
        assertThat(result["protocolVersion"]!!.jsonPrimitive.content).isEqualTo(McpProtocol.PROTOCOL_VERSION)
        assertThat(result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            .isEqualTo(McpProtocol.SERVER_NAME)
    }

    @Test fun toolsListReturnsDefinitions() = runTest {
        val session = McpSession(registry())
        val response = session.handle(
            JsonRpcRequest(id = JsonPrimitive(2), method = McpProtocol.METHOD_TOOLS_LIST),
        )
        val tools = response!!.result!!.jsonObject["tools"]!!.jsonArray
        assertThat(tools).hasSize(2)
        assertThat(tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })
            .containsExactly("echo", "boom")
    }

    @Test fun toolsCallInvokesTool() = runTest {
        val session = McpSession(registry())
        val request = JsonRpcRequest(
            id = JsonPrimitive(3),
            method = McpProtocol.METHOD_TOOLS_CALL,
            params = buildJsonObject {
                put("name", JsonPrimitive("echo"))
                put("arguments", buildJsonObject { put("text", JsonPrimitive("hello")) })
            },
        )
        val response = session.handle(request)
        assertThat(response!!.error).isNull()
        val content = response.result!!.jsonObject["content"]!!.jsonArray
        assertThat(content.first().jsonObject["text"]!!.jsonPrimitive.content).isEqualTo("echo: hello")
    }

    @Test fun unknownToolReturnsError() = runTest {
        val session = McpSession(registry())
        val request = JsonRpcRequest(
            id = JsonPrimitive(4),
            method = McpProtocol.METHOD_TOOLS_CALL,
            params = buildJsonObject { put("name", JsonPrimitive("nope")) },
        )
        val response = session.handle(request)
        assertThat(response!!.error).isNotNull()
        assertThat(response.error!!.code).isEqualTo(McpProtocol.INVALID_PARAMS)
    }

    @Test fun throwingToolIsReportedAsError() = runTest {
        val session = McpSession(registry())
        val request = JsonRpcRequest(
            id = JsonPrimitive(5),
            method = McpProtocol.METHOD_TOOLS_CALL,
            params = buildJsonObject { put("name", JsonPrimitive("boom")) },
        )
        val response = session.handle(request)
        assertThat(response!!.error).isNull()
        val result = response.result!!.jsonObject
        assertThat(result["isError"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test fun unknownMethodReturnsMethodNotFound() = runTest {
        val session = McpSession(registry())
        val response = session.handle(JsonRpcRequest(id = JsonPrimitive(6), method = "does/not/exist"))
        assertThat(response!!.error!!.code).isEqualTo(McpProtocol.METHOD_NOT_FOUND)
    }

    @Test fun notificationReturnsNull() = runTest {
        val session = McpSession(registry())
        val response = session.handle(JsonRpcRequest(method = McpProtocol.METHOD_INITIALIZED))
        assertThat(response).isNull()
    }

    @Test fun handleRawParsesAndDispatches() = runTest {
        val session = McpSession(registry())
        val raw = """{"jsonrpc":"2.0","id":7,"method":"ping"}"""
        val response = session.handleRaw(raw)
        assertThat(response).isNotNull()
        assertThat(response).contains("\"jsonrpc\":\"2.0\"")
    }

    @Test fun handleRawRejectsMalformedJson() = runTest {
        val session = McpSession(registry())
        val response = session.handleRaw("{ not json ]")
        assertThat(response).contains("${McpProtocol.PARSE_ERROR}")
    }
}
