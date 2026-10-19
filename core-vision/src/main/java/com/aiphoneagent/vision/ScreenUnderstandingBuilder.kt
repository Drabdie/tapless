package com.aiphoneagent.vision

import com.aiphoneagent.common.ScreenSnapshot
import com.aiphoneagent.common.UiNode

/**
 * Merges the accessibility tree and the optional vision analysis into a single textual
 * understanding of the screen. Keeping this pure (no Android types) makes it unit-testable
 * and lets the planner consume one uniform description regardless of the perception source.
 */
object ScreenUnderstandingBuilder {

    fun build(
        tree: ScreenSnapshot?,
        vision: VisionAnalysis?,
        maxTreeNodes: Int = 70,
    ): ScreenUnderstanding {
        val treeText = treeToText(tree, maxTreeNodes)
        val visionText = visionText(vision)
        val merged = mergeText(treeText, visionText)
        return ScreenUnderstanding(
            packageName = tree?.packageName.orEmpty(),
            treeText = treeText,
            visionText = visionText,
            mergedText = merged,
            elements = vision?.elements.orEmpty(),
            usedVision = vision?.usedVision == true,
            timestamp = System.currentTimeMillis(),
        )
    }

    /** Render the accessibility tree to a compact, LLM-friendly list of elements. */
    fun treeToText(screen: ScreenSnapshot?, maxNodes: Int): String =
        com.aiphoneagent.common.UiTextRenderer.render(screen, maxNodes)

    fun visionText(vision: VisionAnalysis?): String {
        if (vision == null || !vision.usedVision) return ""
        return buildString {
            if (vision.summary.isNotBlank()) {
                append("summary: ").append(vision.summary.take(400)).append('\n')
            }
            vision.elements.take(60).forEach { element ->
                append("- ").append(element.describe()).append('\n')
            }
        }.trimEnd()
    }

    fun mergeText(treeText: String, visionText: String): String {
        if (visionText.isBlank()) return treeText
        if (treeText.isBlank()) return visionText
        return buildString {
            append("ACCESSIBILITY TREE:\n").append(treeText)
            append("\n\nVISION ANALYSIS:\n").append(visionText)
        }
    }

    /**
     * Cross-reference vision elements with the accessibility tree to find a tappable
     * coordinate for [query]. Returns the best vision element whose label matches.
     */
    fun findVisionElement(vision: VisionAnalysis?, query: String): VisionElement? {
        if (vision == null || query.isBlank()) return null
        val needle = query.lowercase()
        return vision.elements
            .filter { it.actionable }
            .maxByOrNull { element ->
                val label = element.label.lowercase()
                when {
                    label == needle -> 1000.0
                    label.contains(needle) -> 500.0 + element.confidence
                    needle.contains(label) && label.isNotBlank() -> 300.0 + element.confidence
                    else -> element.confidence
                }
            }
            ?.takeIf { it.label.lowercase().contains(needle) || needle.contains(it.label.lowercase()) }
    }
}
