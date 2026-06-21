package com.whatsappsuggester.api

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val sender: String,
    val text: String,
    val isMe: Boolean
)

class GeminiApiClient(private val apiKey: String) {

    // Gemini 1.5 Flash — BEZPŁATNY: 1500 zapytań/dzień, 1M tokenów/dzień
    // Pobierz klucz (bez karty kredytowej): aistudio.google.com
    private val model = "gemini-1.5-flash"
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    fun generateReply(messages: List<ChatMessage>, contactName: String): String {
        val myMessages = messages.filter { it.isMe }
        val myStyle = myMessages.takeLast(40).joinToString("\n") { "- ${it.text}" }

        val history = buildString {
            append("Historia rozmowy:\n")
            messages.takeLast(80).forEach { msg ->
                val who = if (msg.isMe) "JA" else contactName.toUpperCase()
                append("$who: ${msg.text}\n")
            }
        }

        val prompt = """
Jesteś asystentem pomagającym pisać odpowiedzi w WhatsApp.

Styl pisania użytkownika (NAŚLADUJ dokładnie — ton, długość, emotikony, skróty):
$myStyle

$history

Napisz JEDNĄ naturalną odpowiedź w DOKŁADNIE takim stylu.
Zwróć WYŁĄCZNIE treść wiadomości — bez komentarzy, cudzysłowów.
""".trimIndent()

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 200)
                put("temperature", 0.9)
            })
        }

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        if (code !in 200..299) {
            Log.e("GeminiApi", "Error $code: $response")
            throw Exception("Błąd API $code")
        }

        return JSONObject(response)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }
}
