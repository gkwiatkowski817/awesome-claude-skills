package com.whatsappsuggester.utils

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wa_suggester_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", true)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()
}
