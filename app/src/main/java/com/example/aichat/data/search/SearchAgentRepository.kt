package com.example.aichat.data.search

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.api.YandexApiService
import com.example.aichat.data.brightdata.BrightDataMcpClient
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.mcp.model.McpToolResult
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * 7. For each article, fetch content using BrightData and generate summary
 * 8. Return articles with summaries
 * 
 * Error Handling:
 * - Network failures wrapped in SearchResult.Error
 * - Empty results handled gracefully
 * - Invalid LLM output parsed with fallback
 * - Article fetching failures don't block other articles
 */
class SearchAgentRepository(
    private val mcpSearchClient: McpSearchClient,
    private val brightDataMcpClient: BrightDataMcpClient,
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
            
            // Step 5: Fetch content and generate summaries for each article
            val articlesWithSummaries = fetchArticleSummaries(articles)
            
            SearchResult.Success(articlesWithSummaries)
            
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
                val query = argsMap["query"] as? String ?: topic + "последние актуальные новости по теме за последние 7 дней"
                val maxResults = 5
                val region = "ru-ru"
                val safesearch = argsMap["safesearch"] as? String ?: "moderate"
                val timelimit = "w"
                val page = (argsMap["page"] as? Number)?.toInt() ?: 1
                val backend = "google"
                
                val toolResult = mcpSearchClient.callSearchTool(
                    toolName = searchToolName,
                    query = query,
                    region = region,
                    safesearch = safesearch,
                    timelimit = timelimit,
                    maxResults = maxResults,
                    page = page,
                    backend = backend
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
    
    /**
     * Fetches article content and generates 3-sentence summaries for each article
     * 
     * @param articles List of articles with URLs
     * @return List of articles with summaries
     */
    private suspend fun fetchArticleSummaries(articles: List<Article>): List<Article> = withContext(Dispatchers.IO) {
        if (articles.isEmpty()) {
            return@withContext emptyList()
        }
        
        Log.d(tag, "Fetching content and generating summaries for ${articles.size} articles")
        
        // Initialize BrightData client if not already initialized
        if (!brightDataMcpClient.isConnected()) {
            val initResult = brightDataMcpClient.initialize()
            if (initResult.isFailure) {
                val error = initResult.exceptionOrNull() ?: Exception("Failed to initialize BrightData client")
                Log.e(tag, "BrightData initialization failed, returning articles without summaries", error)
                return@withContext articles // Return articles without summaries
            }
        }
        
        // Process articles in parallel (with limit to avoid overwhelming the system)
        val articlesWithSummaries = articles.mapIndexed { index, article ->
            async {
                try {
                    Log.d(tag, "Processing article ${index + 1}/${articles.size}: ${article.title}")
                    
                    // Step 1: Fetch article content using BrightData
                    val contentResult = brightDataMcpClient.readArticle(article.url)
                    if (contentResult.isFailure) {
                        val error = contentResult.exceptionOrNull()
                        Log.w(tag, "Failed to fetch content for ${article.url}: ${error?.message}")
                        return@async article // Return article without summary
                    }
                    
                    val toolResult = contentResult.getOrNull() ?: return@async article
                    
                    // Check if the tool result contains an error
                    if (toolResult.isError) {
                        val errorText = toolResult.content.firstOrNull()?.text ?: "Unknown error"
                        Log.w(tag, "BrightData returned error for ${article.url}: $errorText")
                        
                        // If it's a bridge server error, provide helpful diagnostics
                        if (errorText.contains("Expecting value", ignoreCase = true)) {
                            Log.e(tag, """
                                Bridge server communication error detected.
                                This usually means:
                                1. BrightData MCP stdio server is not running or crashed
                                2. Invalid API token - verify BRIGHTDATA_API_KEY
                                3. Bridge server cannot communicate with MCP server
                                
                                Check your bridge server logs for more details.
                            """.trimIndent())
                        }
                        
                        return@async article // Return article without summary
                    }
                    
                    val articleContent = toolResult.content.firstOrNull()?.text ?: ""
                    
                    if (articleContent.isBlank()) {
                        Log.w(tag, "Empty content for ${article.url}")
                        return@async article
                    }
                    
                    // Check if content is an error message
                    if (articleContent.startsWith("Error:", ignoreCase = true)) {
                        Log.w(tag, "Received error message instead of content for ${article.url}: $articleContent")
                        return@async article
                    }
                    
                    // Step 2: Generate 3-sentence summary using LLM
                    val summary = generateSummary(articleContent, article.title)
                    
                    // Step 3: Return article with summary
                    article.copy(summary = summary)
                    
                } catch (e: Exception) {
                    Log.e(tag, "Error processing article ${article.url}", e)
                    article // Return article without summary on error
                }
            }
        }.awaitAll()
        
        Log.d(tag, "Completed processing ${articlesWithSummaries.size} articles")
        articlesWithSummaries
    }
    
    /**
     * Generates a 3-sentence summary of article content using LLM
     * 
     * @param content Full article content
     * @param title Article title for context
     * @return 3-sentence summary in Russian
     */
    private suspend fun generateSummary(content: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            // Truncate content if too long (to avoid token limits)
            val truncatedContent = if (content.length > 3000) {
                content.take(3000) + "..."
            } else {
                content
            }
            
            val prompt = """
                Создай краткое резюме следующей статьи в ровно 3 предложениях на русском языке.
                
                Заголовок: $title
                
                Содержание статьи:
                $truncatedContent
                
                Резюме должно быть:
                - Точно 3 предложения
                - На русском языке
                - Кратким и информативным
                - Отражать основные моменты статьи
                
                Верни только резюме, без дополнительных объяснений.
            """.trimIndent()
            
            val chatRequest = ChatRequest(
                model = Config.DEFAULT_YANDEXGPT_MODEL,
                messages = listOf(
                    ChatMessage(role = "user", content = prompt)
                ),
                tools = null,
                toolChoice = null
            )
            
            val response = callYandexApi(chatRequest)
            if (response != null) {
                val summary = response.choices.firstOrNull()?.message?.content?.trim()
                if (!summary.isNullOrBlank()) {
                    Log.d(tag, "Generated summary for: $title")
                    return@withContext summary
                }
            }
            
            Log.w(tag, "Failed to generate summary for: $title")
            null
        } catch (e: Exception) {
            Log.e(tag, "Exception generating summary", e)
            null
        }
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

