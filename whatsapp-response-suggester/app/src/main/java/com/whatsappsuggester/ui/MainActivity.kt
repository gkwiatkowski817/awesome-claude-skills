package com.whatsappsuggester.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.whatsappsuggester.R
import com.whatsappsuggester.service.OverlayService
import com.whatsappsuggester.service.WhatsAppAccessibilityService
import com.whatsappsuggester.utils.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var etApiKey: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        bindViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun bindViews() {
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnOverlay = findViewById(R.id.btn_overlay)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvStatus = findViewById(R.id.tv_status)
        statusDot = findViewById(R.id.status_dot)
        etApiKey = findViewById(R.id.et_api_key)

        if (prefs.apiKey.isNotBlank()) {
            etApiKey.setText(prefs.apiKey)
        }
    }

    private fun setupClickListeners() {
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btn_save_api_key).setOnClickListener {
            val key = etApiKey.text?.toString()?.trim() ?: ""
            if (key.startsWith("sk-ant-") && key.length > 20) {
                prefs.apiKey = key
                startOverlayServiceIfReady()
                Toast.makeText(this, "Klucz API zapisany!", Toast.LENGTH_SHORT).show()
                updatePermissionStatus()
            } else {
                Toast.makeText(
                    this,
                    "Nieprawidłowy klucz API. Powinien zaczynać się od sk-ant-",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updatePermissionStatus() {
        val accessibilityEnabled = WhatsAppAccessibilityService.isRunning()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val apiKeySet = prefs.apiKey.isNotBlank()

        // Accessibility
        if (accessibilityEnabled) {
            tvAccessibilityStatus.text = "Aktywna"
            tvAccessibilityStatus.setTextColor(getColor(R.color.success_green))
            btnAccessibility.text = "Skonfigurowana"
            btnAccessibility.backgroundTintList =
                getColorStateList(R.color.success_green)
        } else {
            tvAccessibilityStatus.text = "Wymagana — kliknij Włącz"
            tvAccessibilityStatus.setTextColor(getColor(R.color.error_red))
            btnAccessibility.text = "Włącz"
            btnAccessibility.backgroundTintList =
                getColorStateList(R.color.green_primary)
        }

        // Overlay
        if (overlayEnabled) {
            tvOverlayStatus.text = "Aktywna"
            tvOverlayStatus.setTextColor(getColor(R.color.success_green))
            btnOverlay.text = "Skonfigurowana"
            btnOverlay.backgroundTintList =
                getColorStateList(R.color.success_green)
        } else {
            tvOverlayStatus.text = "Wymagana — kliknij Włącz"
            tvOverlayStatus.setTextColor(getColor(R.color.error_red))
            btnOverlay.text = "Włącz"
            btnOverlay.backgroundTintList =
                getColorStateList(R.color.green_primary)
        }

        // Overall status
        val allReady = accessibilityEnabled && overlayEnabled && apiKeySet
        if (allReady) {
            tvStatus.text = "Gotowe! Otwórz WhatsApp — zielony przycisk AI pojawi się automatycznie."
            tvStatus.setTextColor(getColor(R.color.success_green))
            startOverlayServiceIfReady()
        } else {
            val missing = buildString {
                if (!accessibilityEnabled) appendLine("• Włącz usługę dostępności")
                if (!overlayEnabled) appendLine("• Przyznaj uprawnienie nakładki")
                if (!apiKeySet) appendLine("• Zapisz klucz API Claude")
            }
            tvStatus.text = "Pozostało:\n$missing"
            tvStatus.setTextColor(getColor(android.R.color.white))
        }
    }

    private fun startOverlayServiceIfReady() {
        if (Settings.canDrawOverlays(this) && prefs.apiKey.isNotBlank()) {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
    }
}
