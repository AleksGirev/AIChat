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
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MCP Transport implementation using stdio (stdin/stdout)
 * Used for local MCP servers launched as subprocesses
 * 
 * @param command The command and arguments to launch the MCP server
 * @param gson Gson instance for JSON serialization
 * @param environment Optional environment variables to set for the process
 * @param workingDirectory Optional working directory for the process
 */
class StdioMcpTransport(
    private val command: List<String>,
    private val gson: Gson,
    private val environment: Map<String, String>? = null,
    private val workingDirectory: String? = null
) : McpTransport {
    
    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var stdoutReader: BufferedReader? = null
    
    private val isConnectedFlag = AtomicBoolean(false)
    private val pendingRequests = mutableMapOf<String, Channel<McpResponse>>()
    private val notificationChannel = Channel<McpRequest>(Channel.UNLIMITED)
    
    private val tag = "StdioMcpTransport"
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            if (isConnectedFlag.get()) {
                Log.w(tag, "Already connected")
                return@withContext
            }
            
            Log.d(tag, "Starting MCP server: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command)
            
            // Set environment variables if provided
            environment?.let { env ->
                val processEnv = processBuilder.environment()
                env.forEach { (key, value) ->
                    processEnv[key] = value
                    Log.d(tag, "Setting env: $key=${if (key.contains("KEY", ignoreCase = true)) "***" else value}")
                }
            }
            
            // Set working directory if provided
            workingDirectory?.let { dir ->
                processBuilder.directory(java.io.File(dir))
                Log.d(tag, "Working directory: $dir")
            }
            
            processBuilder.redirectErrorStream(false) // Keep stderr separate for debugging
            process = processBuilder.start()
            
            stdinWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            stdoutReader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            isConnectedFlag.set(true)
            
            // Start reading responses in background
            startResponseReader()
            
            Log.d(tag, "MCP server connected via stdio")
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect to MCP server", e)
            isConnectedFlag.set(false)
            throw e
        }
    }
    
    override suspend fun disconnect() {
        try {
            isConnectedFlag.set(false)
            
            stdinWriter?.close()
            stdoutReader?.close()
            process?.destroy()
            
            pendingRequests.clear()
            notificationChannel.close()
            
            Log.d(tag, "Disconnected from MCP server")
        } catch (e: Exception) {
            Log.e(tag, "Error during disconnect", e)
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
            // Send request
            val jsonRequest = gson.toJson(requestWithId)
            Log.d(tag, "Sending request: $jsonRequest")
            
            stdinWriter?.let { writer ->
                writer.write(jsonRequest)
                writer.newLine()
                writer.flush()
            } ?: throw IllegalStateException("stdin writer not initialized")
            
            // Read response from stdout
            val response = readResponse(requestId)
            pendingRequests.remove(requestId)
            
            response
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Log.e(tag, "Error sending request", e)
            throw e
        }
    }
    
    override fun sendRequestStream(request: McpRequest): Flow<McpResponse> = flow {
        // For stdio transport, streaming is not directly supported
        // We'll return single response as a flow
        emit(sendRequest(request))
    }
    
    override fun receiveNotifications(): Flow<McpRequest> = notificationChannel.receiveAsFlow()
    
    /**
     * Reads responses from stdout in background
     */
    private fun startResponseReader() {
        // Start reading in background coroutine
        // Note: In production, this should be launched in a coroutine scope
        // For now, responses are read synchronously in sendRequest
    }
    
    /**
     * Processes a line received from stdout
     */
    private suspend fun processResponseLine(line: String) {
        try {
            if (line.isBlank()) return
            
            val response = gson.fromJson(line, McpResponse::class.java)
            
            if (response.id != null) {
                // This is a response to a request
                pendingRequests[response.id]?.trySend(response)
            } else {
                // This is a notification
                val notification = gson.fromJson(line, McpRequest::class.java)
                notificationChannel.trySend(notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse response line: $line", e)
        }
    }
    
    /**
     * Reads response from stdout (synchronous for stdio)
     */
    private suspend fun readResponse(requestId: String): McpResponse = withContext(Dispatchers.IO) {
        val responseChannel = pendingRequests[requestId]
            ?: throw Exception("Response channel not found for request $requestId")
        
        stdoutReader?.let { reader ->
            // Read line from stdout
            val line = reader.readLine() ?: throw Exception("End of stream")
            
            // Parse response
            val response = try {
                gson.fromJson(line, McpResponse::class.java)
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse response line: $line", e)
                throw Exception("Failed to parse response: ${e.message}")
            }
            
            // Send response to channel
            responseChannel.send(response)
            
            // Wait for response from channel (it should be immediately available)
            kotlinx.coroutines.withTimeout(30000) {
                responseChannel.receive()
            }
        } ?: throw IllegalStateException("stdout reader not initialized")
    }
}

