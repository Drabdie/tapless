package com.aiphoneagent.mcp

/**
 * The phone-control MCP server. Exposes a JSON-RPC 2.0 endpoint at POST /mcp plus a
 * /health probe, so MCP clients (Claude Desktop via mcp-remote, Cursor, OpenClaw, ...)
 * can drive the device.
 */
class McpServer(
    registry: McpToolRegistry,
    port: Int = McpProtocol.DEFAULT_PORT,
    serverInfo: McpServerInfo = McpServerInfo(McpProtocol.SERVER_NAME, McpProtocol.SERVER_VERSION),
) {
    private val session = McpSession(registry, serverInfo)
    private val http = MiniHttpServer(port) { request -> route(request) }

    val isRunning: Boolean get() = http.isRunning
    fun boundPort(): Int = http.boundPort()

    fun start(): Boolean = http.start()
    fun stop() = http.stop()

    suspend fun route(request: HttpRequest): HttpResponse {
        val path = request.path.substringBefore('?')
        return when {
            request.method.equals("OPTIONS", ignoreCase = true) -> HttpResponse.noContent()
            request.method.equals("GET", ignoreCase = true) && path == "/health" ->
                HttpResponse.json("""{"status":"ok","server":"${McpProtocol.SERVER_NAME}","tools":${registry.size}}""")
            request.method.equals("GET", ignoreCase = true) && (path == "/" || path == "/info") ->
                HttpResponse.json(
                    """{"name":"${McpProtocol.SERVER_NAME}","version":"${McpProtocol.SERVER_VERSION}",""" +
                        """"mcp":"POST /mcp","protocol":"${McpProtocol.PROTOCOL_VERSION}"}""",
                )
            request.method.equals("POST", ignoreCase = true) && (path == "/mcp" || path == "/" || path == "/sse") -> {
                val response = session.handleRaw(request.body)
                if (response == null) {
                    HttpResponse(202, "text/plain; charset=utf-8", "")
                } else {
                    HttpResponse.json(response)
                }
            }
            else -> HttpResponse.json("""{"error":"not found"}""", 404)
        }
    }

    private val registry: McpToolRegistry = registry
}
