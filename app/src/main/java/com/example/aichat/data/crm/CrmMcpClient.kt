package com.example.aichat.data.crm

import android.util.Log
import com.example.aichat.data.mcp.McpClient
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.mcp.model.McpToolResult
import com.example.aichat.data.mcp.transport.StdioMcpTransport
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MCP Client for CRM MCP server.
 * 
 * This client handles communication with CRM MCP server for accessing user tickets and user information.
 * 
 * Architecture:
 * - Uses stdio transport (runs as a local process)
 * - For Android, requires a REST bridge server (similar to BrightData)
 * - Alternatively, can be accessed via HTTP bridge if server is wrapped
 * 
 * Setup:
 * - CRM MCP server runs as: java -jar crm-mcp-server.jar
 * - For Android, wrap in HTTP bridge server
 * - Configure CRM_MCP_BRIDGE_URL in Config.kt (e.g., http://10.0.2.2:8003)
 * 
 * MCP Tools:
 *   - crm.getUserTickets - Get all tickets for a user
 *   - crm.getTicket - Get specific ticket details
 *   - crm.getUserInfo - Get user information
 *   - crm.searchTickets - Search tickets by query
 */
class CrmMcpClient(
    private val gson: Gson,
    private val httpClient: okhttp3.OkHttpClient? = null,
    private val useRestBridge: Boolean = false,
    private val bridgeUrl: String? = null,
    private val command: List<String> = listOf("java", "-jar", "crm-mcp-server.jar")
) {
    private val tag = "CrmMcpClient"
    private var mcpRepository: McpRepository? = null
    
    /**
     * Initialize CRM MCP client
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("GIREV", "=== CrmMcpClient: initialize ===")
            Log.d("GIREV", "useRestBridge: $useRestBridge, bridgeUrl: $bridgeUrl")
            
            val transport = if (useRestBridge) {
                // Use REST bridge (recommended for Android)
                val url = bridgeUrl ?: run {
                    val error = IllegalArgumentException(
                        "CRM_MCP_BRIDGE_URL is required when using REST bridge. " +
                        "Set it in Config.kt (e.g., http://10.0.2.2:8003)"
                    )
                    Log.e("GIREV", "✗ CRM bridge URL not configured")
                    Log.e(tag, "CRM bridge URL not configured", error)
                    return@withContext Result.failure(error)
                }
                
                Log.d("GIREV", "Initializing CRM MCP client via REST bridge: $url")
                Log.d(tag, "Initializing CRM MCP client via REST bridge: $url")
                
                val httpClientInstance = httpClient ?: run {
                    val error = IllegalStateException("OkHttpClient required for REST bridge")
                    Log.e("GIREV", "✗ OkHttpClient is null")
                    throw error
                }
                
                com.example.aichat.data.mcp.transport.RestMcpTransport(
                    baseUrl = url,
                    apiKey = null, // CRM server doesn't require API key
                    httpClient = httpClientInstance,
                    gson = gson
                )
            } else {
                // Use stdio transport (requires Java process)
                Log.d("GIREV", "Initializing CRM MCP client via stdio: ${command.joinToString(" ")}")
                Log.d(tag, "Initializing CRM MCP client via stdio: ${command.joinToString(" ")}")
                
                StdioMcpTransport(
                    command = command,
                    gson = gson,
                    environment = emptyMap()
                )
            }
            
            Log.d("GIREV", "Creating McpClient and McpRepository...")
            val mcpClient = McpClient(transport)
            mcpRepository = McpRepository(mcpClient)
            
            Log.d("GIREV", "Calling mcpRepository.initialize()...")
            val initResult = mcpRepository?.initialize()
            
            if (initResult?.isSuccess == true) {
                Log.d("GIREV", "✓ CRM MCP client initialized successfully")
                Log.d(tag, "CRM MCP client initialized successfully")
                Result.success(Unit)
            } else {
                val error = initResult?.exceptionOrNull() ?: Exception("Failed to initialize CRM MCP client")
                Log.e("GIREV", "✗ Failed to initialize CRM MCP client: ${error.message}", error)
                Log.e(tag, "Failed to initialize CRM MCP client", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e("GIREV", "✗ Exception initializing CRM MCP client: ${e.message}", e)
            Log.e(tag, "Error initializing CRM MCP client", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all tickets for a user
     */
    suspend fun getUserTickets(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("GIREV", "=== CrmMcpClient: getUserTickets ===")
            Log.d("GIREV", "UserId: $userId")
            
            val repository = mcpRepository ?: run {
                Log.e("GIREV", "✗ CRM MCP client not initialized. Call initialize() first.")
                return@withContext Result.failure(
                    IllegalStateException("CRM MCP client not initialized. Call initialize() first.")
                )
            }
            
            Log.d("GIREV", "Calling tool: crm.getUserTickets")
            val result = repository.callTool("crm.getUserTickets", mapOf("userId" to userId))
            
            if (result.isSuccess) {
                val toolResult = result.getOrThrow()
                val ticketsJson = extractTextContent(toolResult)
                Log.d("GIREV", "✓ getUserTickets successful: ${ticketsJson.length} chars")
                Result.success(ticketsJson)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Failed to get user tickets")
                Log.e("GIREV", "✗ getUserTickets failed: ${error.message}", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e("GIREV", "✗ Exception in getUserTickets: ${e.message}", e)
            Log.e(tag, "Error getting user tickets", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get a specific ticket by ID
     */
    suspend fun getTicket(ticketId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val repository = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("CRM MCP client not initialized. Call initialize() first.")
            )
            
            val result = repository.callTool("crm.getTicket", mapOf("ticketId" to ticketId))
            if (result.isSuccess) {
                val toolResult = result.getOrThrow()
                val ticketJson = extractTextContent(toolResult)
                Result.success(ticketJson)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to get ticket"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting ticket", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user information
     */
    suspend fun getUserInfo(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val repository = mcpRepository ?: return@withContext Result.failure(
                IllegalStateException("CRM MCP client not initialized. Call initialize() first.")
            )
            
            val result = repository.callTool("crm.getUserInfo", mapOf("userId" to userId))
            if (result.isSuccess) {
                val toolResult = result.getOrThrow()
                val userJson = extractTextContent(toolResult)
                Result.success(userJson)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to get user info"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting user info", e)
            Result.failure(e)
        }
    }
    
    /**
     * Search tickets by query
     */
    suspend fun searchTickets(query: String, userId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("GIREV", "=== CrmMcpClient: searchTickets ===")
            Log.d("GIREV", "Query: $query, UserId: $userId")
            
            val repository = mcpRepository ?: run {
                Log.e("GIREV", "✗ CRM MCP client not initialized. Call initialize() first.")
                return@withContext Result.failure(
                    IllegalStateException("CRM MCP client not initialized. Call initialize() first.")
                )
            }
            
            val arguments = mutableMapOf<String, Any>("query" to query)
            if (userId != null) {
                arguments["userId"] = userId
            }
            
            Log.d("GIREV", "Calling tool: crm.searchTickets with args: $arguments")
            val result = repository.callTool("crm.searchTickets", arguments)
            
            if (result.isSuccess) {
                val toolResult = result.getOrThrow()
                val ticketsJson = extractTextContent(toolResult)
                Log.d("GIREV", "✓ searchTickets successful: ${ticketsJson.length} chars")
                Result.success(ticketsJson)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Failed to search tickets")
                Log.e("GIREV", "✗ searchTickets failed: ${error.message}", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e("GIREV", "✗ Exception in searchTickets: ${e.message}", e)
            Log.e(tag, "Error searching tickets", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if client is initialized and connected
     */
    fun isConnected(): Boolean {
        val connected = mcpRepository?.isConnected() == true
        Log.d("GIREV", "CrmMcpClient.isConnected() = $connected (mcpRepository is ${if (mcpRepository != null) "not null" else "null"})")
        return connected
    }
    
    /**
     * Extract text content from MCP tool result
     */
    private fun extractTextContent(toolResult: McpToolResult): String {
        return toolResult.content.firstOrNull()?.text ?: ""
    }
}
