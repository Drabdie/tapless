package com.aiphoneagent.vision

import com.aiphoneagent.common.UiRect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single UI element detected by the vision model on a screenshot. Coordinates are in
 * screen pixels (top-left origin) so they can be fed straight into a tap gesture.
 */
@Serializable
data class VisionElement(
    @SerialName("label") val label: String = "",
    @SerialName("type") val type: String = "unknown",
    @SerialName("x") val x: Int = 0,
    @SerialName("y") val y: Int = 0,
    @SerialName("w") val w: Int = 0,
    @SerialName("h") val h: Int = 0,
    @SerialName("confidence") val confidence: Double = 0.0,
    @SerialName("actionable") val actionable: Boolean = false,
) {
    val bounds: UiRect get() = UiRect(x, y, x + w.coerceAtLeast(0), y + h.coerceAtLeast(0))
    val centerX: Int get() = x + w / 2
    val centerY: Int get() = y + h / 2

    fun describe(): String = buildString {
        append(type).append(" \"").append(label.take(60)).append('"')
        if (actionable) append(" [tappable]")
        append(" @").append(centerX).append(',').append(centerY)
    }
}

/** Raw structured result of a single vision pass over a screenshot. */
data class VisionAnalysis(
    val summary: String,
    val elements: List<VisionElement>,
    val rawText: String,
    val provider: String,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
    val usedVision: Boolean = true,
)

/**
 * The merged understanding of a screen: the accessibility tree rendered to text, plus the
 * optional vision description. [mergedText] is what the planner/LLM ultimately consumes.
 */
data class ScreenUnderstanding(
    val packageName: String,
    val treeText: String,
    val visionText: String,
    val mergedText: String,
    val elements: List<VisionElement> = emptyList(),
    val usedVision: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)
