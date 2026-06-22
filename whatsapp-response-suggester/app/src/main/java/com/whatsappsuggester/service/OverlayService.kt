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
        private const val TYPE_APPLICATION_OVERLAY = 2038
        private const val POLL_INTERVAL_MS = 500L
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
    private var isGenerating = false

    private var generateStartedAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            val waActive = WhatsAppAccessibilityService.isWhatsAppActive()
            // While generating, never hide — tapping the overlay can briefly flip isWhatsAppActive() to false
            val generating = isGenerating && (System.currentTimeMillis() - generateStartedAt < 60_000)
            if (waActive && overlayView == null) {
                showOverlay()
            } else if (!waActive && overlayView != null && !generating) {
                hideOverlay()
            }
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
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
        mainHandler.removeCallbacks(pollRunnable)
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
        params.x = 24
        params.y = 200

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
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
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
            if (isGenerating) {
                Toast.makeText(this, "Już generuję odpowiedź, poczekaj...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiKey = prefs.apiKey
            if (apiKey.isBlank()) {
                Toast.makeText(this, getString(R.string.error_no_api_key), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val accessibility = WhatsAppAccessibilityService.instance
            if (accessibility == null) {
                Toast.makeText(this, "Usługa dostępności nie działa! Włącz ją w Ustawieniach.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Szukam wiadomości...", Toast.LENGTH_SHORT).show()

            val (messages, contactName, diagnostics) = accessibility.collectMessages()

            Log.d(TAG, "collectMessages: ${messages.size} msgs, contact=$contactName, diag=$diagnostics")

            if (messages.isEmpty()) {
                Toast.makeText(this, "Brak wiadomości! $diagnostics", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Znalazłem ${messages.size} wiad. od $contactName. Pytam AI...", Toast.LENGTH_SHORT).show()

            isGenerating = true
            generateStartedAt = System.currentTimeMillis()
            fab.visibility = View.INVISIBLE
            progress.visibility = View.VISIBLE

            Thread {
                var reply = ""
                var error = ""
                try {
                    reply = GeminiApiClient(apiKey).generateReply(messages, contactName)
                } catch (e: Exception) {
                    Log.e(TAG, "API error", e)
                    error = e.message ?: "nieznany błąd"
                }
                mainHandler.post {
                    try {
                        progress.visibility = View.GONE
                        fab.visibility = View.VISIBLE
                        isGenerating = false
                        if (error.isNotEmpty()) {
                            Toast.makeText(this, "Błąd AI: $error", Toast.LENGTH_LONG).show()
                        } else {
                            val filled = accessibility.fillTextInput(reply)
                            if (filled) {
                                Toast.makeText(this, "Gotowe! Odpowiedź wpisana.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "AI odpowiedział ale wpisanie nie zadziałało. Wróć do rozmowy i spróbuj ponownie.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "UI update error", e)
                        progress.visibility = View.GONE
                        fab.visibility = View.VISIBLE
                        isGenerating = false
                        Toast.makeText(this, "Błąd wpisywania: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            Toast.makeText(this, "Błąd nakładki: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (ignored: Exception) {}
            overlayView = null
        }
        isGenerating = false
    }
}
