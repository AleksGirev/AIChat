package com.example.aichat.data.search

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.api.YandexApiService
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Repository for orchestrating article search via MCP server and LLM.
 * 
 * Flow:
 * 1. Initialize MCP client and list available tools
 * 2. Construct LLM request with user query and available tools
 * 3. Execute LLM call (YandexGPT)
 * 4. If LLM responds with tool_calls, execute them via MCP
 * 5. Return tool results to LLM for final response
 * 6. Parse LLM response to extract articles
 * 
 * Error Handling:
 * - Network failures wrapped in SearchResult.Error
 * - Empty results handled gracefully
 * - Invalid LLM output parsed with fallback
 */
class SearchAgentRepository(
    private val mcpSearchClient: McpSearchClient,
    private val yandexApiService: YandexApiService,
    private val gson: Gson
) {
    
    private val tag = "SearchAgentRepository"
    
    companion object {
        private const val MAX_TOOL_CALL_ITERATIONS = 3 // Prevent infinite loops
        private const val ARTICLES_TO_FIND = 10
    }
    
    /**
     * Searches for articles on a given topic from the last 7 days
     * 
     * @param topic Search topic (e.g., "artificial intelligence")
     * @return SearchResult containing articles or error
     */
    suspend fun searchArticles(topic: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Starting article search for topic: $topic")
            
            // Step 1: Initialize MCP client if not already initialized
            if (!mcpSearchClient.isConnected()) {
                val initResult = mcpSearchClient.initialize()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull() ?: Exception("Failed to initialize MCP client")
                    Log.e(tag, "MCP initialization failed", error)
                    return@withContext SearchResult.Error(
                        message = "Failed to connect to search service: ${error.message}",
                        cause = error
                    )
                }
            }
            
            // Step 2: List available tools from MCP server
            val toolsResult = mcpSearchClient.listTools()
            if (toolsResult.isFailure) {
                val error = toolsResult.exceptionOrNull() ?: Exception("Failed to list tools")
                Log.e(tag, "Failed to list MCP tools", error)
                return@withContext SearchResult.Error(
                    message = "Failed to retrieve search tools: ${error.message}",
                    cause = error
                )
            }
            
            val mcpTools = toolsResult.getOrNull() ?: emptyList()
            if (mcpTools.isEmpty()) {
                Log.e(tag, "No tools available from MCP server")
                return@withContext SearchResult.Error(
                    message = "No search tools available",
                    cause = null
                )
            }
            
            Log.d(tag, "Found ${mcpTools.size} tools: ${mcpTools.map { it.name }}")
            
            // Find search tool (prefer search_text, fallback to any search tool)
            val searchTool = mcpTools.find { 
                it.name == "search_text" || it.name == "search_web"
            } ?: mcpTools.find { 
                it.name.contains("search", ignoreCase = true) && 
                !it.name.contains("image", ignoreCase = true) &&
                !it.name.contains("video", ignoreCase = true)
            } ?: mcpTools.find { 
                it.name.contains("search", ignoreCase = true) || 
                it.description?.contains("search", ignoreCase = true) == true
            } ?: mcpTools.first()
            
            Log.d(tag, "Using search tool: ${searchTool.name}")
            
            // Step 3: Construct LLM request
            val userMessage = buildSearchPrompt(topic)
            val openAiTools = convertToOpenAiTools(mcpTools)
            
            // Step 4: Execute LLM call with tool support
            val articles = executeLlmWithTools(userMessage, openAiTools, searchTool.name, topic)
            
            if (articles.isEmpty()) {
                Log.w(tag, "No articles found for topic: $topic")
                return@withContext SearchResult.Empty
            }
            
            Log.d(tag, "Found ${articles.size} articles")
            SearchResult.Success(articles)
            
        } catch (e: Exception) {
            Log.e(tag, "Exception during article search", e)
            SearchResult.Error(
                message = "Search failed: ${e.message}",
                cause = e
            )
        }
    }
    
    /**
     * Builds the search prompt for the LLM
     */
    private fun buildSearchPrompt(topic: String): String {
        return """
            Find the $ARTICLES_TO_FIND most relevant, recent, and valuable articles in russian language from the last 7 days on the topic: "$topic".
            
            Return only titles and URLs, no explanations.
            Format: JSON array with objects containing "title" and "url" fields.
            If publication date is available, include "publicationDate" field.
        """.trimIndent()
    }
    
    /**
     * Converts MCP tools to OpenAI-compatible format
     */
    private fun convertToOpenAiTools(mcpTools: List<McpTool>): List<Map<String, Any>> {
        return mcpTools.map { tool ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to (tool.description ?: ""),
                    "parameters" to mapOf(
                        "type" to tool.inputSchema.type,
                        "properties" to (tool.inputSchema.properties?.mapValues { (_, prop) ->
                            mapOf<String, Any>(
                                "type" to prop.type,
                                "description" to (prop.description ?: "")
                            ).let { propMap ->
                                if (prop.enum != null) {
                                    propMap + ("enum" to prop.enum)
                                } else {
                                    propMap
                                }
                            }
                        } ?: emptyMap<String, Any>()),
                        "required" to (tool.inputSchema.required ?: emptyList<String>())
                    )
                )
            )
        }
    }
    
    /**
     * Executes LLM call with tool support, handling tool calls iteratively
     */
    private suspend fun executeLlmWithTools(
        userMessage: String,
        tools: List<Map<String, Any>>,
        searchToolName: String,
        topic: String
    ): List<Article> {
        var conversationHistory = mutableListOf<ChatMessage>(
            ChatMessage(role = "user", content = userMessage)
        )
        
        var iterations = 0
        while (iterations < MAX_TOOL_CALL_ITERATIONS) {
            iterations++
            
            // Call LLM
            val chatRequest = ChatRequest(
                model = Config.DEFAULT_YANDEXGPT_MODEL,
                messages = conversationHistory,
                tools = tools,
                toolChoice = "auto"
            )
            
            val response = callYandexApi(chatRequest)
            if (response == null) {
                Log.e(tag, "LLM API call failed")
                break
            }
            
            val message = response.choices.firstOrNull()?.message
            val content = message?.content ?: ""
            val toolCalls = message?.toolCalls
            
            // Add assistant response to conversation
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = content,
                toolCalls = toolCalls
            )
            
            conversationHistory.add(assistantMessage)
            
            // If no tool calls, we're done - parse the final response
            if (toolCalls.isNullOrEmpty()) {
                return parseArticlesFromResponse(content)
            }
            
            // Execute tool calls
            val toolResults = mutableListOf<ChatMessage>()
            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                val toolArguments = toolCall.function.arguments
                
                Log.d(tag, "Executing tool call: $toolName with args: $toolArguments")
                
                // Parse arguments
                val argsMap = try {
                    val jsonObj = gson.fromJson(toolArguments, JsonObject::class.java)
                    jsonObj.keySet().associateWith { key ->
                        jsonObj.get(key).asString
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse tool arguments", e)
                    mapOf("query" to topic) // Fallback to topic
                }
                
                // Call MCP tool with proper arguments
                val query = argsMap["query"] as? String ?: topic
                val maxResults = (argsMap["max_results"] as? Number)?.toInt() ?: 10
                val toolResult = mcpSearchClient.callSearchTool(
                    toolName = searchToolName,
                    query = query,
                    maxResults = maxResults
                )
                
                val toolResultContent = if (toolResult.isSuccess) {
                    val result = toolResult.getOrNull()
                    result?.content?.firstOrNull()?.text ?: ""
                } else {
                    "Error: ${toolResult.exceptionOrNull()?.message}"
                }
                
                toolResults.add(
                    ChatMessage(
                        role = "tool",
                        content = toolResultContent,
                        toolCallId = toolCall.id
                    )
                )
            }
            
            // Add tool results to conversation
            conversationHistory.addAll(toolResults)
        }
        
        // If we exhausted iterations, try to parse the last response
        val lastMessage = conversationHistory.lastOrNull()
        val lastContent = lastMessage?.content ?: ""
        return parseArticlesFromResponse(lastContent)
    }
    
    /**
     * Calls YandexGPT API
     */
    private suspend fun callYandexApi(request: ChatRequest): ChatResponse? {
        return try {
            val authHeader = "Bearer ${Config.YANDEX_IAM_TOKEN}"
            val response: Response<ChatResponse> = yandexApiService.sendChatRequest(
                authorization = authHeader,
                folderId = Config.YANDEX_FOLDER_ID,
                request = request
            )
            
            if (response.isSuccessful) {
                response.body()
            } else {
                val errorBody = response.errorBody()?.string() ?: response.message()
                Log.e(tag, "YandexGPT API error ${response.code()}: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception calling YandexGPT API", e)
            null
        }
    }
    
    /**
     * Parses articles from LLM response
     * Supports multiple formats:
     * 1. JSON string (quoted string containing JSON)
     * 2. JSON array directly
     * 3. JSON in code blocks (```json ... ```)
     * 4. Markdown list format
     */
    private fun parseArticlesFromResponse(content: String): List<Article> {
        val trimmedContent = content.trim()
        
        // Try 1: Extract JSON from code blocks
        val codeBlockRegex = Regex("""```(?:json)?\s*(.*?)```""", RegexOption.DOT_MATCHES_ALL)
        val codeBlockMatch = codeBlockRegex.find(trimmedContent)
        val jsonFromCodeBlock = codeBlockMatch?.groupValues?.get(1)?.trim()
        
        if (jsonFromCodeBlock != null) {
            return try {
                // First try parsing as direct JSON array
                val jsonArray = gson.fromJson(jsonFromCodeBlock, Array<Article>::class.java)
                jsonArray.toList()
            } catch (e: Exception) {
                // If that fails, try parsing as JSON string (quoted)
                try {
                    val unquotedString = gson.fromJson(jsonFromCodeBlock, String::class.java)
                    val jsonArray = gson.fromJson(unquotedString, Array<Article>::class.java)
                    jsonArray.toList()
                } catch (e2: Exception) {
                    Log.d(tag, "Failed to parse JSON from code block, trying markdown parsing", e2)
                    parseArticlesFromMarkdown(content)
                }
            }
        }
        
        // Try 2: Parse as JSON string (if content is a quoted string)
        if (trimmedContent.startsWith("\"") && trimmedContent.endsWith("\"")) {
            try {
                val unquotedString = gson.fromJson(trimmedContent, String::class.java)
                // Now try to parse the unquoted string as JSON array
                val jsonArray = gson.fromJson(unquotedString, Array<Article>::class.java)
                return jsonArray.toList()
            } catch (e: Exception) {
                Log.d(tag, "Failed to parse JSON string, trying direct parsing", e)
                // Continue to next parsing attempt
            }
        }
        
        // Try 3: Direct JSON array parsing
        return try {
            val jsonArray = gson.fromJson(trimmedContent, Array<Article>::class.java)
            jsonArray.toList()
        } catch (e: Exception) {
            // Try 4: Extract JSON array from text (look for [ {...}, {...} ] pattern)
            val jsonArrayRegex = Regex("""\[[\s\S]*?\]""")
            val jsonArrayMatch = jsonArrayRegex.find(trimmedContent)
            if (jsonArrayMatch != null) {
                try {
                    val jsonArray = gson.fromJson(jsonArrayMatch.value, Array<Article>::class.java)
                    jsonArray.toList()
                } catch (e2: Exception) {
                    Log.d(tag, "Failed to parse extracted JSON array, trying markdown parsing", e2)
                    parseArticlesFromMarkdown(content)
                }
            } else {
                // Fallback to markdown/list parsing
                Log.d(tag, "JSON parsing failed, trying markdown parsing", e)
                parseArticlesFromMarkdown(content)
            }
        }
    }
    
    /**
     * Parses articles from markdown list format
     * Example:
     * - [Title](URL)
     * - [Title 2](URL2)
     */
    private fun parseArticlesFromMarkdown(content: String): List<Article> {
        val articles = mutableListOf<Article>()
        val lines = content.lines()
        
        val linkRegex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
        
        for (line in lines) {
            val match = linkRegex.find(line)
            if (match != null) {
                val title = match.groupValues[1]
                val url = match.groupValues[2]
                articles.add(Article(title = title, url = url))
            }
        }
        
        return articles
    }
}

/**
 * Result of article search operation
 */
sealed class SearchResult {
    data class Success(val articles: List<Article>) : SearchResult()
    object Empty : SearchResult()
    data class Error(val message: String, val cause: Throwable? = null) : SearchResult()
}

