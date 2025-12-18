package com.example.aichat.data.brightdata

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.mcp.McpClient
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.mcp.model.McpToolResult
import com.example.aichat.data.mcp.transport.RestMcpTransport
import com.example.aichat.data.mcp.transport.StdioMcpTransport
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * MCP Client for BrightData MCP server.
 * 
 * This client handles communication with BrightData MCP server for reading articles by URLs.
 * 
 * Architecture:
 * - Supports both stdio and REST transport modes
 * - Default: REST transport via HTTP bridge server (recommended for Android)
 * - Alternative: stdio transport via npx (requires Node.js, not practical for Android)
 * 
 * Setup for Android (REST Bridge - Recommended):
 * - Run a local HTTP bridge server that wraps BrightData MCP stdio server
 * - Bridge server command: npx -y @brightdata/mcp (runs on your development machine)
 * - Bridge server exposes REST endpoints: GET /tools, POST /tools/{name}
 * - Configure BRIGHTDATA_MCP_BRIDGE_URL in Config.kt (e.g., http://10.0.2.2:8002)
 * - Configure BRIGHTDATA_API_KEY in Config.kt
 * 
 * Setup for stdio (Not recommended for Android):
 * - Requires Node.js and npx on the device
 * - Configure BRIGHTDATA_API_KEY in Config.kt
 * - API token passed via API_TOKEN environment variable
 * 
 * MCP Schema (for reference):
 * {
 *   "mcpServers": {
 *     "brightdata-mcp": {
 *       "command": "npx",
 *       "args": ["-y", "@brightdata/mcp"],
 *       "env": {
 *         "API_TOKEN": "<your API token>"
 *       }
 *     }
 *   }
 * }
 * 
 * Documentation: https://docs.brightdata.com/integrations/ai-integrations
 * 
 * Error Handling:
 * - Network/process failures are caught and wrapped in Result
 * - Connection errors handled gracefully
 * - Retry logic handled at repository level
 */
class BrightDataMcpClient(
    private val apiKey: String? = Config.BRIGHTDATA_API_KEY.takeIf { it.isNotBlank() },
    private val gson: Gson,
    private val httpClient: OkHttpClient? = null,
    // Use REST bridge by default (recommended for Android)
    private val useRestBridge: Boolean = true,
    private val bridgeUrl: String? = Config.BRIGHTDATA_MCP_BRIDGE_URL.takeIf { it.isNotBlank() },
    // stdio command (only used if useRestBridge = false)
    private val command: List<String> = listOf("npx", "-y", "@brightdata/mcp")
) {
    
    private val tag = "BrightDataMcpClient"
    private var mcpRepository: McpRepository? = null
    
    /**
     * Initializes connection to the BrightData MCP server
     * 
     * Supports two modes:
     * 1. REST Bridge (default, recommended for Android): Connects to local HTTP bridge server
     * 2. stdio (alternative): Runs npx command directly (requires Node.js, not practical for Android)
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrBlank()) {
                val error = IllegalArgumentException("BRIGHTDATA_API_KEY is required. Set it in Config.kt")
                Log.e(tag, "BrightData API key not configured", error)
                return@withContext Result.failure(error)
            }
            
            val transport = if (useRestBridge) {
                // Use REST bridge (recommended for Android)
                val url = bridgeUrl ?: run {
                    val error = IllegalArgumentException(
                        "BRIGHTDATA_MCP_BRIDGE_URL is required when using REST bridge. " +
                        "Set it in Config.kt (e.g., http://10.0.2.2:8002)"
                    )
                    Log.e(tag, "BrightData bridge URL not configured", error)
                    return@withContext Result.failure(error)
                }
                
                Log.d(tag, "Initializing BrightData MCP client via REST bridge: $url")
                
                RestMcpTransport(
                    baseUrl = url,
                    apiKey = apiKey, // Pass API key as Bearer token
                    httpClient = httpClient ?: throw IllegalStateException("OkHttpClient required for REST bridge"),
                    gson = gson
                )
            } else {
                // Use stdio transport (requires Node.js)
                Log.d(tag, "Initializing BrightData MCP client via stdio: ${command.joinToString(" ")}")
                
                val environment = mapOf(
                    "API_TOKEN" to apiKey
                )
                
                StdioMcpTransport(
                    command = command,
                    gson = gson,
                    environment = environment
                )
            }
            
            val mcpClient = McpClient(transport)
            mcpRepository = McpRepository(mcpClient)
            
            val initResult = mcpRepository?.initialize()
            if (initResult?.isSuccess == true) {
                Log.d(tag, "BrightData MCP client initialized successfully")
                Result.success(Unit)
            } else {
                val error = initResult?.exceptionOrNull() ?: Exception("Unknown initialization error")
                Log.e(tag, "Failed to initialize BrightData MCP client", error)
                if (useRestBridge) {
                    Log.e(tag, "Make sure your BrightData MCP bridge server is running at: $bridgeUrl")
                    Log.e(tag, "Bridge server should run: npx -y @brightdata/mcp with API_TOKEN=$apiKey")
                } else {
                    Log.e(tag, "Note: BrightData MCP requires Node.js and npx. For Android, use REST bridge mode.")
                }
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during BrightData MCP client initialization", e)
            if (useRestBridge) {
                Log.e(tag, "Make sure your BrightData MCP bridge server is running")
            }
            Result.failure(e)
        }
    }
    
    /**
     * Lists available tools from the BrightData MCP server
     * Expected tools: scrape_as_markdown, search_engine, scrape_batch, etc.
     * 
     * Common tools:
     * - scrape_as_markdown: Scrape webpage as markdown (use this for reading articles)
     * - search_engine: Web search (Google, Bing, Yandex)
     * - scrape_batch: Batch scraping (up to 10 URLs)
     * - scraping_browser_*: Browser automation tools
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            val repo = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("BrightData MCP client not initialized. Call initialize() first.")
            )
            
            val result = repo.listTools()
            if (result.isSuccess) {
                val tools = result.getOrNull() ?: emptyList()
                Log.d(tag, "Found ${tools.size} tools: ${tools.map { it.name }}")
                Result.success(tools)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error listing tools"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception listing tools", e)
            Result.failure(e)
        }
    }
    
    /**
     * Reads an article from a URL using scrape_as_markdown tool
     * 
     * According to BrightData MCP documentation, the correct tool is `scrape_as_markdown`
     * which scrapes webpage content and returns it as markdown.
     * 
     * @param url The URL of the article to read (required)
     * @return Result containing the article content as markdown
     */
    suspend fun readArticle(url: String): Result<McpToolResult> = withContext(Dispatchers.IO) {
        try {
            val repo = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("BrightData MCP client not initialized. Call initialize() first.")
            )
            
            Log.d(tag, "Reading article from URL: $url")
            
            val arguments = mapOf<String, Any>(
                "url" to url
            )
            
            // Use scrape_as_markdown as the primary tool (correct tool name per BrightData MCP docs)
            // Fallback to other possible tool names for compatibility
            val toolNames = listOf(
                "scrape_as_markdown",  // Primary tool - correct name per BrightData MCP docs
                "scrape_url",
                "get_url_content",
                "fetch_article"
            )
            var lastError: Exception? = null
            
            for (toolName in toolNames) {
                try {
                    Log.d(tag, "Attempting to call tool: $toolName with URL: $url")
                    val result = repo.callTool(toolName, arguments)
                    if (result.isSuccess) {
                        val toolResult = result.getOrNull()
                        if (toolResult?.isError == true) {
                            val errorText = toolResult.content.firstOrNull()?.text ?: "Unknown error"
                            Log.e(tag, "Tool $toolName returned error: $errorText")
                            lastError = Exception("Tool returned error: $errorText")
                            continue
                        }
                        Log.d(tag, "Article read successfully using tool: $toolName")
                        return@withContext Result.success(toolResult ?: McpToolResult(emptyList(), isError = true))
                    } else {
                        lastError = result.exceptionOrNull() as? Exception ?: Exception("Tool call failed")
                        Log.w(tag, "Tool $toolName failed: ${lastError?.message}, trying next...")
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.e(tag, "Exception calling tool $toolName: ${e.message}", e)
                }
            }
            
            // If all tools failed, return the last error
            Log.e(tag, "All article reading tools failed. Make sure 'scrape_as_markdown' tool is available.")
            Result.failure(lastError ?: Exception("No suitable tool found for reading articles. Expected 'scrape_as_markdown'."))
        } catch (e: Exception) {
            Log.e(tag, "Exception reading article", e)
            Result.failure(e)
        }
    }
    
    /**
     * Scrapes a webpage as markdown (convenience method)
     * 
     * This is a convenience wrapper around callTool("scrape_as_markdown", ...)
     * 
     * @param url The URL to scrape
     * @return Result containing the markdown content
     */
    suspend fun scrapeAsMarkdown(url: String): Result<McpToolResult> {
        return callTool(
            toolName = "scrape_as_markdown",
            arguments = mapOf("url" to url)
        )
    }
    
    /**
     * Calls a tool with the given arguments
     * 
     * Available tools:
     * - scrape_as_markdown: Scrape webpage as markdown (use for reading articles)
     * - search_engine: Web search (Google, Bing, Yandex)
     * - scrape_batch: Batch scraping (up to 10 URLs)
     * - scraping_browser_*: Browser automation tools
     * 
     * @param toolName Name of the tool to call
     * @param arguments Map of arguments for the tool
     * @return Result containing the tool result
     */
    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>
    ): Result<McpToolResult> = withContext(Dispatchers.IO) {
        try {
            val repo = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("BrightData MCP client not initialized. Call initialize() first.")
            )
            
            Log.d(tag, "Calling tool: $toolName with arguments: $arguments")
            
            val result = repo.callTool(toolName, arguments)
            if (result.isSuccess) {
                Log.d(tag, "Tool executed successfully")
                Result.success(result.getOrNull() ?: McpToolResult(emptyList(), isError = true))
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error calling tool")
                Log.e(tag, "Tool call failed", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception calling tool", e)
            Result.failure(e)
        }
    }
    
    /**
     * Checks if the client is connected
     */
    fun isConnected(): Boolean {
        return mcpRepository?.isConnected() == true
    }
    
    /**
     * Disconnects from the BrightData MCP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            mcpRepository?.disconnect()
            mcpRepository = null
            Log.d(tag, "BrightData MCP client disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error during disconnect", e)
        }
    }
}


