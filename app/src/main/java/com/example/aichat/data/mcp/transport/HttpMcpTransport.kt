package com.example.aichat.data.mcp.transport

import android.util.Log
import com.example.aichat.data.mcp.model.McpRequest
import com.example.aichat.data.mcp.model.McpResponse
import com.example.aichat.data.mcp.model.McpTransport
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MCP Transport implementation using HTTP POST requests
 * Used for remote MCP servers accessible via HTTP endpoint
 */
class HttpMcpTransport(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : McpTransport {
    
    private val isConnectedFlag = AtomicBoolean(false)
    private val notificationChannel = Channel<McpRequest>(Channel.UNLIMITED)
    
    private val tag = "HttpMcpTransport"
    private val jsonMediaType = "application/json".toMediaType()
    
    override suspend fun connect() {
        isConnectedFlag.set(true)
        Log.d(tag, "HTTP transport connected to $baseUrl")
    }
    
    override suspend fun disconnect() {
        isConnectedFlag.set(false)
        notificationChannel.close()
        Log.d(tag, "HTTP transport disconnected")
    }
    
    override fun isConnected(): Boolean = isConnectedFlag.get()
    
    override suspend fun sendRequest(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) {
            throw IllegalStateException("Not connected to MCP server")
        }
        
        val requestId = request.id ?: UUID.randomUUID().toString()
        val requestWithId = request.copy(id = requestId)
        
        try {
            val jsonRequest = gson.toJson(requestWithId)
            Log.d(tag, "Sending HTTP request to $baseUrl")
            Log.d(tag, "Request body: $jsonRequest")
            
            val requestBody = jsonRequest.toRequestBody(jsonMediaType)
            val httpRequestBuilder = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/event-stream") // Server requires both formats
                .addHeader("Accept-Encoding", "identity") // Disable compression to avoid issues
                .addHeader("User-Agent", "AIChat-Android/1.0")
            
            // Add API key if provided
            if (apiKey != null && apiKey.isNotBlank()) {
                httpRequestBuilder.addHeader("Authorization", "Bearer $apiKey")
                Log.d(tag, "Using API key authentication")
            } else {
                Log.d(tag, "No API key provided, using public endpoint")
            }
            
            val httpRequest = httpRequestBuilder.build()
            
            // Log request headers for debugging
            Log.d(tag, "Request headers: ${httpRequest.headers}")
            
            httpClient.newCall(httpRequest).execute().use { response ->
                // Log response headers
                Log.d(tag, "Response code: ${response.code}")
                Log.d(tag, "Response headers: ${response.headers}")
                
                if (!response.isSuccessful) {
                    val errorBody = try {
                        response.body?.string() ?: response.message
                    } catch (e: Exception) {
                        response.message
                    }
                    
                    Log.e(tag, "HTTP error ${response.code}: $errorBody")
                    Log.e(tag, "Request URL: $baseUrl")
                    Log.e(tag, "Request body: $jsonRequest")
                    
                    // Provide more helpful error message
                    val errorMessage = when (response.code) {
                        406 -> "Server returned 406 Not Acceptable. The MCP server may require a different format or endpoint. " +
                                "Check if the server uses SSE (Server-Sent Events) instead of POST requests. " +
                                "Error details: $errorBody"
                        401 -> "Unauthorized. Check your API key. Error: $errorBody"
                        404 -> "Endpoint not found. Check the MCP server URL: $baseUrl. Error: $errorBody"
                        else -> "HTTP error: ${response.code} - $errorBody"
                    }
                    
                    throw Exception(errorMessage)
                }
                
                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response body")
                
                Log.d(tag, "Received HTTP response: $responseBody")
                
                val mcpResponse = try {
                    gson.fromJson(responseBody, McpResponse::class.java)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse response as JSON: $responseBody", e)
                    throw Exception("Invalid JSON response from server: ${e.message}")
                }
                
                if (mcpResponse.error != null) {
                    Log.e(tag, "MCP error: ${mcpResponse.error.message} (code: ${mcpResponse.error.code})")
                    throw Exception("MCP error: ${mcpResponse.error.message} (code: ${mcpResponse.error.code})")
                }
                
                mcpResponse
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending HTTP request", e)
            throw e
        }
    }
    
    override fun sendRequestStream(request: McpRequest): Flow<McpResponse> = flow {
        // HTTP transport doesn't support streaming by default
        // For SSE streaming, use WebSocketMcpTransport or implement SSE client
        emit(sendRequest(request))
    }
    
    override fun receiveNotifications(): Flow<McpRequest> = notificationChannel.receiveAsFlow()
}

