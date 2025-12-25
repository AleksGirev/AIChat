package com.example.aichat.console.agent

import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.model.ChatMessage
import com.example.aichat.console.rag.RAGPipeline
import com.example.aichat.console.rag.RelevanceReranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RAG-enhanced agent orchestrator
 * Combines RAG (Retrieval-Augmented Generation) with LLM for context-aware responses
 * 
 * Flow:
 * 1. User asks a question
 * 2. Search RAG index for relevant documents
 * 3. Format context from search results
 * 4. Send query + context to LLM
 * 5. Return LLM response
 */
class RAGAgentOrchestrator(
    private val llmClient: OpenAiClient,
    private val ragPipeline: RAGPipeline,
    private val mcpClient: McpClientWrapper? = null
) {
    
    private val baseOrchestrator: AgentOrchestrator = AgentOrchestrator(llmClient, mcpClient)
    private var conversationHistory = mutableListOf<ChatMessage>()
    private val reranker: RelevanceReranker = RelevanceReranker()
    
    /**
     * Last used sources from RAG search (for displaying to user)
     */
    private var lastUsedSources: List<String> = emptyList()
    
    /**
     * Whether RAG is currently enabled
     * When disabled, the orchestrator works like a regular AgentOrchestrator without RAG context
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
     * Applied after initial vector search
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
     * Initializes the orchestrator
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Initialize base orchestrator (for MCP tools)
            baseOrchestrator.initialize()
            
            // Check RAG index
            val stats = ragPipeline.getStats()
            if (stats.totalChunks == 0) {
                println("[RAG]: ⚠ Warning: No documents indexed in RAG database")
                println("[RAG]: Use 'rag index <path>' to index documents first")
            } else {
                println("[RAG]: ✓ Index contains ${stats.totalChunks} chunks from ${stats.uniqueSources} source(s)")
            }
            
            // Create system message with RAG instructions
            val systemMessage = createSystemMessage()
            conversationHistory.clear()
            conversationHistory.add(systemMessage)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Processes a user command with RAG-enhanced context (if RAG is enabled)
     */
    suspend fun processCommand(userCommand: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // If RAG is disabled, use base orchestrator directly
            if (!ragEnabled) {
                lastUsedSources = emptyList() // Clear sources when RAG is disabled
                return@withContext baseOrchestrator.processCommand(userCommand)
            }
            
            // Search RAG index for relevant context
            val ragContextResult = retrieveRAGContextWithSources(userCommand)
            val ragContext = ragContextResult.first
            lastUsedSources = ragContextResult.second // Store sources for display
            
            // Build enhanced user message with RAG context
            val enhancedUserMessage = if (ragContext.isNotEmpty()) {
                """
                |$userCommand
                |
                |[Relevant context from indexed documents:]
                |$ragContext
                |
                |Please use the provided context to answer the question. If the context doesn't contain relevant information, 
                |you can still answer based on your general knowledge, but prioritize the provided context.
                """.trimMargin()
            } else {
                userCommand
            }
            
            // Use base orchestrator to process (it handles LLM + MCP tools)
            // The base orchestrator will add the message to its history
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
     * Gets the sources used in the last RAG search
     */
    fun getLastUsedSources(): List<String> = lastUsedSources
    
    /**
     * Enables RAG retrieval
     */
    fun enableRAG() {
        ragEnabled = true
        println("[RAG]: ✓ RAG enabled - questions will use indexed documents")
        // Update system message to reflect RAG status
        val systemMessage = createSystemMessage()
        baseOrchestrator.setConversationHistory(listOf(systemMessage))
    }
    
    /**
     * Disables RAG retrieval (falls back to regular chat without RAG context)
     */
    fun disableRAG() {
        ragEnabled = false
        println("[RAG]: ✗ RAG disabled - questions will be answered without RAG context")
        // Update system message to reflect RAG status
        val systemMessage = createSystemMessage()
        baseOrchestrator.setConversationHistory(listOf(systemMessage))
    }
    
    /**
     * Gets current RAG status
     */
    fun isRAGEnabled(): Boolean = ragEnabled
    
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
                println("RAG search result size ${results.size}")
            }
            
            // Second stage: reranking/filtering
            if (useReranker) {
                results = reranker.rerank(
                    results = results,
                    rerankThreshold = rerankThresholdValue,
                    maxResults = ragContextLimit
                )
                println("RAG rerank size ${results.size}")

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
     * Compares answer quality with and without reranker filter
     * Returns both answers for comparison
     */
    suspend fun compareAnswersWithFilter(userCommand: String): Result<ComparisonResult> = withContext(Dispatchers.IO) {
        try {
            if (!ragEnabled) {
                return@withContext Result.failure(Exception("RAG is disabled. Enable RAG first to compare answers."))
            }
            
            // Get answer without reranker
            val wasRerankerEnabled = useReranker
            useReranker = false
            val answerWithoutFilter = processCommand(userCommand)
            useReranker = wasRerankerEnabled
            
            // Get answer with reranker
            val answerWithFilter = processCommand(userCommand)
            
            if (answerWithoutFilter.isFailure || answerWithFilter.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to generate one or both answers: " +
                        "${answerWithoutFilter.exceptionOrNull()?.message ?: ""} " +
                        "${answerWithFilter.exceptionOrNull()?.message ?: ""}")
                )
            }
            
            Result.success(
                ComparisonResult(
                    query = userCommand,
                    answerWithoutFilter = answerWithoutFilter.getOrThrow(),
                    answerWithFilter = answerWithFilter.getOrThrow(),
                    rerankThreshold = rerankThresholdValue,
                    useReranker = useReranker
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sets the similarity threshold for initial vector search
     */
    fun setSimilarityThreshold(threshold: Float) {
        require(threshold in 0.0f..1.0f) { "Threshold must be between 0.0 and 1.0" }
        minSimilarity = threshold
        println("[RAG]: Similarity threshold set to ${String.format("%.2f", threshold)}")
    }
    
    /**
     * Sets the reranker threshold for second-stage filtering
     */
    fun setRerankThreshold(threshold: Float) {
        require(threshold in 0.0f..1.0f) { "Threshold must be between 0.0 and 1.0" }
        rerankThresholdValue = threshold
        println("[RAG]: Reranker threshold set to ${String.format("%.2f", threshold)}")
    }
    
    /**
     * Enables or disables reranker
     */
    fun setRerankerEnabled(enabled: Boolean) {
        useReranker = enabled
        println("[RAG]: Reranker ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Creates system message with RAG instructions
     */
    private fun createSystemMessage(): ChatMessage {
        val stats = ragPipeline.getStats()
        
        val systemPrompt = buildString {
            if (ragEnabled) {
                appendLine("You are a helpful AI assistant with access to indexed documents through RAG (Retrieval-Augmented Generation).")
                
                if (stats.totalChunks > 0) {
                    appendLine("You have access to ${stats.totalChunks} indexed document chunks from ${stats.uniqueSources} source(s).")
                    appendLine("When answering questions, relevant context from these documents will be provided to you.")
                    appendLine("Use this context to provide accurate, document-based answers.")
                } else {
                    appendLine("Note: No documents are currently indexed. You can answer questions using your general knowledge.")
                }
            } else {
                appendLine("You are a helpful AI assistant.")
                appendLine("Note: RAG (Retrieval-Augmented Generation) is currently disabled. Answer questions using your general knowledge.")
            }
            
            if (mcpClient != null) {
                appendLine("You also have access to mobile device control tools through MCP.")
            }
            
            appendLine("Always respond in the same language as the user's question.")
        }
        
        return ChatMessage(role = "system", content = systemPrompt)
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

/**
 * Result of comparing answers with and without reranker filter
 */
data class ComparisonResult(
    val query: String,
    val answerWithoutFilter: String,
    val answerWithFilter: String,
    val rerankThreshold: Float,
    val useReranker: Boolean
)

