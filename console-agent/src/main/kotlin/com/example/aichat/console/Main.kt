package com.example.aichat.console

import com.example.aichat.console.agent.AgentOrchestrator
import com.example.aichat.console.agent.RAGAgentOrchestrator
import com.example.aichat.console.agent.TeamAssistantOrchestrator
import com.example.aichat.console.help.HelpService
import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientFactory
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        // Check modes
        val useRAG = !args.contains("--no-rag")
        val useTeamAssistant = args.contains("--team-assistant")
        
        when {
            useTeamAssistant -> {
                println("=== Team Assistant (RAG + Tasks MCP) ===")
            }
            useRAG -> {
                println("=== RAG-Enhanced AI Agent ===")
            }
            else -> {
                println("=== Mobile Device Control Agent ===")
            }
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
            var tasksMcpProcess: Process? = null
            var repoMcpProcess: Process? = null
            var orchestrator: AgentOrchestrator? = null
            var ragOrchestrator: RAGAgentOrchestrator? = null
            var teamAssistantOrchestrator: TeamAssistantOrchestrator? = null
            var ragPipeline: RAGPipeline? = null
            var helpService: HelpService? = null
            var repoMcpClient: McpClientWrapper? = null
        
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
            
            // Initialize repo MCP client for /help command
            println("[Help]: Initializing repo MCP client for /help command...")
            val repoMcpResult = try {
                McpClientFactory.createRepoMcpClient()
            } catch (e: Exception) {
                println("[Help]: WARNING: Failed to start repo MCP server: ${e.message}")
                println("[Help]: /help command will work without repo context")
                Result.failure(e)
            }
            
            if (repoMcpResult.isSuccess) {
                val pair = repoMcpResult.getOrThrow()
                repoMcpClient = pair.first
                repoMcpProcess = pair.second
                println("[Help]: ✓ Repo MCP client connected")
            } else {
                println("[Help]: ✗ Repo MCP client not available (continuing without it)")
            }
            
            // Create HelpService if RAG is available
            if (useRAG && ragPipeline != null) {
                helpService = HelpService(
                    llmClient = llmClient,
                    ragPipeline = ragPipeline,
                    repoMcpClient = repoMcpClient
                )
            }
            
            // Create MCP client based on mode
            val mcpClient: McpClientWrapper? = if (useTeamAssistant) {
                // Team Assistant mode: use Tasks MCP server
                println("[Agent]: Connecting to tasks-mcp server...")
                try {
                    val tasksMcpResult = McpClientFactory.createTasksMcpClient()
                    if (tasksMcpResult.isSuccess) {
                        val pair = tasksMcpResult.getOrThrow()
                        tasksMcpProcess = pair.second // Keep process reference for cleanup
                        println("[Agent]: ✓ Tasks MCP server connected")
                        pair.first
                    } else {
                        val error = tasksMcpResult.exceptionOrNull()
                        println("WARNING: Failed to start Tasks MCP server: ${error?.message}")
                        println("[Agent]: Continuing without Tasks MCP tools...")
                        null
                    }
                } catch (e: Exception) {
                    println("WARNING: Failed to start Tasks MCP server: ${e.message}")
                    println("[Agent]: Continuing without Tasks MCP tools...")
                    null
                }
            } else {
                // Regular mode: use Mobile MCP server
                println("[Agent]: Connecting to mobile-mcp server...")
                println("[Agent]: NOTE: Make sure a device is connected (Android/iOS) for mobile-mcp to work")
                try {
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
            }
            
            // Create appropriate orchestrator
            if (useTeamAssistant && useRAG && ragPipeline != null) {
                // Team Assistant mode: RAG + Tasks MCP
                teamAssistantOrchestrator = TeamAssistantOrchestrator(llmClient, ragPipeline, mcpClient)
                val initResult = teamAssistantOrchestrator.initialize()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    println("ERROR: Failed to initialize Team Assistant orchestrator: ${error?.message}")
                    return@runBlocking
                }
            } else if (useRAG && ragPipeline != null) {
                // RAG mode: RAG + Mobile MCP (or no MCP)
                ragOrchestrator = RAGAgentOrchestrator(llmClient, ragPipeline, mcpClient)
                val initResult = ragOrchestrator.initialize()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    println("ERROR: Failed to initialize RAG orchestrator: ${error?.message}")
                    return@runBlocking
                }
            } else {
                // Regular mode: Mobile MCP only (or no MCP)
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
            when {
                useTeamAssistant && teamAssistantOrchestrator != null -> {
                    println("[Team Assistant]: Team Assistant mode enabled")
                    println("[Team Assistant]: - RAG: answers questions about the project")
                    println("[Team Assistant]: - Tasks MCP: manages team tasks")
                    println("[Team Assistant]: - Use '/rag off' to disable RAG, '/rag on' to enable it")
                }
                useRAG && ragOrchestrator != null -> {
                    println("[RAG]: RAG mode enabled - questions will be answered using indexed documents")
                    println("[RAG]: Use '/rag off' to disable RAG, '/rag on' to enable it")
                }
            }
            println()
            
            // Interactive loop
            // Use System.console() for better interactive input handling
            val console = System.console()
            var running = true
            while (running) {
                try {
                    val input = if (console != null) {
                        // Use console for better terminal interaction
                        console.readLine("> ")?.trim()
                    } else {
                        // Fallback to System.in if console is not available
                        print("> ")
                        System.out.flush() // Ensure prompt is displayed immediately
                        readLine()?.trim()
                    }
                    
                    when {
                        input.isNullOrBlank() -> continue
                        input.equals("exit", ignoreCase = true) -> {
                            running = false
                            println("[Agent]: Завершение работы...")
                        }
                        input.startsWith("/help", ignoreCase = true) -> {
                            // Handle /help command
                            val query = input.removePrefix("/help").trim()
                            if (query.isBlank()) {
                                println("[Help]: Usage: /help <your question>")
                                println("[Help]: Example: /help How do we handle navigation in Compose?")
                            } else {
                                if (helpService == null) {
                                    println("[Help]: Help service is not available. RAG must be enabled.")
                                    println("[Help]: Start the console agent with RAG enabled (default)")
                                } else {
                                    println("[Help]: Processing your question...")
                                    val result = helpService.processHelpQuery(query)
                                    if (result.isSuccess) {
                                        println()
                                        println("=".repeat(80))
                                        println("ANSWER:")
                                        println("=".repeat(80))
                                        println(result.getOrThrow())
                                        println("=".repeat(80))
                                    } else {
                                        val error = result.exceptionOrNull()
                                        println("[Help]: Error: ${error?.message ?: "Unknown error"}")
                                    }
                                }
                            }
                        }
                        input.startsWith("/rag", ignoreCase = true) -> {
                            // Handle RAG commands
                            val ragOrch = teamAssistantOrchestrator ?: ragOrchestrator
                            if (ragOrch == null) {
                                println("[RAG]: RAG is not available (started without RAG mode)")
                            } else {
                                val parts = input.split("\\s+".toRegex())
                                when {
                                    input.equals("/rag on", ignoreCase = true) || 
                                    input.equals("/rag enable", ignoreCase = true) -> {
                                        if (teamAssistantOrchestrator != null) {
                                            teamAssistantOrchestrator.ragEnabled = true
                                            println("[RAG]: ✓ RAG enabled")
                                        } else {
                                            ragOrchestrator?.enableRAG()
                                        }
                                    }
                                    input.equals("/rag off", ignoreCase = true) || 
                                    input.equals("/rag disable", ignoreCase = true) -> {
                                        if (teamAssistantOrchestrator != null) {
                                            teamAssistantOrchestrator.ragEnabled = false
                                            println("[RAG]: ✗ RAG disabled")
                                        } else {
                                            ragOrchestrator?.disableRAG()
                                        }
                                    }
                                    input.equals("/rag status", ignoreCase = true) || 
                                    input.equals("/rag", ignoreCase = true) -> {
                                        val status = if (teamAssistantOrchestrator != null) {
                                            if (teamAssistantOrchestrator.ragEnabled) "enabled" else "disabled"
                                        } else {
                                            if (ragOrchestrator?.isRAGEnabled() == true) "enabled" else "disabled"
                                        }
                                        println("[RAG]: Status: $status")
                                        val stats = ragPipeline?.getStats()
                                        if (stats != null) {
                                            println("[RAG]: Index contains ${stats.totalChunks} chunks from ${stats.uniqueSources} source(s)")
                                        }
                                        if (teamAssistantOrchestrator != null) {
                                            println("[RAG]: Similarity threshold: ${String.format("%.2f", teamAssistantOrchestrator.minSimilarity)}")
                                            println("[RAG]: Reranker threshold: ${String.format("%.2f", teamAssistantOrchestrator.rerankThresholdValue)}")
                                            println("[RAG]: Reranker enabled: ${teamAssistantOrchestrator.useReranker}")
                                        } else if (ragOrchestrator != null) {
                                            println("[RAG]: Similarity threshold: ${String.format("%.2f", ragOrchestrator.minSimilarity)}")
                                            println("[RAG]: Reranker threshold: ${String.format("%.2f", ragOrchestrator.rerankThresholdValue)}")
                                            println("[RAG]: Reranker enabled: ${ragOrchestrator.useReranker}")
                                        }
                                    }
                                    parts.size == 3 && parts[1].equals("threshold", ignoreCase = true) -> {
                                        try {
                                            val threshold = parts[2].toFloat()
                                            if (teamAssistantOrchestrator != null) {
                                                teamAssistantOrchestrator.minSimilarity = threshold
                                                println("[RAG]: Similarity threshold set to ${String.format("%.2f", threshold)}")
                                            } else {
                                                ragOrchestrator?.setSimilarityThreshold(threshold)
                                            }
                                        } catch (e: Exception) {
                                            println("[RAG]: Invalid threshold value. Use a number between 0.0 and 1.0")
                                        }
                                    }
                                    parts.size == 3 && parts[1].equals("rerank-threshold", ignoreCase = true) -> {
                                        try {
                                            val threshold = parts[2].toFloat()
                                            if (teamAssistantOrchestrator != null) {
                                                teamAssistantOrchestrator.rerankThresholdValue = threshold
                                                println("[RAG]: Reranker threshold set to ${String.format("%.2f", threshold)}")
                                            } else {
                                                ragOrchestrator?.setRerankThreshold(threshold)
                                            }
                                        } catch (e: Exception) {
                                            println("[RAG]: Invalid rerank threshold value. Use a number between 0.0 and 1.0")
                                        }
                                    }
                                    parts.size == 3 && parts[1].equals("reranker", ignoreCase = true) -> {
                                        when {
                                            parts[2].equals("on", ignoreCase = true) || 
                                            parts[2].equals("enable", ignoreCase = true) -> {
                                                if (teamAssistantOrchestrator != null) {
                                                    teamAssistantOrchestrator.useReranker = true
                                                    println("[RAG]: Reranker enabled")
                                                } else {
                                                    ragOrchestrator?.setRerankerEnabled(true)
                                                }
                                            }
                                            parts[2].equals("off", ignoreCase = true) || 
                                            parts[2].equals("disable", ignoreCase = true) -> {
                                                if (teamAssistantOrchestrator != null) {
                                                    teamAssistantOrchestrator.useReranker = false
                                                    println("[RAG]: Reranker disabled")
                                                } else {
                                                    ragOrchestrator?.setRerankerEnabled(false)
                                                }
                                            }
                                            else -> {
                                                println("[RAG]: Unknown reranker command. Use:")
                                                println("  /rag reranker on  - Enable reranker")
                                                println("  /rag reranker off - Disable reranker")
                                            }
                                        }
                                    }
                                    input.startsWith("/rag compare", ignoreCase = true) -> {
                                        println("[RAG]: Compare command is not available in Team Assistant mode")
                                        println("[RAG]: Use regular queries to test RAG functionality")
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
                            val result = when {
                                useTeamAssistant && teamAssistantOrchestrator != null -> {
                                    teamAssistantOrchestrator.processCommand(input)
                                }
                                useRAG && ragOrchestrator != null -> {
                                    ragOrchestrator.processCommand(input)
                                }
                                else -> {
                                    orchestrator?.processCommand(input) ?: Result.failure(Exception("Orchestrator not available"))
                                }
                            }
                            
                            if (result.isSuccess) {
                                val response = result.getOrThrow()
                                println("[Agent]: $response")
                                
                                // Display sources if RAG was used
                                val sources = when {
                                    useTeamAssistant && teamAssistantOrchestrator != null && teamAssistantOrchestrator.ragEnabled -> {
                                        teamAssistantOrchestrator.getLastUsedSources()
                                    }
                                    useRAG && ragOrchestrator != null && ragOrchestrator.isRAGEnabled() -> {
                                        ragOrchestrator.getLastUsedSources()
                                    }
                                    else -> emptyList()
                                }
                                
                                if (sources.isNotEmpty()) {
                                    println()
                                    println("Источники:")
                                    sources.forEachIndexed { index, source ->
                                        println("  ${index + 1}. $source")
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
                teamAssistantOrchestrator?.shutdown()
                ragOrchestrator?.shutdown()
                orchestrator?.shutdown()
                ragPipeline?.close()
                mcpProcess?.destroyForcibly() // Clean up Mobile MCP server process
                tasksMcpProcess?.destroyForcibly() // Clean up Tasks MCP server process
                repoMcpProcess?.destroyForcibly() // Clean up repo MCP server process
                repoMcpClient?.disconnect()
            } catch (e: Exception) {
                println("[Agent]: Error during shutdown: ${e.message}")
            }
        }
    }
    
    println("До свидания!")
}
