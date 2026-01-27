package com.example.aichat.console

import com.example.aichat.console.PersonalConfig
import com.example.aichat.console.agent.AgentOrchestrator
import com.example.aichat.console.agent.RAGAgentOrchestrator
import com.example.aichat.console.agent.TeamAssistantOrchestrator
import com.example.aichat.console.help.HelpService
import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientFactory
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.runBlocking

/**
 * Personal Agent Main Entry Point
 * 
 * This is a personalized version of the console agent that:
 * - Uses local Ollama LLM by default (http://localhost:11434/v1)
 * - Integrates personal information from PersonalConfig
 * - Supports RAG and Team Assistant modes
 * 
 * Usage:
 *   ./gradlew :console-agent:runPersonalAgent
 * 
 * Or with custom Ollama URL/model:
 *   OLLAMA_BASE_URL=http://localhost:11434 OLLAMA_MODEL=qwen2:7b-instruct ./gradlew :console-agent:runPersonalAgent
 */
fun main(args: Array<String>) {
    runBlocking {
        // Check modes
        val useRAG = !args.contains("--no-rag")
        val useTeamAssistant = args.contains("--team-assistant")
        
        when {
            useTeamAssistant -> {
                println("=== Personal Team Assistant (RAG + Tasks MCP) ===")
            }
            useRAG -> {
                println("=== Personal RAG-Enhanced AI Agent ===")
            }
            else -> {
                println("=== Personal AI Agent ===")
            }
        }
        println()
        
        // Ollama configuration (local LLM by default)
        val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val ollamaModel = System.getenv("OLLAMA_MODEL") ?: "qwen2:7b-instruct"
        
        // Ollama uses OpenAI-compatible API at /v1 endpoint
        val ollamaApiUrl = if (ollamaBaseUrl.endsWith("/v1")) {
            ollamaBaseUrl
        } else {
            "$ollamaBaseUrl/v1"
        }
        
        println("[LLM]: Using local Ollama")
        println("[LLM]:   Base URL: $ollamaBaseUrl")
        println("[LLM]:   API URL: $ollamaApiUrl")
        println("[LLM]:   Model: $ollamaModel")
        println()
        
        // Check if Ollama is accessible
        try {
            val testClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val testRequest = okhttp3.Request.Builder()
                .url("$ollamaBaseUrl/api/tags")
                .get()
                .build()
            val testResponse = testClient.newCall(testRequest).execute()
            if (!testResponse.isSuccessful) {
                println("[LLM]: ⚠ Warning: Ollama server might not be running or accessible")
                println("[LLM]: Make sure Ollama is running: ollama serve")
            } else {
                println("[LLM]: ✓ Ollama server is accessible")
            }
        } catch (e: Exception) {
            println("[LLM]: ⚠ Warning: Could not connect to Ollama: ${e.message}")
            println("[LLM]: Make sure Ollama is running: ollama serve")
            println("[LLM]: Continuing anyway...")
        }
        println()
        
        // Create OpenAI-compatible client for Ollama
        // Ollama doesn't require API key, so we use a dummy one
        val llmClient = OpenAiClient(
            apiKey = "ollama", // Ollama doesn't require auth, but OpenAiClient needs a key
            baseUrl = ollamaApiUrl,
            model = ollamaModel,
            folderId = null
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
                val ragOllamaBaseUrl = System.getenv("RAG_OLLAMA_BASE_URL") ?: ollamaBaseUrl
                val ragOllamaModel = System.getenv("RAG_OLLAMA_MODEL") ?: "nomic-embed-text"
                
                println("[RAG]: Initializing RAG pipeline...")
                println("[RAG]:   Database: $dbPath")
                println("[RAG]:   Ollama URL: $ragOllamaBaseUrl")
                println("[RAG]:   Embedding Model: $ragOllamaModel")
                
                ragPipeline = RAGPipeline.create(
                    dbPath = dbPath,
                    ollamaBaseUrl = ragOllamaBaseUrl,
                    ollamaModel = ragOllamaModel
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
                        tasksMcpProcess = pair.second
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
                // Regular mode: use Mobile MCP server (optional)
                println("[Agent]: Mobile MCP server is optional in personal agent mode")
                println("[Agent]: Continuing without mobile device control...")
                null
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
                // RAG mode: RAG (no mobile MCP by default)
                ragOrchestrator = RAGAgentOrchestrator(llmClient, ragPipeline, mcpClient)
                val initResult = ragOrchestrator.initialize()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    println("ERROR: Failed to initialize RAG orchestrator: ${error?.message}")
                    return@runBlocking
                }
            } else {
                // Regular mode: No MCP (personalized chat only)
                orchestrator = AgentOrchestrator(llmClient, mcpClient)
                val orchestratorInit = orchestrator.initialize()
                if (orchestratorInit.isFailure) {
                    val error = orchestratorInit.exceptionOrNull()
                    println("ERROR: Failed to initialize orchestrator: ${error?.message}")
                    return@runBlocking
                }
            }
            
            println("[Agent]: Готов к работе!")
            
            // Display personalization status
            if (PersonalConfig.isConfigured()) {
                println("[Personalization]: ✓ Personal information loaded")
                println("[Personalization]: ${PersonalConfig.getQuickContext()}")
            } else {
                println("[Personalization]: ⚠ Personal information not configured")
                println("[Personalization]: Fill PersonalConfig.kt to enable personalized responses")
            }
            println()
            
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
            val console = System.console()
            var running = true
            while (running) {
                try {
                    val input = if (console != null) {
                        console.readLine("> ")?.trim()
                    } else {
                        print("> ")
                        System.out.flush()
                        readLine()?.trim()
                    }
                    
                    when {
                        input.isNullOrBlank() -> continue
                        input.equals("exit", ignoreCase = true) -> {
                            running = false
                            println("[Agent]: Завершение работы...")
                        }
                        input.startsWith("/help", ignoreCase = true) -> {
                            val query = input.removePrefix("/help").trim()
                            if (query.isBlank()) {
                                println("[Help]: Usage: /help <your question>")
                                println("[Help]: Example: /help How do we handle navigation in Compose?")
                            } else {
                                if (helpService == null) {
                                    println("[Help]: Help service is not available. RAG must be enabled.")
                                    println("[Help]: Start the personal agent with RAG enabled (default)")
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
                            val ragOrch = teamAssistantOrchestrator ?: ragOrchestrator
                            if (ragOrch == null) {
                                println("[RAG]: RAG is not available (started without RAG mode)")
                            } else {
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
                                    }
                                    else -> {
                                        println("[RAG]: Unknown command. Available commands:")
                                        println("  /rag on/off              - Enable/disable RAG")
                                        println("  /rag status               - Show RAG status")
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
                mcpProcess?.destroyForcibly()
                tasksMcpProcess?.destroyForcibly()
                repoMcpProcess?.destroyForcibly()
                repoMcpClient?.disconnect()
            } catch (e: Exception) {
                println("[Agent]: Error during shutdown: ${e.message}")
            }
        }
    }
    
    println("До свидания!")
}
