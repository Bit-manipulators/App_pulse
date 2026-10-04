package com.apppulse.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object OllamaClient {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Configurable endpoint with smart default for Android Emulator & local reverse
    var baseUrl: String = "http://10.0.2.2:11434"
    var modelName: String = "qwen2.5-coder:3b"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targets = listOf(
            baseUrl,
            "http://127.0.0.1:11434",
            "http://localhost:11434",
            "http://10.0.2.2:11434"
        ).distinct()

        for (url in targets) {
            try {
                val req = Request.Builder()
                    .url("$url/api/tags")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        baseUrl = url // auto-latch working endpoint
                        val body = response.body?.string() ?: ""
                        return@withContext true to "Connected to Ollama ($url)"
                    }
                }
            } catch (e: Exception) {
                // Try next candidate
            }
        }
        return@withContext false to "Cannot reach Ollama at $baseUrl. Ensure Ollama is running."
    }

    suspend fun generateAnalysis(
        prompt: String,
        systemPrompt: String = "You are an expert mobile performance and security analyst. Provide a factual, clear, and actionable analysis of the application based strictly on the provided real device metrics."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JsonObject().apply {
                addProperty("model", modelName)
                addProperty("prompt", prompt)
                addProperty("system", systemPrompt)
                addProperty("stream", false)
            }

            val requestBody = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    return@withContext Result.failure(Exception("Ollama error ($err)"))
                }

                val resBody = response.body?.string() ?: ""
                val json = gson.fromJson(resBody, JsonObject::class.java)
                val output = json.get("response")?.asString ?: "No response generated."
                return@withContext Result.success(output)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
