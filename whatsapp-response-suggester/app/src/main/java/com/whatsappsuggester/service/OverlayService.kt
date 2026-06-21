package com.whatsappsuggester.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.whatsappsuggester.R
import com.whatsappsuggester.api.ClaudeApiClient
import com.whatsappsuggester.utils.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.whatsappsuggester.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.whatsappsuggester.HIDE_OVERLAY"
        private const val CHANNEL_ID = "wa_suggester_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val prefs by lazy { Prefs(this) }
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
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
        if (!Settings.canDrawOverlays(this)) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 20
        params.y = 120

        val fab = view.findViewById<FloatingActionButton>(R.id.fab_suggest)
        val progress = view.findViewById<ProgressBar>(R.id.progress_loading)

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
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                    }
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
            fab.isEnabled = false
            fab.visibility = View.INVISIBLE
            progress.visibility = View.VISIBLE

            scope.launch {
                generateAndFill(
                    onDone = {
                        progress.visibility = View.GONE
                        fab.visibility = View.VISIBLE
                        fab.isEnabled = true
                    }
                )
            }
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private suspend fun generateAndFill(onDone: () -> Unit) {
        val accessibilityService = WhatsAppAccessibilityService.instance
        if (accessibilityService == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@OverlayService, "Włącz usługę dostępności!", Toast.LENGTH_LONG).show()
                onDone()
            }
            return
        }

        val (messages, contactName) = withContext(Dispatchers.Default) {
            accessibilityService.collectMessages()
        }

        if (messages.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@OverlayService, "Nie znaleziono wiadomości.", Toast.LENGTH_SHORT).show()
                onDone()
            }
            return
        }

        val client = ClaudeApiClient(prefs.apiKey)
        val result = client.generateReply(messages, contactName)

        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { reply ->
                    accessibilityService.fillTextInput(reply)
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@OverlayService,
                        "${getString(R.string.error_api)}${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
            onDone()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
