package com.aiphoneagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import com.aiphoneagent.common.AgentError
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.CapturedFrame
import com.aiphoneagent.common.ScreenshotProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Captures the screen through the accessibility service (API 30+). This needs no extra
 * permission beyond the accessibility service itself, which is why the vision layer can
 * work "out of the box" on Android 11 and newer.
 */
@Singleton
class AccessibilityScreenshotProvider @Inject constructor() : ScreenshotProvider {

    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && AccessibilityBridge.service != null

    override suspend fun capture(): AgentResult<CapturedFrame> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return AgentResult.Failure(AgentError.Unknown, "Захват экрана требует Android 11+")
        }
        val service = AccessibilityBridge.service
            ?: return AgentResult.Failure(AgentError.Unknown, "Служба спецвозможностей выключена")
        return suspendCancellableCoroutine { continuation ->
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val result = try {
                                val hardware = screenshot.hardwareBuffer
                                val wrapped = Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace)
                                val software = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                hardware.close()
                                if (software != null) {
                                    val frame = CapturedFrame(
                                        base64Jpeg = encodeJpeg(software),
                                        width = software.width,
                                        height = software.height,
                                        source = "accessibility",
                                    )
                                    software.recycle()
                                    AgentResult.Success(frame)
                                } else {
                                    AgentResult.Failure(AgentError.Unknown, "Не удалось декодировать кадр")
                                }
                            } catch (error: Exception) {
                                AgentResult.Failure(AgentError.Unknown, error.message ?: "Ошибка захвата")
                            }
                            if (continuation.isActive) continuation.resume(result)
                        }

                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    AgentResult.Failure(AgentError.Unknown, "Захват экрана не удался ($errorCode)"),
                                )
                            }
                        }
                    },
                )
            } catch (error: Exception) {
                if (continuation.isActive) {
                    continuation.resume(AgentResult.Failure(AgentError.Unknown, error.message ?: "Ошибка захвата"))
                }
            }
        }
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int = 72, maxDim: Int = 1080): String {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > maxDim && maxDim > 0) {
            val ratio = maxDim.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
