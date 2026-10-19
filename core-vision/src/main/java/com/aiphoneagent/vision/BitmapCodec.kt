package com.aiphoneagent.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Encodes screenshots to compact base64 JPEGs suitable for multimodal LLM requests.
 * Down-scaling keeps token usage and bandwidth low without losing UI legibility.
 */
object BitmapCodec {
    const val DEFAULT_MAX_DIM = 1080
    const val DEFAULT_QUALITY = 72

    fun toJpegBase64(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        maxDim: Int = DEFAULT_MAX_DIM,
    ): String {
        val scaled = scaleDown(bitmap, maxDim)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeJpeg(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim || maxDim <= 0) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
