package com.whatsappsuggester.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.whatsappsuggester.R
import com.whatsappsuggester.service.OverlayService
import com.whatsappsuggester.service.WhatsAppAccessibilityService
import com.whatsappsuggester.utils.Prefs

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etApiKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        btnAccessibility = findViewById(R.id.btn_accessibility) as Button
        btnOverlay = findViewById(R.id.btn_overlay) as Button
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status) as TextView
        tvOverlayStatus = findViewById(R.id.tv_overlay_status) as TextView
        tvStatus = findViewById(R.id.tv_status) as TextView
        etApiKey = findViewById(R.id.et_api_key) as EditText

        if (prefs.apiKey.isNotBlank()) etApiKey.setText(prefs.apiKey)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlay.setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        (findViewById(R.id.btn_save_api_key) as Button).setOnClickListener {
            val key = etApiKey.text?.toString()?.trim() ?: ""
            if (key.length > 10) {
                prefs.apiKey = key
                Toast.makeText(this, "Klucz Gemini zapisany!", Toast.LENGTH_SHORT).show()
                startOverlayIfReady()
                updateStatus()
            } else {
                Toast.makeText(this, "Klucz jest za krótki", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityOk = WhatsAppAccessibilityService.isRunning()
        val overlayOk = Settings.canDrawOverlays(this)
        val apiKeyOk = prefs.apiKey.isNotBlank()

        tvAccessibilityStatus.text = if (accessibilityOk) "Aktywna ✓" else "Wymagana — kliknij Włącz"
        tvAccessibilityStatus.setTextColor(if (accessibilityOk) 0xFF388E3C.toInt() else 0xFFD32F2F.toInt())
        btnAccessibility.text = if (accessibilityOk) "OK" else "Włącz"

        tvOverlayStatus.text = if (overlayOk) "Aktywna ✓" else "Wymagana — kliknij Włącz"
        tvOverlayStatus.setTextColor(if (overlayOk) 0xFF388E3C.toInt() else 0xFFD32F2F.toInt())
        btnOverlay.text = if (overlayOk) "OK" else "Włącz"

        if (accessibilityOk && overlayOk && apiKeyOk) {
            tvStatus.text = "Gotowe! Otwórz WhatsApp — zielony przycisk AI pojawi się automatycznie."
            tvStatus.setTextColor(0xFF25D366.toInt())
            startOverlayIfReady()
        } else {
            tvStatus.text = buildString {
                append("Pozostało do skonfigurowania:\n")
                if (!accessibilityOk) append("• Włącz usługę dostępności\n")
                if (!overlayOk) append("• Przyznaj uprawnienie nakładki\n")
                if (!apiKeyOk) append("• Wpisz klucz API Gemini\n")
            }.trimEnd()
            tvStatus.setTextColor(0xFFCCCCCC.toInt())
        }
    }

    private fun startOverlayIfReady() {
        if (Settings.canDrawOverlays(this) && prefs.apiKey.isNotBlank()) {
            startService(Intent(this, OverlayService::class.java))
        }
    }
}
