package com.aiphoneagent.common

/**
 * A single captured frame of the device screen, already encoded as a base64 JPEG so
 * that it can travel through pure-Kotlin code (and straight into a multimodal LLM)
 * without depending on Android graphics types.
 */
data class CapturedFrame(
    val base64Jpeg: String,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "unknown",
)

/**
 * Port for capturing the current screen. Implemented on Android by the accessibility
 * layer (AccessibilityService.takeScreenshot on API 30+, MediaProjection fallback).
 */
interface ScreenshotProvider {
    /** Whether a capture can currently be attempted. */
    fun isAvailable(): Boolean

    /** Capture the screen. Returns a failure when the platform refuses the request. */
    suspend fun capture(): AgentResult<CapturedFrame>
}

/** A no-op provider used when vision is disabled or unsupported on the device. */
class NoopScreenshotProvider : ScreenshotProvider {
    override fun isAvailable(): Boolean = false
    override suspend fun capture(): AgentResult<CapturedFrame> =
        AgentResult.Failure(AgentError.Unknown, "Захват экрана недоступен на этом устройстве")
}
