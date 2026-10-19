package com.aiphoneagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.provider.Settings
import com.aiphoneagent.common.AgentError
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.common.GestureRunner
import com.aiphoneagent.common.GlobalGesture
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ServiceGestureRunner(private val context: Context) : GestureRunner {
    override suspend fun tap(x: Int, y: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(path, durationMs.coerceIn(40, 3_000))
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }
        return dispatch(path, durationMs.coerceIn(80, 3_000))
    }

    override suspend fun global(which: GlobalGesture): Boolean {
        val service = AccessibilityBridge.service ?: return false
        val action = when (which) {
            GlobalGesture.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalGesture.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalGesture.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalGesture.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        return service.performGlobalAction(action)
    }

    override suspend fun launch(query: String): AgentResult<Unit> {
        val name = query.trim()
        val special = when (name.lowercase()) {
            "wifi", "wi-fi", "вайфай", "вай-фай" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth", "блютуз" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "settings", "настройки" -> Intent(Settings.ACTION_SETTINGS)
            else -> null
        }
        val intent = special
            ?: context.packageManager.getLaunchIntentForPackage(name)
            ?: findByLabel(name)
        if (intent == null) {
            return AgentResult.Failure(AgentError.ElementNotFound, "Приложение не найдено: $name")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            AgentResult.Success(Unit)
        } catch (error: Exception) {
            AgentResult.Failure(AgentError.Unknown, error.message ?: "Не удалось открыть приложение")
        }
    }

    override fun screenSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean {
        val service = AccessibilityBridge.service ?: return false
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return suspendCancellableCoroutine { continuation ->
            val accepted = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) continuation.resume(false)
        }
    }

    private fun findByLabel(query: String): Intent? {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, PackageManager.MATCH_DEFAULT_ONLY)
        val hit = apps.firstOrNull { info ->
            val label = info.loadLabel(pm)?.toString().orEmpty()
            label.contains(query, ignoreCase = true) ||
                info.activityInfo.packageName.contains(query, ignoreCase = true)
        } ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
    }
}
