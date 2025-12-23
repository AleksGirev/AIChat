package com.example.aichat.console.agent

import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.model.ChatMessage
import com.example.aichat.console.rag.RAGPipeline
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
     * Minimum similarity threshold for RAG results (0.0 to 1.0)
     */
    var minSimilarity: Float = 0.5f
    
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
                return@withContext baseOrchestrator.processCommand(userCommand)
            }
            
            // Search RAG index for relevant context
            val ragContext = retrieveRAGContext(userCommand)
            
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
     * Retrieves relevant context from RAG index
     */
    private suspend fun retrieveRAGContext(query: String): String = withContext(Dispatchers.IO) {
        try {
            val searchResult = ragPipeline.search(
                queryText = query,
                limit = ragContextLimit,
                minSimilarity = minSimilarity
            )
            
            if (searchResult.isFailure) {
                println("[RAG]: Failed to search index: ${searchResult.exceptionOrNull()?.message}")
                return@withContext ""
            }
            
            val results = searchResult.getOrThrow()
            
            if (results.isEmpty()) {
                println("[RAG]: No relevant documents found for query")
                return@withContext ""
            }
            
//            println("[RAG]: Found ${results.size} relevant document chunks")
//            results.forEach {
//                println("[RAG]: Found ${it}")
//            }


            // Format context from search results
            val contextBuilder = StringBuilder()
            var totalChars = 0
            
            results.forEachIndexed { index, result ->
                val chunk = result.chunk
                val similarity = result.similarity
                
                // Format: [Source: filename] (similarity: 0.85)
                // Content...
                val chunkText = """
                    |[${index + 1}] Source: ${chunk.source.split("/").lastOrNull() ?: chunk.source} (relevance: ${String.format("%.2f", similarity)})
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
            
            contextBuilder.toString().trim()
        } catch (e: Exception) {
            println("[RAG]: Error retrieving context: ${e.message}")
            ""
        }
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

