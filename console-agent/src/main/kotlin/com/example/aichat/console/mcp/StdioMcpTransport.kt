package com.example.aichat.console.mcp

import com.example.aichat.console.model.McpRequest
import com.example.aichat.console.model.McpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MCP Transport implementation using stdio (stdin/stdout)
 * Used for local MCP servers launched as subprocesses
 */
class StdioMcpTransport(
    private val command: List<String>,
    private val environment: Map<String, String>? = null,
    private val workingDirectory: String? = null
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null
    private var stdoutReader: BufferedReader? = null
    private var stderrReader: BufferedReader? = null
    
    private val isConnectedFlag = AtomicBoolean(false)
    private val pendingRequests = mutableMapOf<String, Channel<McpResponse>>()
    
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    
    /**
     * Connects to the MCP server by launching the process
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            if (isConnectedFlag.get()) {
                println("[MCP]: Already connected")
                return@withContext
            }
            
            println("[MCP]: Starting MCP server: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command)
            
            // Set environment variables if provided
            environment?.let { env ->
                val processEnv = processBuilder.environment()
                env.forEach { (key, value) ->
                    processEnv[key] = value
                }
            }
            
            // Set working directory if provided
            workingDirectory?.let { dir ->
                processBuilder.directory(java.io.File(dir))
            }
            
            processBuilder.redirectErrorStream(false) // Keep stderr separate
            process = processBuilder.start()
            
            // Check if process started successfully
            if (process == null || !process!!.isAlive) {
                throw Exception("MCP server process failed to start")
            }
            
            stdinWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
            stdoutReader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
            stderrReader = BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8))
            
            isConnectedFlag.set(true)
            
            // Start reading responses in background
            startResponseReader()
            startStderrReader()
            
            // Give the MCP server a moment to initialize
            println("[MCP]: Waiting for server to initialize...")
            delay(5000) // Increased to 5 seconds to allow npx to download and start
            
            // Check if process is still alive
            if (!process!!.isAlive) {
                val exitCode = process!!.exitValue()
                throw Exception("MCP server process exited with code $exitCode")
            }
            
            // Try to read any initial output from the server
            println("[MCP]: Checking if server is ready...")
            val testLine = try {
                stdoutReader?.readLine()
            } catch (e: Exception) {
                null
            }
            
            if (testLine != null) {
                println("[MCP]: Server output: $testLine")
            }
            
            println("[MCP]: Connected successfully (PID: ${process!!.pid()})")
        } catch (e: Exception) {
            println("[MCP]: Failed to connect: ${e.message}")
            isConnectedFlag.set(false)
            throw e
        }
    }
    
    /**
     * Disconnects from the MCP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            isConnectedFlag.set(false)
            
            stdinWriter?.close()
            stdoutReader?.close()
            stderrReader?.close()
            process?.destroyForcibly()
            
            pendingRequests.clear()
            
            println("[MCP]: Disconnected")
        } catch (e: Exception) {
            println("[MCP]: Error during disconnect: ${e.message}")
        }
    }
    
    /**
     * Checks if transport is connected
     */
    fun isConnected(): Boolean = isConnectedFlag.get() && process?.isAlive == true
    
    /**
     * Sends a request and returns the response
     */
    suspend fun sendRequest(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) {
            throw IllegalStateException("Not connected to MCP server")
        }
        
        val requestId = request.id ?: UUID.randomUUID().toString()
        val requestWithId = request.copy(id = requestId)
        
        val responseChannel = Channel<McpResponse>(Channel.CONFLATED)
        pendingRequests[requestId] = responseChannel
        
        try {
            // Send request as JSON-RPC over stdio (newline-delimited)
            val jsonRequest = json.encodeToString(McpRequest.serializer(), requestWithId)
            println("[MCP]: Sending request (id=$requestId): ${jsonRequest.take(200)}${if (jsonRequest.length > 200) "..." else ""}")
            
            stdinWriter?.let { writer ->
                writer.write(jsonRequest)
                writer.newLine()
                writer.flush()
                println("[MCP]: Request sent, waiting for response...")
            } ?: throw IllegalStateException("stdin writer not initialized")
            
            // Wait for response with timeout
            val response = try {
                kotlinx.coroutines.withTimeout(60000) { // Increased to 60 seconds
                    responseChannel.receive()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                println("[MCP]: Timeout waiting for response. Pending requests: ${pendingRequests.keys}")
                println("[MCP]: Process alive: ${process?.isAlive}, Connected: ${isConnectedFlag.get()}")
                throw Exception("Timeout waiting for response from MCP server (60s). Request ID: $requestId")
            }
            
            println("[MCP]: Received response for request $requestId")
            pendingRequests.remove(requestId)
            response
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            println("[MCP]: Error sending request: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * Starts reading responses from stdout in background
     */
    private fun startResponseReader() {
        scope.launch {
            stdoutReader?.let { reader ->
                try {
                    println("[MCP]: Response reader started")
                    var lineCount = 0
                    while (isConnectedFlag.get() && process?.isAlive == true) {
                        val line = try {
                            // Use a timeout for readLine to avoid blocking forever
                            withContext(Dispatchers.IO) {
                                reader.readLine()
                            }
                        } catch (e: Exception) {
                            if (isConnectedFlag.get()) {
                                println("[MCP]: Error reading line: ${e.message}")
                            }
                            break
                        }
                        
                        if (line == null) {
                            println("[MCP]: End of stream reached (read $lineCount lines total)")
                            break
                        }
                        
                        lineCount++
                        
                        if (line.isBlank()) {
                            println("[MCP]: Received blank line (line $lineCount)")
                            continue
                        }
                        
                        println("[MCP]: Received line #$lineCount: ${line.take(200)}${if (line.length > 200) "..." else ""}")
                        
                        try {
                            val response = json.decodeFromString<McpResponse>(line)
                            println("[MCP]: Parsed response with id=${response.id}")
                            
                            if (response.id != null) {
                                // This is a response to a request
                                val channel = pendingRequests[response.id]
                                if (channel != null) {
                                    val sent = channel.trySend(response)
                                    if (sent.isSuccess) {
                                        println("[MCP]: Response sent to channel for request ${response.id}")
                                    } else {
                                        println("[MCP]: Failed to send response to channel: ${sent.exceptionOrNull()?.message}")
                                    }
                                } else {
                                    println("[MCP]: WARNING: No pending request found for id=${response.id}. Available: ${pendingRequests.keys}")
                                }
                            } else {
                                println("[MCP]: Received notification (no id)")
                            }
                        } catch (e: Exception) {
                            println("[MCP]: Failed to parse as JSON-RPC response: ${e.message}")
                            println("[MCP]: Line was: $line")
                            // Try to see if it's a different format
                            if (line.startsWith("{") || line.startsWith("[")) {
                                println("[MCP]: Looks like JSON but not valid MCP response format")
                            }
                        }
                    }
                    println("[MCP]: Response reader stopped (read $lineCount lines total)")
                } catch (e: Exception) {
                    if (isConnectedFlag.get()) {
                        println("[MCP]: Error in response reader: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } ?: println("[MCP]: WARNING: stdoutReader is null, cannot start response reader")
        }
    }
    
    /**
     * Starts reading stderr for debugging
     */
    private fun startStderrReader() {
        scope.launch {
            stderrReader?.let { reader ->
                try {
                    while (isConnectedFlag.get() && process?.isAlive == true) {
                        val line = reader.readLine() ?: break
                        // Print stderr for debugging
                        println("[MCP stderr]: $line")
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}
