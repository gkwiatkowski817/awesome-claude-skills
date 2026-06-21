package com.whatsappsuggester.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import com.whatsappsuggester.R
import com.whatsappsuggester.api.GeminiApiClient
import com.whatsappsuggester.utils.Prefs

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.whatsappsuggester.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.whatsappsuggester.HIDE_OVERLAY"
        private const val TAG = "OverlayService"

        // TYPE_APPLICATION_OVERLAY = 2038 (API 26+, always available since minSdk=26)
        private const val TYPE_APPLICATION_OVERLAY = 2038
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val prefs by lazy { Prefs(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 20
        params.y = 160

        val fab = view.findViewById(R.id.fab_suggest) as ImageButton
        val progress = view.findViewById(R.id.progress_loading) as ProgressBar

        fab.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX - dx
                        params.y = initialY + dy
                        try { windowManager.updateViewLayout(view, params) } catch (ignored: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) v.performClick()
                    false
                }
                else -> false
            }
        }

        fab.setOnClickListener {
            if (prefs.apiKey.isBlank()) {
                Toast.makeText(this, getString(R.string.error_no_api_key), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            fab.visibility = View.INVISIBLE
            progress.visibility = View.VISIBLE

            Thread {
                try {
                    val accessibility = WhatsAppAccessibilityService.instance
                    if (accessibility == null) {
                        mainHandler.post {
                            Toast.makeText(this, "Włącz usługę dostępności!", Toast.LENGTH_LONG).show()
                            progress.visibility = View.GONE
                            fab.visibility = View.VISIBLE
                        }
                        return@Thread
                    }

                    val (messages, contactName) = accessibility.collectMessages()

                    if (messages.isEmpty()) {
                        mainHandler.post {
                            Toast.makeText(this, "Brak wiadomości do analizy.", Toast.LENGTH_SHORT).show()
                            progress.visibility = View.GONE
                            fab.visibility = View.VISIBLE
                        }
                        return@Thread
                    }

                    val client = GeminiApiClient(prefs.apiKey)
                    val reply = client.generateReply(messages, contactName)

                    mainHandler.post {
                        accessibility.fillTextInput(reply)
                        progress.visibility = View.GONE
                        fab.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating reply", e)
                    mainHandler.post {
                        Toast.makeText(
                            this,
                            "${getString(R.string.error_api)}${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        progress.visibility = View.GONE
                        fab.visibility = View.VISIBLE
                    }
                }
            }.start()
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (ignored: Exception) {}
            overlayView = null
        }
    }
}
