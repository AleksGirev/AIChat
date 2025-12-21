package com.example.aichat.console.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Factory for creating MCP client with process management
 */
object McpClientFactory {
    
    /**
     * Creates an MCP client by starting the mobile-mcp server process
     */
    suspend fun createMobileMcpClient(): Result<Pair<McpClientWrapper, Process>> = withContext(Dispatchers.IO) {
        try {
            val command = listOf(
                "npx", "-y", "@mobilenext/mobile-mcp@latest"
            )
            
            println("[MCP]: Starting mobile-mcp server: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(false) // Keep stderr separate
            
            val process = processBuilder.start()
            
            // Check if process started successfully
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                return@withContext Result.failure(
                    Exception("MCP server process failed to start with exit code $exitCode")
                )
            }
            
            // Give the process a moment to initialize
            delay(3000)
            
            // Check if process is still alive
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                return@withContext Result.failure(
                    Exception("MCP server process exited with code $exitCode")
                )
            }
            
            println("[MCP]: Process started successfully (PID: ${process.pid()})")
            
            // Create client wrapper with process streams
            val client = McpClientWrapper(
                inputStream = process.inputStream,
                outputStream = process.outputStream
            )
            
            // Connect to the server
            client.connect()
            
            Result.success(Pair(client, process))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
