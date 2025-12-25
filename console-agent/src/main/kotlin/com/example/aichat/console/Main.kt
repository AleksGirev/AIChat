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
                            // Handle RAG commands
                            if (ragOrchestrator == null) {
                                println("[RAG]: RAG is not available (started without RAG mode)")
                            } else {
                                val parts = input.split("\\s+".toRegex())
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
                                        println("[RAG]: Similarity threshold: ${String.format("%.2f", ragOrchestrator.minSimilarity)}")
                                        println("[RAG]: Reranker threshold: ${String.format("%.2f", ragOrchestrator.rerankThresholdValue)}")
                                        println("[RAG]: Reranker enabled: ${ragOrchestrator.useReranker}")
                                    }
                                    parts.size == 3 && parts[1].equals("threshold", ignoreCase = true) -> {
                                        try {
                                            val threshold = parts[2].toFloat()
                                            ragOrchestrator.setSimilarityThreshold(threshold)
                                        } catch (e: Exception) {
                                            println("[RAG]: Invalid threshold value. Use a number between 0.0 and 1.0")
                                        }
                                    }
                                    parts.size == 3 && parts[1].equals("rerank-threshold", ignoreCase = true) -> {
                                        try {
                                            val threshold = parts[2].toFloat()
                                            ragOrchestrator.setRerankThreshold(threshold)
                                        } catch (e: Exception) {
                                            println("[RAG]: Invalid rerank threshold value. Use a number between 0.0 and 1.0")
                                        }
                                    }
                                    parts.size == 3 && parts[1].equals("reranker", ignoreCase = true) -> {
                                        when {
                                            parts[2].equals("on", ignoreCase = true) || 
                                            parts[2].equals("enable", ignoreCase = true) -> {
                                                ragOrchestrator.setRerankerEnabled(true)
                                            }
                                            parts[2].equals("off", ignoreCase = true) || 
                                            parts[2].equals("disable", ignoreCase = true) -> {
                                                ragOrchestrator.setRerankerEnabled(false)
                                            }
                                            else -> {
                                                println("[RAG]: Unknown reranker command. Use:")
                                                println("  /rag reranker on  - Enable reranker")
                                                println("  /rag reranker off - Disable reranker")
                                            }
                                        }
                                    }
                                    input.startsWith("/rag compare", ignoreCase = true) -> {
                                        val query = input.removePrefix("/rag compare").trim()
                                        if (query.isBlank()) {
                                            println("[RAG]: Please provide a query to compare. Usage:")
                                            println("  /rag compare <your question>")
                                        } else {
                                            println("[RAG]: Comparing answers with and without reranker filter...")
                                            println("[RAG]: Query: $query")
                                            println()
                                            
                                            val comparisonResult = ragOrchestrator.compareAnswersWithFilter(query)
                                            if (comparisonResult.isSuccess) {
                                                val result = comparisonResult.getOrThrow()
                                                println("=".repeat(80))
                                                println("ANSWER WITHOUT RERANKER FILTER:")
                                                println("=".repeat(80))
                                                println(result.answerWithoutFilter)
                                                println()
                                                println("=".repeat(80))
                                                println("ANSWER WITH RERANKER FILTER (threshold: ${String.format("%.2f", result.rerankThreshold)}):")
                                                println("=".repeat(80))
                                                println(result.answerWithFilter)
                                                println()
                                                println("[RAG]: Comparison complete. Review both answers to evaluate quality.")
                                            } else {
                                                val error = comparisonResult.exceptionOrNull()
                                                println("[RAG]: Failed to compare answers: ${error?.message}")
                                            }
                                        }
                                    }
                                    else -> {
                                        println("[RAG]: Unknown command. Available commands:")
                                        println("  /rag on/off              - Enable/disable RAG")
                                        println("  /rag status               - Show RAG status")
                                        println("  /rag threshold <0.0-1.0>  - Set similarity threshold")
                                        println("  /rag rerank-threshold <0.0-1.0> - Set reranker threshold")
                                        println("  /rag reranker on/off      - Enable/disable reranker")
                                        println("  /rag compare <query>      - Compare answers with/without filter")
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
                                
                                // Display sources if RAG was used
                                if (useRAG && ragOrchestrator != null && ragOrchestrator.isRAGEnabled()) {
                                    val sources = ragOrchestrator.getLastUsedSources()
                                    if (sources.isNotEmpty()) {
                                        println()
                                        println("Источники:")
                                        sources.forEachIndexed { index, source ->
                                            println("  ${index + 1}. $source")
                                        }
                                    }
                                }
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
