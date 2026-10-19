package com.aiphoneagent.app

import android.content.Context
import android.content.Intent
import com.aiphoneagent.mcp.CompanionClient

/**
 * Bridges the main app to the separately-installed companion agent APK. The main app keeps
 * working with its own in-app accessibility service; when the companion is present it can
 * additionally drive the device through the companion's local MCP endpoint.
 */
object CompanionBridge {
    const val COMPANION_PACKAGE = "com.aiphoneagent.companion"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(COMPANION_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun launch(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(COMPANION_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    suspend fun isServerHealthy(port: Int = 8765): Boolean =
        CompanionClient("http://127.0.0.1:$port").health()
}
