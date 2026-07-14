package com.cpsoverlay.counter

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        var instance: OverlayService? = null
            private set
        private const val CHANNEL_ID = "cps_overlay_channel"
        private const val NOTIF_ID = 1
        private const val WINDOW_MS = 1000L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var cpsReadout: TextView
    private lateinit var fpsReadout: TextView
    private lateinit var historyGraph: ClickHistoryView
    private lateinit var keyHud: KeyHudView

    private val lmbTimestamps = ArrayDeque<Long>()
    private val rmbTimestamps = ArrayDeque<Long>()
    private val cpsHistory = mutableListOf<Int>()

    private var frameCount = 0
    private var lastFpsTime = System.nanoTime()
    private val choreographer = Choreographer.getInstance()

    private var hidden = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification()
        setupOverlay()
        startCpsTicker()
        startFpsEstimator()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "CPS Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CPS Overlay running")
            .setContentText("Tap to open controls")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager.addView(overlayView, params)

        cpsReadout = overlayView.findViewById(R.id.cpsReadout)
        fpsReadout = overlayView.findViewById(R.id.fpsReadout)
        historyGraph = overlayView.findViewById(R.id.historyGraph)
        keyHud = overlayView.findViewById(R.id.keyHud)

        setupDragHandle()
        setupTapButton(overlayView.findViewById(R.id.lmbButton), isLmb = true)
        setupTapButton(overlayView.findViewById(R.id.rmbButton), isLmb = false)
    }

    private fun setupDragHandle() {
        val handle = overlayView.findViewById<android.view.View>(R.id.dragHandle)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val held = System.currentTimeMillis() - downTime
                    val moved = kotlin.math.abs(event.rawX - touchX) > 12 ||
                            kotlin.math.abs(event.rawY - touchY) > 12
                    if (held > 500 && !moved) toggleVisibility()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTapButton(button: Button, isLmb: Boolean) {
        button.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (isLmb) lmbTimestamps.addLast(now) else rmbTimestamps.addLast(now)

                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                val tapX = loc[0] + v.width / 2f
                val tapY = loc[1] + v.height / 2f
                TapForwardService.instance?.dispatchTapAt(tapX, tapY)
            }
            false
        }
    }

    private fun startCpsTicker() {
        val handler = android.os.Handler(mainLooper)
        val ticker = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                while (lmbTimestamps.isNotEmpty() && now - lmbTimestamps.first() > WINDOW_MS) lmbTimestamps.removeFirst()
                while (rmbTimestamps.isNotEmpty() && now - rmbTimestamps.first() > WINDOW_MS) rmbTimestamps.removeFirst()

                val lmbCps = lmbTimestamps.size
                val rmbCps = rmbTimestamps.size
                cpsReadout.text = "LMB $lmbCps  |  RMB $rmbCps CPS"

                cpsHistory.add(lmbCps + rmbCps)
                if (cpsHistory.size > 20) cpsHistory.removeAt(0)
                historyGraph.updateHistory(cpsHistory)

                handler.postDelayed(this, 200)
            }
        }
        handler.post(ticker)
    }

    private fun startFpsEstimator() {
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCount++
                val elapsed = frameTimeNanos - lastFpsTime
                if (elapsed >= 1_000_000_000L) {
                    fpsReadout.text = "Overlay FPS: $frameCount (est.)"
                    frameCount = 0
                    lastFpsTime = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        })
    }

    fun onWasdKey(key: Char, isDown: Boolean) {
        keyHud.setKeyState(key, isDown)
    }

    fun toggleVisibility() {
        hidden = !hidden
        overlayView.visibility = if (hidden) android.view.View.GONE else android.view.View.VISIBLE
    }
}
