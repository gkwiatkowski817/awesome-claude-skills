package com.whatsappsuggester.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ChatMessage(
    val sender: String,
    val text: String,
    val isMe: Boolean
)

class ClaudeApiClient(private val apiKey: String) {

    private val endpoint = "https://api.anthropic.com/v1/messages"
    private val model = "claude-haiku-4-5-20251001"

    suspend fun generateReply(
        messages: List<ChatMessage>,
        contactName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val yesterday = LocalDate.now().minusDays(1)
            val cutoffLabel = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val myMessages = messages.filter { it.isMe }
            val theirMessages = messages.filter { !it.isMe }
            val lastTheirMessage = theirMessages.lastOrNull()?.text ?: ""

            val historyText = buildString {
                append("Historia rozmowy (do początku dnia $cutoffLabel):\n\n")
                messages.take(120).forEach { msg ->
                    val who = if (msg.isMe) "JA" else contactName.uppercase()
                    appendLine("$who: ${msg.text}")
                }
            }

            val myStyleExamples = myMessages.takeLast(40)
                .joinToString("\n") { "- ${it.text}" }

            val systemPrompt = """
Jesteś asystentem, który pomaga pisać odpowiedzi w WhatsApp.

Twoje zadanie: Na podstawie historii rozmowy i stylu pisania użytkownika,
zaproponuj JEDNĄ naturalną odpowiedź na ostatnią wiadomość od $contactName.

STYL UŻYTKOWNIKA (jego ostatnie wiadomości):
$myStyleExamples

ZASADY:
- Pisz dokładnie tak jak użytkownik: podobna długość, podobny ton, podobne wyrażenia
- Jeśli użytkownik używa skrótów, emotikonów, gwary — rób to samo
- NIE bądź zbyt formalny jeśli rozmowa jest nieformalna
- Odpowiedź ma być naturalna, jakby użytkownik sam ją napisał
- Zwróć TYLKO treść wiadomości — bez żadnych wyjaśnień, cudzysłowów ani komentarzy
- Odpowiedź w tym samym języku co rozmowa
""".trimIndent()

            val userPrompt = """
$historyText

Ostatnia wiadomość od $contactName: "$lastTheirMessage"

Napisz odpowiedź w stylu użytkownika (JA):
""".trimIndent()

            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 300)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }

            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode !in 200..299) {
                Log.e("ClaudeApi", "Error $responseCode: $response")
                return@withContext Result.failure(Exception("API Error $responseCode"))
            }

            val json = JSONObject(response)
            val text = json
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Result.success(text)
        } catch (e: Exception) {
            Log.e("ClaudeApi", "Request failed", e)
            Result.failure(e)
        }
    }
}
