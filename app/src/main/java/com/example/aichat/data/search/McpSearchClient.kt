package com.example.aichat.data.search

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.mcp.McpClient
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.mcp.model.McpToolResult
import com.example.aichat.data.mcp.transport.RestMcpTransport
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * MCP Client for DuckDuckGo MCP server via HTTP bridge.
 * 
 * This client handles communication with a DuckDuckGo MCP HTTP bridge server
 * that wraps the DuckDuckGo MCP server and exposes it via REST API.
 * 
 * Architecture:
 * - Connects to HTTP bridge server (FastAPI + uvicorn)
 * - HTTP bridge wraps DuckDuckGo MCP server (stdio-based)
 * - Uses REST transport for HTTP communication
 * - Provides search tool execution (search_text, search_images, search_videos)
 * 
 * Server Setup:
 * - Start HTTP bridge: python mcp_http_bridge.py --port 8000 --host 0.0.0.0
 * - For emulator: Use http://10.0.2.2:8000
 * - For physical device: Use http://YOUR_COMPUTER_IP:8000
 * 
 * API Endpoints:
 * - GET /health - Health check
 * - GET /tools - List available tools
 * - POST /tools/{toolName} - Call tool with {"arguments": {...}}
 * 
 * Error Handling:
 * - Network failures are caught and wrapped in Result
 * - Timeouts configured for production reliability
 * - Retry logic handled at repository level
 */
class McpSearchClient(
    private val baseUrl: String = Config.LOBEHUB_DUCKDUCKGO_MCP_URL,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    
    private val tag = "McpSearchClient"
    private var mcpRepository: McpRepository? = null
    
    companion object {
        // Timeout configurations for production reliability
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 60L
    }
    
    /**
     * Creates an OkHttpClient with appropriate timeouts for MCP search operations
     */
    fun createHttpClient(): OkHttpClient {
        return httpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Initializes connection to the MCP server
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Initializing MCP search client to $baseUrl")
            
            val transport = RestMcpTransport(
                baseUrl = baseUrl,
                apiKey = null, // LobeHub MCP is public, no auth required
                httpClient = createHttpClient(),
                gson = gson
            )
            
            val mcpClient = McpClient(transport)
            mcpRepository = McpRepository(mcpClient)
            
            val initResult = mcpRepository?.initialize()
            if (initResult?.isSuccess == true) {
                Log.d(tag, "MCP search client initialized successfully")
                Result.success(Unit)
            } else {
                val error = initResult?.exceptionOrNull() ?: Exception("Unknown initialization error")
                Log.e(tag, "Failed to initialize MCP search client", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during MCP search client initialization", e)
            Result.failure(e)
        }
    }
    
    /**
     * Lists available tools from the MCP server
     * Expected tools: search_text, search_images, search_videos
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            val repo = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("MCP client not initialized. Call initialize() first.")
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
     * Calls a search tool with the given query
     * 
     * @param toolName Name of the tool (e.g., "search_text", "search_images", "search_videos")
     * @param query Search query string (required)
     * @param region Search region (default: "ru-ru")
     * @param safesearch Safe search level: "on", "moderate", "off" (default: "moderate")
     * @param timelimit Time limit filter: "w" for week, "m" for month, "y" for year, etc. (default: "w")
     * @param maxResults Maximum number of results (default: 10)
     * @param page Page number for pagination (default: 1)
     * @param backend Search backend: "google", "duckduckgo", etc. (default: "google")
     * @return Result containing the tool result
     */
    suspend fun callSearchTool(
        toolName: String,
        query: String,
        region: String = "ru-ru",
        safesearch: String = "moderate",
        timelimit: String = "w",
        maxResults: Int = 3,
        page: Int = 1,
        backend: String = "google"
    ): Result<McpToolResult> = withContext(Dispatchers.IO) {
        try {
            val repo = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("MCP client not initialized. Call initialize() first.")
            )
            
            Log.d(tag, "Calling search tool: $toolName with query: $query, region: $region, maxResults: $maxResults, timelimit: $timelimit, backend: $backend")
            
            val arguments = mapOf<String, Any>(
                "query" to query,
                "region" to region,
                "safesearch" to safesearch,
                "timelimit" to timelimit,
                "max_results" to maxResults,
                "page" to page,
                "backend" to backend
            )
            
            val result = repo.callTool(toolName, arguments)
            if (result.isSuccess) {
                Log.d(tag, "Search tool executed successfully")
                Result.success(result.getOrNull() ?: McpToolResult(emptyList(), isError = true))
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error calling tool")
                Log.e(tag, "Search tool failed", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception calling search tool", e)
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
     * Disconnects from the MCP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            mcpRepository?.disconnect()
            mcpRepository = null
            Log.d(tag, "MCP search client disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error during disconnect", e)
        }
    }
}

