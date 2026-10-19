package com.aiphoneagent.common

/**
 * Renders an accessibility tree to a compact, LLM-friendly textual description.
 * Shared by the vision merge layer and the MCP tools so both perceive screens identically.
 */
object UiTextRenderer {
    fun render(screen: ScreenSnapshot?, maxNodes: Int = 70): String {
        if (screen == null) return "screen: unknown"
        val nodes = screen.root.flatten()
            .filter { it.visible }
            .filter {
                !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() ||
                    it.editable || it.clickable
            }
            .take(maxNodes)
        return buildString {
            append("package=").append(screen.packageName)
            if (screen.width > 0 && screen.height > 0) {
                append(" size=").append(screen.width).append('x').append(screen.height)
            }
            append('\n')
            nodes.forEach { append(renderNode(it)).append('\n') }
        }.trimEnd()
    }

    fun renderNode(node: UiNode): String = buildString {
        append("- ")
        if (node.clickable) append("[tap] ")
        if (node.editable) append("[input] ")
        if (node.scrollable) append("[scroll] ")
        if (!node.enabled) append("[disabled] ")
        val label = node.text ?: node.contentDescription ?: node.className?.substringAfterLast('.') ?: "?"
        append(label.take(80))
        node.resourceId?.let { append("  id=").append(it.substringAfterLast('/')) }
        if (node.bounds.width > 0) {
            append("  @").append(node.bounds.centerX).append(',').append(node.bounds.centerY)
        }
    }

    /** Render the tree as structured JSON-ish lines including bounds, for MCP clients. */
    fun renderDetailed(screen: ScreenSnapshot?, maxNodes: Int = 120): String {
        if (screen == null) return "screen: unknown"
        val nodes = screen.root.flatten().filter { it.visible }.take(maxNodes)
        return buildString {
            append("package=").append(screen.packageName).append('\n')
            nodes.forEach { node ->
                val label = node.text ?: node.contentDescription ?: node.className ?: "?"
                append("- \"").append(label.take(80)).append("\"")
                append(" type=").append(node.className?.substringAfterLast('.') ?: "?")
                append(" bounds=").append(node.bounds.left).append(',').append(node.bounds.top)
                    .append(',').append(node.bounds.right).append(',').append(node.bounds.bottom)
                if (node.clickable) append(" clickable")
                if (node.editable) append(" editable")
                if (node.scrollable) append(" scrollable")
                if (!node.enabled) append(" disabled")
                node.resourceId?.let { append(" id=").append(it) }
                append('\n')
            }
        }.trimEnd()
    }
}
