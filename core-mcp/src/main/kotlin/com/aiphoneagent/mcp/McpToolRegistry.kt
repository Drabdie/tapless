package com.aiphoneagent.mcp

import kotlinx.serialization.json.JsonObject

/** A single callable MCP tool. */
interface McpToolHandler {
    val definition: McpToolDef
    suspend fun invoke(args: JsonObject): McpToolResult
}

/** Holds the set of tools exposed to MCP clients. */
class McpToolRegistry(tools: List<McpToolHandler>) {
    private val byName: Map<String, McpToolHandler> = tools.associateBy { it.definition.name }

    val size: Int get() = byName.size

    fun list(): List<McpToolDef> = byName.values.map { it.definition }

    fun find(name: String): McpToolHandler? = byName[name]
}
