package com.example.aichat.console.agent

import com.example.aichat.console.PersonalConfig
import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.model.ChatMessage
import com.example.aichat.console.rag.RAGPipeline
import com.example.aichat.console.rag.RelevanceReranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Team Assistant Orchestrator
 * Combines RAG (Retrieval-Augmented Generation) with Tasks MCP for team task management
 * 
 * Flow:
 * 1. User asks a question or requests task operation
 * 2. Search RAG index for relevant project documentation (if question about project)
 * 3. Use Tasks MCP tools for task management operations
 * 4. Combine RAG context + task context in system prompt
 * 5. Send enhanced prompt to LLM
 * 6. Return contextualized response
 */
class TeamAssistantOrchestrator(
    private val llmClient: OpenAiClient,
    private val ragPipeline: RAGPipeline,
    private val tasksMcpClient: McpClientWrapper? = null
) {
    
    private val baseOrchestrator: AgentOrchestrator = AgentOrchestrator(llmClient, tasksMcpClient)
    private var conversationHistory = mutableListOf<ChatMessage>()
    private val reranker: RelevanceReranker = RelevanceReranker()
    
    /**
     * Last used sources from RAG search (for displaying to user)
     */
    private var lastUsedSources: List<String> = emptyList()
    
    /**
     * Whether RAG is currently enabled
     */
    var ragEnabled: Boolean = true
    
    /**
     * Number of RAG results to include in context
     */
    var ragContextLimit: Int = 10
    
    /**
     * Minimum similarity threshold for initial vector search (0.0 to 1.0)
     */
    var minSimilarity: Float = 0.3f
    
    /**
     * Reranker threshold for second-stage filtering (0.0 to 1.0)
     */
    var rerankThresholdValue: Float = 0.71f
    
    /**
     * Whether to use reranker for second-stage filtering
     */
    var useReranker: Boolean = true
    
    /**
     * Maximum total characters from RAG context to include
     */
    var maxContextChars: Int = 4000
    
    /**
     * Path to team assistant prompt file
     */
    private val promptFilePath: String = "docs/team-assistant-prompt-en.md"
    
    /**
     * Initializes the orchestrator
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Initialize base orchestrator (for Tasks MCP tools)
            baseOrchestrator.initialize()
            
            // Check RAG index
            val stats = ragPipeline.getStats()
            if (stats.totalChunks == 0) {
                println("[RAG]: ⚠ Warning: No documents indexed in RAG database")
                println("[RAG]: Use 'rag index <path>' to index documents first")
            } else {
                println("[RAG]: ✓ Index contains ${stats.totalChunks} chunks from ${stats.uniqueSources} source(s)")
            }
            
            // Load team assistant prompt
            val systemMessage = createSystemMessage()
            conversationHistory.clear()
            conversationHistory.add(systemMessage)
            
            // Set system message in base orchestrator
            baseOrchestrator.setConversationHistory(listOf(systemMessage))
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Processes a user command with RAG-enhanced context (if RAG is enabled and question is about project)
     */
    suspend fun processCommand(userCommand: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Determine if this is a question about the project (should use RAG)
            val isProjectQuestion = isProjectRelatedQuestion(userCommand)
            
            // If RAG is disabled or not a project question, use base orchestrator directly
            if (!ragEnabled || !isProjectQuestion) {
                lastUsedSources = emptyList()
                return@withContext baseOrchestrator.processCommand(userCommand)
            }
            
            // Search RAG index for relevant context
            val ragContextResult = retrieveRAGContextWithSources(userCommand)
            val ragContext = ragContextResult.first
            lastUsedSources = ragContextResult.second
            
            // Build enhanced user message with RAG context
            val enhancedUserMessage = if (ragContext.isNotEmpty()) {
                """
                |$userCommand
                |
                |[Relevant context from indexed project documentation:]
                |$ragContext
                |
                |Please use the provided context to answer the question. If the context doesn't contain relevant information, 
                |you can still answer based on your general knowledge, but prioritize the provided context.
                """.trimMargin()
            } else {
                userCommand
            }
            
            // Use base orchestrator to process (it handles LLM + Tasks MCP tools)
            val result = baseOrchestrator.processCommand(enhancedUserMessage)
            
            // Update our conversation history
            if (result.isSuccess) {
                conversationHistory.add(ChatMessage(role = "user", content = userCommand))
                val response = result.getOrThrow()
                conversationHistory.add(ChatMessage(role = "assistant", content = response))
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Determines if a question is about the project (should use RAG)
     */
    private fun isProjectRelatedQuestion(query: String): Boolean {
        val lowerQuery = query.lowercase()
        
        // Task management commands don't need RAG
        val taskKeywords = listOf(
            "create task", "show tasks", "get tasks", "list tasks",
            "update task", "task status", "project status", "priority",
            "assignee", "what should i do", "recommend"
        )
        
        if (taskKeywords.any { lowerQuery.contains(it) }) {
            return false // Task management - use MCP tools, not RAG
        }
        
        // Project-related questions should use RAG
        val projectKeywords = listOf(
            "how does", "what is", "explain", "architecture", "implementation",
            "how to", "where is", "why does", "code", "file", "function",
            "class", "module", "database", "api", "authentication", "rag",
            "mcp", "documentation"
        )
        
        return projectKeywords.any { lowerQuery.contains(it) }
    }
    
    /**
     * Gets the sources used in the last RAG search
     */
    fun getLastUsedSources(): List<String> = lastUsedSources
    
    /**
     * Retrieves relevant context from RAG index with optional reranking
     * Returns pair of (context string, list of unique sources)
     */
    private suspend fun retrieveRAGContextWithSources(query: String): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        try {
            // First stage: vector similarity search
            val searchResult = ragPipeline.search(
                queryText = query,
                limit = ragContextLimit * 2, // Get more results for reranking
                minSimilarity = minSimilarity
            )
            
            if (searchResult.isFailure) {
                println("[RAG]: Failed to search index: ${searchResult.exceptionOrNull()?.message}")
                return@withContext Pair("", emptyList())
            }
            
            var results = searchResult.getOrThrow()
            
            if (results.isEmpty()) {
                println("[RAG]: No relevant documents found for query")
                return@withContext Pair("", emptyList())
            } else {
                println("[RAG]: Found ${results.size} relevant document chunks")
            }
            
            // Second stage: reranking/filtering
            if (useReranker) {
                results = reranker.rerank(
                    results = results,
                    rerankThreshold = rerankThresholdValue,
                    maxResults = ragContextLimit
                )
                println("[RAG]: After reranking: ${results.size} chunks")
            } else {
                // Just limit results if reranker is disabled
                results = results.take(ragContextLimit)
            }
            
            if (results.isEmpty()) {
                println("[RAG]: No results passed reranking threshold")
                return@withContext Pair("", emptyList())
            }

            // Format context from search results and collect unique sources
            val contextBuilder = StringBuilder()
            val sourcesSet = mutableSetOf<String>()
            var totalChars = 0
            
            results.forEachIndexed { index, result ->
                val chunk = result.chunk
                val similarity = result.similarity
                val sourceName = chunk.source.split("/").lastOrNull() ?: chunk.source
                
                // Add to sources set
                sourcesSet.add(chunk.source)
                
                // Format: [Source: filename] (similarity: 0.85)
                // Content...
                val chunkText = """
                    |[${index + 1}] Source: $sourceName (relevance: ${String.format("%.2f", similarity)})
                    |${chunk.content}
                    |
                """.trimMargin()
                
                val chunkChars = chunkText.length
                
                // Check if adding this chunk would exceed max context size
                if (totalChars + chunkChars > maxContextChars && index > 0) {
                    // Stop adding chunks if we've exceeded the limit (but include at least one)
                    return@forEachIndexed
                }
                
                contextBuilder.append(chunkText)
                totalChars += chunkChars
            }
            
            val context = contextBuilder.toString().trim()
            val sources = sourcesSet.map { it.split("/").lastOrNull() ?: it }.distinct().sorted()
            
            Pair(context, sources)
        } catch (e: Exception) {
            println("[RAG]: Error retrieving context: ${e.message}")
            Pair("", emptyList())
        }
    }
    
    /**
     * Creates system message with team assistant prompt and personal context
     */
    private fun createSystemMessage(): ChatMessage {
        // Try to load prompt from file
        val basePromptContent = try {
            val possiblePaths = listOf(
                File("console-agent/$promptFilePath"),
                File(promptFilePath),
                File("../$promptFilePath"),
                File("docs/team-assistant-prompt-en.md")
            )
            
            var loaded = false
            var content = ""
            
            for (path in possiblePaths) {
                if (path.exists() && path.isFile) {
                    content = path.readText()
                    loaded = true
                    println("[Team Assistant]: Loaded prompt from: ${path.absolutePath}")
                    break
                }
            }
            
            if (!loaded) {
                println("[Team Assistant]: ⚠ Prompt file not found, using default prompt")
                getDefaultPrompt()
            } else {
                content
            }
        } catch (e: Exception) {
            println("[Team Assistant]: Error loading prompt: ${e.message}, using default")
            getDefaultPrompt()
        }
        
        // Add personal context to the prompt
        val personalContext = if (PersonalConfig.isConfigured()) {
            "\n\n${PersonalConfig.formatPersonalContext()}\n"
        } else {
            "\n\n[Note: Personal information is not configured. The user can fill PersonalConfig.kt with their information for personalized responses.]\n"
        }
        
        val enhancedPrompt = buildString {
            appendLine(basePromptContent)
            append(personalContext)
            if (PersonalConfig.isConfigured()) {
                appendLine()
                appendLine("IMPORTANT: Use the personal information above for ALL your responses.")
                appendLine("- When answering questions, consider the user's role, interests, goals, and current projects.")
                appendLine("- Adapt your communication style to match: ${PersonalConfig.communicationStyle}")
                appendLine("- Provide personalized suggestions based on the user's preferences and work style.")
                appendLine("- When suggesting tasks or priorities, consider the user's goals, current projects, and work habits.")
                appendLine("- You can help with learning plans, project planning, code reviews, and any other questions.")
            }
        }
        
        return ChatMessage(role = "system", content = enhancedPrompt.trim())
    }
    
    /**
     * Default prompt if file is not found
     */
    private fun getDefaultPrompt(): String {
        val stats = ragPipeline.getStats()
        
        return buildString {
            appendLine("You are an intelligent team assistant that helps manage project tasks and answers questions about the project.")
            appendLine()
            appendLine("You have access to:")
            appendLine("1. **RAG (Retrieval-Augmented Generation)**: Indexed project documentation")
            if (stats.totalChunks > 0) {
                appendLine("   - ${stats.totalChunks} indexed document chunks from ${stats.uniqueSources} source(s)")
            } else {
                appendLine("   - No documents currently indexed")
            }
            appendLine("2. **Task Management Tools (MCP)**: Tools to create, query, and manage team tasks")
            appendLine()
            appendLine("**Your Capabilities:**")
            appendLine("- Answer questions about the project using RAG documentation")
            appendLine("- Create, view, and manage tasks using task management tools")
            appendLine("- Analyze project status and provide recommendations")
            appendLine("- Suggest task priorities based on dependencies and urgency")
            appendLine()
            appendLine("**Important Rules:**")
            appendLine("- Always use RAG for project-related questions")
            appendLine("- Use task management tools for task operations")
            appendLine("- Consider task dependencies when making recommendations")
            appendLine("- Prioritize unblocking blocked tasks")
            appendLine("- Respond in the same language as the user's question")
        }
    }
    
    /**
     * Clears conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
        val systemMessage = createSystemMessage()
        conversationHistory.add(systemMessage)
        baseOrchestrator.clearHistory()
    }
    
    /**
     * Shuts down the orchestrator
     */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        ragPipeline.close()
        baseOrchestrator.shutdown()
    }
}
