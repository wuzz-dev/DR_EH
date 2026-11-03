package com.dreh.app.ai

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** User-supplied API credentials + endpoint. Stored in SharedPreferences (no backend in the prototype).
 *  No live API key is shipped in source; without a key, chat uses its local companion reply. */
data class AiConfig(
    val baseUrl: String = GROQ_BASE_URL,
    val apiKey: String = GROQ_DEFAULT_API_KEY,
    val model: String = GROQ_DEFAULT_MODEL,
    val temperature: Float = 0.4f
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    val chatUrl: String get() = baseUrl.trimEnd('/') + "/chat/completions"
}

/** Groq defaults: OpenAI-compatible endpoint and model. Users supply their own API key in settings. */
const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
const val GROQ_DEFAULT_API_KEY = ""

/** Default chat model on Groq's 2026 roster (llama-3.3-70b-versatile was retired). */
const val GROQ_DEFAULT_MODEL = "openai/gpt-oss-20b"

class AiStore(context: Context) {
    private val prefs = context.getSharedPreferences("dr_eh_ai", Context.MODE_PRIVATE)

    fun load(): AiConfig = AiConfig(
        baseUrl = prefs.getString("baseUrl", null)?.takeIf { it.isNotBlank() } ?: GROQ_BASE_URL,
        apiKey = prefs.getString("apiKey", null)?.takeIf { it.isNotBlank() } ?: GROQ_DEFAULT_API_KEY,
        model = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: GROQ_DEFAULT_MODEL,
        temperature = prefs.getFloat("temperature", 0.4f)
    )

    fun save(c: AiConfig) {
        prefs.edit()
            .putString("baseUrl", c.baseUrl)
            .putString("apiKey", c.apiKey)
            .putString("model", c.model)
            .putFloat("temperature", c.temperature)
            .apply()
    }
}

/** One message in a chat log. role is "user" or "assistant". */
data class ChatMsg(val role: String, val content: String)

/** Thin OpenAI-compatible chat client (works with OpenAI, Groq, OpenRouter, Ollama, LM Studio, ...). */
object AiClient {
    suspend fun complete(config: AiConfig, systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            require(config.isConfigured) { "No API configured" }

            val body = JSONObject().apply {
                put("model", config.model)
                put("temperature", config.temperature.toDouble())
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                })
            }

            val conn = (URL(config.chatUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }

            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(300)}")

                val root = JSONObject(text)
                root.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty().ifBlank {
                        throw IllegalStateException("Empty response from API")
                    }
            } finally {
                conn.disconnect()
            }
        }

    /** Companion chat: the system prompt plus the running conversation (roles: user / assistant). */
    suspend fun chat(config: AiConfig, systemPrompt: String, history: List<ChatMsg>): String =
        withContext(Dispatchers.IO) {
            require(config.isConfigured) { "No API configured" }

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                history.takeLast(16).forEach { msg ->
                    put(JSONObject().put("role", msg.role).put("content", msg.content))
                }
            }

            val body = JSONObject().apply {
                put("model", config.model)
                put("temperature", config.temperature.toDouble())
                put("stream", false)
                put("messages", messages)
            }

            val conn = (URL(config.chatUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }

            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(300)}")

                val root = JSONObject(text)
                root.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty().ifBlank {
                        throw IllegalStateException("Empty response from API")
                    }
            } finally {
                conn.disconnect()
            }
        }
}
