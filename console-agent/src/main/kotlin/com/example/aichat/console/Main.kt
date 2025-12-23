package com.example.aichat.console

import com.example.aichat.console.agent.AgentOrchestrator
import com.example.aichat.console.agent.RAGAgentOrchestrator
import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientFactory
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        // Check if RAG mode is enabled (default: true)
        val useRAG = !args.contains("--no-rag")
        
        if (true) {
            println("=== RAG-Enhanced AI Agent ===")
        } else {
            println("=== Mobile Device Control Agent ===")
        }
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
        var ragOrchestrator: RAGAgentOrchestrator? = null
        var ragPipeline: RAGPipeline? = null
        
        try {
            println("[Agent]: Инициализация...")
            
            // Initialize RAG pipeline if RAG mode is enabled
            if (useRAG) {
                val dbPath = ConsoleConfig.getRAGDatabasePath()
                val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
                val ollamaModel = System.getenv("OLLAMA_MODEL") ?: "nomic-embed-text"
                
                println("[RAG]: Initializing RAG pipeline...")
                println("[RAG]:   Database: $dbPath")
                println("[RAG]:   Ollama URL: $ollamaBaseUrl")
                println("[RAG]:   Embedding Model: $ollamaModel")
                
                ragPipeline = RAGPipeline.create(
                    dbPath = dbPath,
                    ollamaBaseUrl = ollamaBaseUrl,
                    ollamaModel = ollamaModel
                )
            }
            
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
            
            // Create appropriate orchestrator
            if (useRAG && ragPipeline != null) {
                ragOrchestrator = RAGAgentOrchestrator(llmClient, ragPipeline, mcpClient)
                val initResult = ragOrchestrator.initialize()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    println("ERROR: Failed to initialize RAG orchestrator: ${error?.message}")
                    return@runBlocking
                }
            } else {
                orchestrator = AgentOrchestrator(llmClient, mcpClient)
                val orchestratorInit = orchestrator.initialize()
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
            }
            
            println("[Agent]: Готов к работе!")
            if (useRAG && ragOrchestrator != null) {
                println("[RAG]: RAG mode enabled - questions will be answered using indexed documents")
                println("[RAG]: Use '/rag off' to disable RAG, '/rag on' to enable it")
            }
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
                        input.startsWith("/rag", ignoreCase = true) -> {
                            // Handle RAG toggle commands
                            if (ragOrchestrator == null) {
                                println("[RAG]: RAG is not available (started without RAG mode)")
                            } else {
                                when {
                                    input.equals("/rag on", ignoreCase = true) || 
                                    input.equals("/rag enable", ignoreCase = true) -> {
                                        ragOrchestrator.enableRAG()
                                    }
                                    input.equals("/rag off", ignoreCase = true) || 
                                    input.equals("/rag disable", ignoreCase = true) -> {
                                        ragOrchestrator.disableRAG()
                                    }
                                    input.equals("/rag status", ignoreCase = true) || 
                                    input.equals("/rag", ignoreCase = true) -> {
                                        val status = if (ragOrchestrator.isRAGEnabled()) "enabled" else "disabled"
                                        println("[RAG]: Status: $status")
                                        val stats = ragPipeline?.getStats()
                                        if (stats != null) {
                                            println("[RAG]: Index contains ${stats.totalChunks} chunks from ${stats.uniqueSources} source(s)")
                                        }
                                    }
                                    else -> {
                                        println("[RAG]: Unknown command. Use:")
                                        println("  /rag on    - Enable RAG")
                                        println("  /rag off   - Disable RAG")
                                        println("  /rag status - Show RAG status")
                                    }
                                }
                            }
                        }
                        else -> {
                            // Process command
                            val result = if (useRAG && ragOrchestrator != null) {
                                ragOrchestrator.processCommand(input)
                            } else {
                                orchestrator?.processCommand(input) ?: Result.failure(Exception("Orchestrator not available"))
                            }
                            
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
                ragOrchestrator?.shutdown()
                orchestrator?.shutdown()
                ragPipeline?.close()
                mcpProcess?.destroyForcibly() // Clean up MCP server process
            } catch (e: Exception) {
                println("[Agent]: Error during shutdown: ${e.message}")
            }
        }
    }
    
    println("До свидания!")
}
