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
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

/**
 * MCP Transport implementation using WebSocket
 * Used for remote MCP servers with WebSocket support
 * Note: This is a simplified implementation. For production, consider using
 * a WebSocket library like okhttp3 WebSocket or Ktor WebSocket client
 */
class WebSocketMcpTransport(
    private val wsUrl: String,
    private val apiKey: String? = null,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : McpTransport {
    
    private var webSocket: okhttp3.WebSocket? = null
    private val isConnectedFlag = AtomicBoolean(false)
    private val pendingRequests = mutableMapOf<String, Channel<McpResponse>>()
    private val notificationChannel = Channel<McpRequest>(Channel.UNLIMITED)
    
    private val tag = "WebSocketMcpTransport"
    
    private val webSocketListener = object : okhttp3.WebSocketListener() {
        override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
            Log.d(tag, "WebSocket connected")
            isConnectedFlag.set(true)
        }
        
        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
            processMessage(text)
        }
        
        override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
            processMessage(bytes.utf8())
        }
        
        override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            Log.d(tag, "WebSocket closing: $code - $reason")
            isConnectedFlag.set(false)
        }
        
        override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            Log.d(tag, "WebSocket closed: $code - $reason")
            isConnectedFlag.set(false)
        }
        
        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
            Log.e(tag, "WebSocket failure", t)
            isConnectedFlag.set(false)
        }
    }
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            if (isConnectedFlag.get()) {
                Log.w(tag, "Already connected")
                return@withContext
            }
            
            val requestBuilder = Request.Builder().url(wsUrl)
            
            if (apiKey != null) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            val request = requestBuilder.build()
            webSocket = httpClient.newWebSocket(request, webSocketListener)
            
            Log.d(tag, "WebSocket connection initiated to $wsUrl")
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect WebSocket", e)
            isConnectedFlag.set(false)
            throw e
        }
    }
    
    override suspend fun disconnect() {
        try {
            isConnectedFlag.set(false)
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
            
            pendingRequests.clear()
            notificationChannel.close()
            
            Log.d(tag, "WebSocket disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error during WebSocket disconnect", e)
        }
    }
    
    override fun isConnected(): Boolean = isConnectedFlag.get()
    
    override suspend fun sendRequest(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) {
            throw IllegalStateException("Not connected to MCP server")
        }
        
        val requestId = request.id ?: UUID.randomUUID().toString()
        val requestWithId = request.copy(id = requestId)
        
        val responseChannel = Channel<McpResponse>(Channel.CONFLATED)
        pendingRequests[requestId] = responseChannel
        
        try {
            val jsonRequest = gson.toJson(requestWithId)
            Log.d(tag, "Sending WebSocket request: $jsonRequest")
            
            val sent = webSocket?.send(jsonRequest) ?: false
            if (!sent) {
                throw IllegalStateException("Failed to send WebSocket message")
            }
            
            // Wait for response with timeout
            val response = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(30000) {
                    responseChannel.receive()
                }
            }
            
            pendingRequests.remove(requestId)
            response
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Log.e(tag, "Error sending WebSocket request", e)
            throw e
        }
    }
    
    override fun sendRequestStream(request: McpRequest): Flow<McpResponse> = flow {
        // For streaming, emit the initial response and then continue receiving
        val initialResponse = sendRequest(request)
        emit(initialResponse)
        
        // Additional streaming responses would be handled via notifications
        // This is a simplified implementation
    }
    
    override fun receiveNotifications(): Flow<McpRequest> = notificationChannel.receiveAsFlow()
    
    /**
     * Processes incoming WebSocket messages
     */
    private fun processMessage(text: String) {
        try {
            val response = gson.fromJson(text, McpResponse::class.java)
            
            if (response.id != null) {
                // This is a response to a request
                pendingRequests[response.id]?.trySend(response)
            } else {
                // This is a notification
                val notification = gson.fromJson(text, McpRequest::class.java)
                notificationChannel.trySend(notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse WebSocket message: $text", e)
        }
    }
}

