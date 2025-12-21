package com.example.aichat.console

import com.example.aichat.console.agent.AgentOrchestrator
import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientFactory
import com.example.aichat.console.mcp.McpClientWrapper
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        println("=== Mobile Device Control Agent ===")
        println()
        
        // Get YandexGPT credentials from environment
        val iamToken = ConsoleConfig.YANDEX_IAM_TOKEN
        val folderId = ConsoleConfig.YANDEX_FOLDER_ID
        
        if (iamToken.isBlank()) {
            println("ERROR: YANDEX_IAM_TOKEN environment variable is not set")
            println("Please set it before running:")
            println("  export YANDEX_IAM_TOKEN=your-iam-token")
            println("Get your IAM token from: https://cloud.yandex.ru/docs/iam/operations/iam-token/create")
            return@runBlocking
        }
        
        if (folderId.isBlank()) {
            println("ERROR: YANDEX_FOLDER_ID environment variable is not set")
            println("Please set it before running:")
            println("  export YANDEX_FOLDER_ID=your-folder-id")
            println("Get your folder ID from: https://cloud.yandex.ru/docs/resource-manager/operations/folder/get-id")
            return@runBlocking
        }
        
        // YandexGPT configuration
        val yandexBaseUrl = "https://llm.api.cloud.yandex.net/v1"
        val yandexModel = "gpt://$folderId/yandexgpt/latest"
        
        val llmClient = OpenAiClient(
            apiKey = iamToken,
            baseUrl = yandexBaseUrl,
            model = yandexModel,
            folderId = folderId
        )
        
        // Initialize
        var mcpProcess: Process? = null
        var orchestrator: AgentOrchestrator? = null
        try {
            println("[Agent]: Инициализация...")
            
            // Create MCP client using official SDK
            println("[Agent]: Connecting to mobile-mcp server...")
            println("[Agent]: NOTE: Make sure a device is connected (Android/iOS) for mobile-mcp to work")
            
            val mcpClient: McpClientWrapper? = try {
                val mcpResult = McpClientFactory.createMobileMcpClient()
                if (mcpResult.isSuccess) {
                    val pair = mcpResult.getOrThrow()
                    mcpProcess = pair.second // Keep process reference for cleanup
                    pair.first
                } else {
                    val error = mcpResult.exceptionOrNull()
                    println("WARNING: Failed to start MCP server: ${error?.message}")
                    println("[Agent]: Continuing without MCP tools...")
                    null
                }
            } catch (e: Exception) {
                println("WARNING: Failed to start MCP server: ${e.message}")
                println("[Agent]: Continuing without MCP tools...")
                null
            }
            
            // Pass mcpClient to orchestrator - it will use it for tool listing and execution
            orchestrator = AgentOrchestrator(llmClient, mcpClient)
            
            // Initialize orchestrator (fetches tools)
            val orchestratorInit = orchestrator?.initialize() ?: Result.failure(Exception("Orchestrator not created"))
            if (orchestratorInit.isFailure) {
                val error = orchestratorInit.exceptionOrNull()
                println("ERROR: Failed to initialize orchestrator: ${error?.message}")
                println()
                println("Troubleshooting:")
                println("1. Check if a device is connected:")
                println("   - Android: Run 'adb devices'")
                println("   - iOS: Check Xcode device manager")
                println("2. Verify Node.js is installed: 'node --version'")
                println("3. Test MCP server manually: 'npx -y @mobilenext/mobile-mcp@latest'")
                return@runBlocking
            }
            
            println("[Agent]: Готов к работе!")
            println()
            
            // Interactive loop
            var running = true
            while (running) {
                try {
                    print("> ")
                    val input = readLine()?.trim()
                    
                    when {
                        input.isNullOrBlank() -> continue
                        input.equals("exit", ignoreCase = true) -> {
                            running = false
                            println("[Agent]: Завершение работы...")
                        }
                        else -> {
                            // Process command
                            val result = orchestrator?.processCommand(input) ?: Result.failure(Exception("Orchestrator not available"))
                            
                            if (result.isSuccess) {
                                val response = result.getOrThrow()
                                println("[Agent]: $response")
                            } else {
                                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                println("[Agent]: Ошибка: $error")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[Agent]: Ошибка обработки команды: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("FATAL ERROR: ${e.message}")
            e.printStackTrace()
        } finally {
            // Cleanup
            try {
                orchestrator?.shutdown()
                mcpProcess?.destroyForcibly() // Clean up MCP server process
            } catch (e: Exception) {
                println("[Agent]: Error during shutdown: ${e.message}")
            }
        }
    }
    
    println("До свидания!")
}
