package com.airi.assistant

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AIClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<Pair<String, String>>()

    fun clearHistory() = history.clear()

    suspend fun chat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        screenshotBytes: ByteArray? = null
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        history.forEach { (role, content) ->
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }

        val userContent: Any = if (screenshotBytes != null) {
            val b64 = Base64.encodeToString(screenshotBytes, Base64.NO_WRAP)
            JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userMessage)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                })
            }
        } else {
            userMessage
        }

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 500)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/Kakitusi44/Airi-android")
            .header("X-Title", "Airi Assistant")
            .post(body)
            .build()

        val reply = client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: throw Exception("Пустой ответ")
            if (!resp.isSuccessful) throw Exception("Ошибка API ${resp.code}")
            JSONObject(respBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }

        history += "user" to userMessage
        history += "assistant" to reply
        if (history.size > 20) { history.removeAt(0); history.removeAt(0) }

        reply
    }
}
