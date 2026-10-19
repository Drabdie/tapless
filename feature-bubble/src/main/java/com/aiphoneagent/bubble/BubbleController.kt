package com.aiphoneagent.bubble

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Small facade the app UI uses to start/stop the floating bubble. */
object BubbleController {
    @Volatile
    var isRunning: Boolean = false
        internal set

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun start(context: Context) {
        val intent = Intent(context, BubbleService::class.java)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, BubbleService::class.java))
    }
}
