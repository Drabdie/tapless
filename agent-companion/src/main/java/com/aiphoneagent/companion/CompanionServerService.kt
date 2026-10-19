package com.aiphoneagent.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.aiphoneagent.mcp.McpProtocol
import com.aiphoneagent.mcp.McpServer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Small facade the UI uses to start/stop the local MCP server foreground service. */
object CompanionServer {
    @Volatile
    var isRunning: Boolean = false
        internal set

    @Volatile
    var port: Int = McpProtocol.DEFAULT_PORT
        internal set

    fun start(context: Context) {
        context.startForegroundService(Intent(context, CompanionServerService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, CompanionServerService::class.java))
    }
}

/**
 * Foreground service that hosts the local MCP HTTP server so external AI clients
 * (Claude Desktop, Cursor, OpenClaw, ...) can drive the phone. Runs while the app is in
 * the background and shows a persistent notification with a STOP action.
 */
@AndroidEntryPoint
class CompanionServerService : Service() {
    @Inject lateinit var server: McpServer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        server.start()
        CompanionServer.port = server.boundPort()
        CompanionServer.isRunning = server.isRunning
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server.stop()
        CompanionServer.isRunning = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI Companion", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CompanionServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("AI Phone Agent Companion")
            .setContentText("MCP-сервер: http://127.0.0.1:${CompanionServer.port}/mcp")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "STOP", stopIntent).build())
            .apply { pending?.let { setContentIntent(it) } }
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "ai_companion_server"
        const val NOTIFICATION_ID = 5201
        const val ACTION_STOP = "com.aiphoneagent.companion.STOP"
    }
}
