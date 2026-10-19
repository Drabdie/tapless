package com.aiphoneagent.mcp

/** Constants for the MCP (Model Context Protocol) JSON-RPC 2.0 transport. */
object McpProtocol {
    const val JSONRPC = "2.0"
    const val PROTOCOL_VERSION = "2024-11-05"
    const val SERVER_NAME = "ai-phone-agent"
    const val SERVER_VERSION = "1.0.0"
    const val DEFAULT_PORT = 8765

    // JSON-RPC 2.0 error codes
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    // MCP methods
    const val METHOD_INITIALIZE = "initialize"
    const val METHOD_INITIALIZED = "notifications/initialized"
    const val METHOD_PING = "ping"
    const val METHOD_TOOLS_LIST = "tools/list"
    const val METHOD_TOOLS_CALL = "tools/call"
}
