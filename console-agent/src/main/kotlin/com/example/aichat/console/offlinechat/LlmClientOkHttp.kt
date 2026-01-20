package com.example.aichat.console.offlinechat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client for interacting with Ollama API using OkHttp.
 * 
 * This implementation uses OkHttp directly (already in the project) instead of Ktor
 * to avoid dependency resolution issues.
 * 
 * @param baseUrl Base URL of Ollama instance (default: http://localhost:11434)
 * @param model Model name to use (default: qwen2:7b-instruct)
 */
class LlmClientOkHttp(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "qwen2:7b-instruct"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Request data class for Ollama /api/generate endpoint
     */
    data class GenerateRequest(
        val model: String,
        val prompt: String,
        @JsonProperty("stream") val stream: Boolean = false
    )

    /**
     * Response data class for Ollama /api/generate endpoint
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GenerateResponse(
        val model: String,
        val response: String,
        val done: Boolean,
        @JsonProperty("created_at") val createdAt: String? = null
    )

    /**
     * Sends a prompt to the LLM and returns the response.
     * 
     * @param prompt The user's input prompt
     * @return The AI's response text
     * @throws Exception if the API call fails
     */
    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val request = GenerateRequest(
                model = model,
                prompt = prompt,
                stream = false
            )

            val requestBody = objectMapper.writeValueAsString(request)
                .toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val responseBody = response.body?.string()
                ?: throw Exception("Empty response body")

            val generateResponse: GenerateResponse = objectMapper.readValue(responseBody)
            generateResponse.response
        } catch (e: Exception) {
            throw Exception("Failed to communicate with Ollama: ${e.message}", e)
        }
    }

    /**
     * Closes the HTTP client and releases resources.
     * Note: OkHttpClient is thread-safe and reusable, but we shut down
     * the executor service to clean up threads on application exit.
     */
    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
