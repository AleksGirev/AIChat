package com.example.aichat.data.mcp

import android.util.Log
import com.example.aichat.data.mcp.model.McpRequest
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.UUID

/**
 * Diagnostic utility for testing MCP server connectivity
 */
object McpDiagnostics {
    
    private const val TAG = "McpDiagnostics"
    private val jsonMediaType = "application/json".toMediaType()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    /**
     * Tests basic connectivity to MCP server
     */
    suspend fun testConnection(
        url: String,
        apiKey: String? = null,
        httpClient: OkHttpClient
    ): TestResult {
        return try {
            // Create a simple initialize request
            val request = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to mapOf<String, Any>(),
                    "clientInfo" to mapOf(
                        "name" to "AIChat-Diagnostics",
                        "version" to "1.0.0"
                    )
                )
            )
            
            val jsonRequest = gson.toJson(request)
            Log.d(TAG, "Testing connection to: $url")
            Log.d(TAG, "Request: $jsonRequest")
            
            val requestBody = jsonRequest.toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream") // Server requires both formats
                .addHeader("Accept-Encoding", "identity")
                .addHeader("User-Agent", "AIChat-Android/1.0")
            
            if (apiKey != null && apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            val httpRequest = requestBuilder.build()
            
            httpClient.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response headers: ${response.headers}")
                Log.d(TAG, "Response body: $responseBody")
                
                TestResult(
                    success = response.isSuccessful,
                    statusCode = response.code,
                    responseBody = responseBody,
                    errorMessage = if (!response.isSuccessful) {
                        "HTTP ${response.code}: ${response.message}"
                    } else null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            TestResult(
                success = false,
                statusCode = -1,
                responseBody = null,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Result of connection test
     */
    data class TestResult(
        val success: Boolean,
        val statusCode: Int,
        val responseBody: String?,
        val errorMessage: String?
    )
}

