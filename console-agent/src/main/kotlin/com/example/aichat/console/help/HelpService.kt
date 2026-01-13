package com.example.aichat.console.help

import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.model.ChatMessage
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for handling /help command
 * Combines RAG search with MCP repo context to provide project-aware assistance
 */
class HelpService(
    private val llmClient: OpenAiClient,
    private val ragPipeline: RAGPipeline,
    private val repoMcpClient: McpClientWrapper? = null
) {
    
    /**
     * Processes a help query using RAG + MCP repo context
     * 
     * Flow:
     * 1. Search RAG index for relevant documentation
     * 2. Fetch repo context via MCP (branch, remote URL, open files)
     * 3. Construct enhanced prompt with context
     * 4. Send to LLM with project-specific instructions
     * 5. Return answer
     */
    suspend fun processHelpQuery(query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Search RAG index for relevant docs
            val ragContext = retrieveRAGContext(query)
            
            // Step 2: Fetch repo context via MCP (if available)
            val repoContext = retrieveRepoContext()
            
            // Step 3: Build enhanced system prompt
            val systemPrompt = buildSystemPrompt(ragContext, repoContext)
            
            // Step 4: Send to LLM
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = query)
            )
            
            val response = llmClient.chatCompletion(
                messages = messages,
                tools = null,
                temperature = 0.7,
                maxTokens = null
            )
            
            if (response.isSuccess) {
                val chatResponse = response.getOrThrow()
                val answer = chatResponse.choices.firstOrNull()?.message?.content
                    ?: "No response from LLM"
                Result.success(answer)
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Retrieves relevant context from RAG index
     */
    private suspend fun retrieveRAGContext(query: String): String = withContext(Dispatchers.IO) {
        try {
            val searchResult = ragPipeline.search(
                queryText = query,
                limit = 5,
                minSimilarity = 0.3f
            )
            
            if (searchResult.isFailure || searchResult.getOrNull().isNullOrEmpty()) {
                return@withContext ""
            }
            
            val results = searchResult.getOrThrow()
            val contextBuilder = StringBuilder()
            
            contextBuilder.appendLine("=== Relevant Documentation ===")
            results.forEachIndexed { index, result ->
                val sourceName = result.chunk.source.split("/").lastOrNull() ?: result.chunk.source
                contextBuilder.appendLine("\n[${index + 1}] Source: $sourceName (relevance: ${String.format("%.2f", result.similarity)})")
                contextBuilder.appendLine(result.chunk.content)
                contextBuilder.appendLine()
            }
            
            contextBuilder.toString()
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Retrieves repository context via MCP
     */
    private suspend fun retrieveRepoContext(): String = withContext(Dispatchers.IO) {
        if (repoMcpClient == null) {
            return@withContext ""
        }
        
        try {
            val contextBuilder = StringBuilder()
            contextBuilder.appendLine("=== Repository Context ===")
            
            // Get current branch
            val branchResult = repoMcpClient.callTool("git.getCurrentBranch", emptyMap())
            if (branchResult.isSuccess) {
                val branch = branchResult.getOrThrow().content.firstOrNull()?.text ?: "unknown"
                contextBuilder.appendLine("Current branch: $branch")
            }
            
            // Get remote URL
            val remoteResult = repoMcpClient.callTool("git.getRemoteUrl", mapOf("remote" to "origin"))
            if (remoteResult.isSuccess) {
                val remoteUrl = remoteResult.getOrThrow().content.firstOrNull()?.text ?: ""
                if (remoteUrl.isNotBlank()) {
                    contextBuilder.appendLine("Remote URL: $remoteUrl")
                }
            }
            
            contextBuilder.toString()
        } catch (e: Exception) {
            // MCP not available or failed - return empty context
            ""
        }
    }
    
    /**
     * Builds system prompt with RAG and repo context
     */
    private fun buildSystemPrompt(ragContext: String, repoContext: String): String {
        return buildString {
            appendLine("You are an expert AI assistant helping developers with THIS specific Android project.")
            appendLine()
            appendLine("IMPORTANT RULES:")
            appendLine("1. Use ONLY the provided documentation context to answer questions.")
            appendLine("2. If the context doesn't contain relevant information, say 'Not documented in the project docs.'")
            appendLine("3. Do NOT make up information or use general knowledge unless explicitly asked.")
            appendLine("4. Reference specific files, modules, or patterns from the documentation when possible.")
            appendLine("5. If unsure, suggest checking the relevant documentation file.")
            appendLine()
            
            if (ragContext.isNotBlank()) {
                appendLine(ragContext)
                appendLine()
            }
            
            if (repoContext.isNotBlank()) {
                appendLine(repoContext)
                appendLine()
            }
            
            if (ragContext.isBlank() && repoContext.isBlank()) {
                appendLine("Note: No project documentation or repository context is currently available.")
                appendLine("You can still help, but be clear that you're using general knowledge.")
            }
        }
    }
}

