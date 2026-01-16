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
    
    /**
     * Creates an MCP client by starting the repo-mcp server process
     * The repo server provides git and filesystem tools
     */
    suspend fun createRepoMcpClient(): Result<Pair<McpClientWrapper, Process>> = withContext(Dispatchers.IO) {
        try {
            // Find the JAR or use gradle run
            // For now, we'll use a simple approach: run the main class via gradle
            // In production, this would be a packaged JAR
            val javaHome = System.getProperty("java.home")
            val javaExecutable = "$javaHome/bin/java"
            
            // Get the classpath from the current process
            val classpath = System.getProperty("java.class.path")
            
            val command = listOf(
                javaExecutable,
                "-cp", classpath,
                "com.example.aichat.console.mcp.repo.RepoMcpServerMainKt"
            )
            
            println("[MCP]: Starting repo-mcp server...")
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(false)
            
            val process = processBuilder.start()
            
            // Check if process started successfully
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                return@withContext Result.failure(
                    Exception("Repo MCP server process failed to start with exit code $exitCode")
                )
            }
            
            // Give the process a moment to initialize
            delay(2000)
            
            // Check if process is still alive
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                return@withContext Result.failure(
                    Exception("Repo MCP server process exited with code $exitCode")
                )
            }
            
            println("[MCP]: Repo MCP server started successfully (PID: ${process.pid()})")
            
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
    
    /**
     * Creates an MCP client by starting the tasks-mcp server process
     * The tasks server provides team task management tools
     */
    suspend fun createTasksMcpClient(): Result<Pair<McpClientWrapper, Process>> = withContext(Dispatchers.IO) {
        try {
            val javaHome = System.getProperty("java.home")
            val javaExecutable = "$javaHome/bin/java"
            
            // Get the classpath from the current process
            val classpath = System.getProperty("java.class.path")
            
            val command = listOf(
                javaExecutable,
                "-cp", classpath,
                "com.example.aichat.console.mcp.tasks.TasksMcpServerMainKt"
            )
            
            println("[MCP]: Starting tasks-mcp server...")
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(false)
            
            val process = processBuilder.start()
            
            // Check if process started successfully
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                return@withContext Result.failure(
                    Exception("Tasks MCP server process failed to start with exit code $exitCode")
                )
            }
            
            // Give the process a moment to initialize
            delay(2000)
            
            // Check if process is still alive
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                return@withContext Result.failure(
                    Exception("Tasks MCP server process exited with code $exitCode")
                )
            }
            
            println("[MCP]: Tasks MCP server started successfully (PID: ${process.pid()})")
            
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
