package com.aiphoneagent.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.aiphoneagent.common.KillSwitch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

/**
 * Foreground service that draws a draggable floating bubble over other apps.
 * Tap opens the agent; long-press triggers the kill switch (instant stop).
 */
@AndroidEntryPoint
class BubbleService : Service() {
    @Inject lateinit var killSwitch: KillSwitch

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        BubbleController.isRunning = false
        super.onDestroy()
    }

    private fun addBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val view = TextView(this).apply {
            text = "AI"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2F6FED"))
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#FFFFFF"))
            }
            elevation = 8f
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }
        attachTouch(view, params)
        runCatching { windowManager.addView(view, params) }
        bubble = view
        layoutParams = params
    }

    private fun attachTouch(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openApp()
                    true
                }
                else -> false
            }
        }
        view.setOnLongClickListener {
            killSwitch.kill()
            true
        }
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launch)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI-агент",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("AI Phone Agent")
            .setContentText("Плавающий помощник активен")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "ai_agent_bubble"
        const val NOTIFICATION_ID = 4201
    }
}
