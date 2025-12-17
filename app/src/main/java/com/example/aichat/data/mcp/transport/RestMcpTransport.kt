package com.example.aichat.data.mcp.transport

import android.util.Log
import com.example.aichat.data.mcp.model.McpRequest
import com.example.aichat.data.mcp.model.McpResponse
import com.example.aichat.data.mcp.model.McpTransport
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.mcp.model.McpToolResult
import com.example.aichat.data.mcp.model.ToolResultContent
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REST API-based MCP Transport implementation.
 * 
 * Uses REST endpoints instead of JSON-RPC 2.0:
 * - GET /health - Health check
 * - GET /tools - List available tools
 * - POST /tools/{toolName} - Call a tool with JSON body: {"arguments": {...}}
 * 
 * This transport is compatible with simple REST-based MCP servers.
 */
class RestMcpTransport(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : McpTransport {
    
    private val isConnectedFlag = AtomicBoolean(false)
    private val notificationChannel = Channel<McpRequest>(Channel.UNLIMITED)
    
    private val tag = "RestMcpTransport"
    private val jsonMediaType = "application/json".toMediaType()
    
    companion object {
        private const val ENDPOINT_HEALTH = "/health"
        private const val ENDPOINT_TOOLS = "/tools"
        private const val ENDPOINT_TOOL_PREFIX = "/tools/"
    }
    
    override suspend fun connect() {
        isConnectedFlag.set(true)
        Log.d(tag, "REST transport connecting to $baseUrl")
        
        // Optionally check health endpoint (non-blocking)
        // This helps verify the server is accessible but doesn't fail if it's not
        try {
            val healthUrl = baseUrl.trimEnd('/') + ENDPOINT_HEALTH
            Log.d(tag, "Attempting health check at: $healthUrl")
            
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Log.d(tag, "Health check passed: $body")
            } else {
                Log.w(tag, "Health check returned ${response.code}, but continuing anyway")
            }
        } catch (e: java.net.ConnectException) {
            Log.w(tag, "Cannot connect to MCP server at $baseUrl")
            Log.w(tag, "Troubleshooting:")
            Log.w(tag, "  1. Ensure MCP server is running on port 8000")
            Log.w(tag, "  2. For emulator: Use http://10.0.2.2:8000")
            Log.w(tag, "  3. For physical device: Use http://YOUR_COMPUTER_IP:8000")
            Log.w(tag, "  4. Check firewall allows connections on port 8000")
            // Don't throw - allow connection to be marked as connected
            // The actual request will fail with a better error message
        } catch (e: Exception) {
            Log.w(tag, "Health check failed: ${e.message}, but continuing anyway")
        }
        
        Log.d(tag, "REST transport marked as connected (health check may have failed)")
    }
    
    override suspend fun disconnect() {
        isConnectedFlag.set(false)
        notificationChannel.close()
        Log.d(tag, "REST transport disconnected")
    }
    
    override fun isConnected(): Boolean = isConnectedFlag.get()
    
    /**
     * Sends a REST API request.
     * 
     * For REST transport, we map MCP methods to REST endpoints:
     * - "tools/list" -> GET /tools
     * - "tools/call" -> POST /tools/{toolName}
     * - "initialize" -> No-op (REST doesn't need initialization)
     */
    override suspend fun sendRequest(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) {
            throw IllegalStateException("Not connected to MCP server")
        }
        
        try {
            val method = request.method
            val params = request.params ?: emptyMap()
            
            when {
                // Health check
                method == "health" || method == "ping" -> {
                    val healthUrl = baseUrl.trimEnd('/') + ENDPOINT_HEALTH
                    val httpRequest = buildGetRequest(healthUrl)
                    executeRequest(httpRequest) { body ->
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = mapOf("status" to "ok", "message" to body)
                        )
                    }
                }
                
                // List tools
                method == "tools/list" -> {
                    val toolsUrl = baseUrl.trimEnd('/') + ENDPOINT_TOOLS
                    val httpRequest = buildGetRequest(toolsUrl)
                    executeRequest(httpRequest) { body ->
                        // Parse tools from REST response
                        val tools = parseToolsFromRestResponse(body)
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = mapOf("tools" to tools.map { toolToMap(it) })
                        )
                    }
                }
                
                // Call tool
                method == "tools/call" -> {
                    val toolName = params["name"] as? String
                        ?: throw IllegalArgumentException("Tool name required in params")
                    val arguments = params["arguments"] as? Map<*, *>
                    
                    val toolUrl = baseUrl.trimEnd('/') + ENDPOINT_TOOL_PREFIX + toolName
                    val httpRequest = buildPostRequest(toolUrl, arguments)
                    executeRequest(httpRequest) { body ->
                        // Parse tool result from REST response
                        val toolResult = parseToolResultFromRestResponse(body)
                        McpResponse(
                            jsonrpc = "2.0",
                            id = request.id,
                            result = mapOf(
                                "content" to toolResult.content.map { contentToMap(it) },
                                "isError" to toolResult.isError
                            )
                        )
                    }
                }
                
                // Initialize - REST doesn't need initialization, just return success
                method == "initialize" -> {
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        result = mapOf("initialized" to true)
                    )
                }
                
                // Notifications - REST APIs don't use notifications, so these are no-ops
                method.startsWith("notifications/") -> {
                    Log.d(tag, "REST transport: ignoring notification '$method' (notifications not supported)")
                    McpResponse(
                        jsonrpc = "2.0",
                        id = request.id,
                        result = mapOf("acknowledged" to true)
                    )
                }
                
                else -> {
                    throw IllegalArgumentException("Unsupported REST method: $method")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending REST request", e)
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = com.example.aichat.data.mcp.model.McpError(
                    code = -32603,
                    message = e.message ?: "Internal error",
                    data = null
                )
            )
        }
    }
    
    /**
     * Builds a GET request
     */
    private fun buildGetRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "AIChat-Android/1.0")
        
        if (apiKey != null && apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        
        return builder.build()
    }
    
    /**
     * Builds a POST request with JSON body
     */
    private fun buildPostRequest(url: String, arguments: Map<*, *>?): Request {
        val requestBody = JsonObject().apply {
            val argsJson = JsonObject()
            arguments?.forEach { (key, value) ->
                when (value) {
                    is String -> argsJson.addProperty(key.toString(), value)
                    is Number -> argsJson.addProperty(key.toString(), value.toDouble())
                    is Boolean -> argsJson.addProperty(key.toString(), value)
                    else -> argsJson.addProperty(key.toString(), value.toString())
                }
            }
            add("arguments", argsJson)
        }
        
        val bodyString = gson.toJson(requestBody)
        val requestBodyObj = bodyString.toRequestBody(jsonMediaType)
        
        val builder = Request.Builder()
            .url(url)
            .post(requestBodyObj)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "AIChat-Android/1.0")
        
        if (apiKey != null && apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        
        Log.d(tag, "POST $url with body: $bodyString")
        return builder.build()
    }
    
    /**
     * Executes HTTP request and parses response
     */
    private suspend fun executeRequest(
        request: Request,
        parser: (String) -> McpResponse
    ): McpResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Executing request: ${request.method} ${request.url}")
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: response.message
                    Log.e(tag, "HTTP error ${response.code}: $errorBody")
                    throw Exception("HTTP error ${response.code}: $errorBody")
                }
                
                val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                Log.d(tag, "REST response: $responseBody")
                
                parser(responseBody)
            }
        } catch (e: java.net.ConnectException) {
            val url = request.url.toString()
            val errorMsg = buildString {
                append("Failed to connect to MCP server at $url\n\n")
                append("Troubleshooting steps:\n")
                append("1. Verify MCP server is running: Check if server is listening on port 8000\n")
                append("2. For Android Emulator: Ensure URL is http://10.0.2.2:8000\n")
                append("3. For Physical Device: Use your computer's IP (e.g., http://192.168.1.XXX:8000)\n")
                append("4. Check firewall: Ensure port 8000 is not blocked\n")
                append("5. Test connection: Try accessing $url from a browser on your computer\n")
                append("6. Network: Ensure device and computer are on the same network")
            }
            Log.e(tag, errorMsg)
            throw Exception(errorMsg, e)
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Connection timeout to MCP server. Server may be slow or unreachable."
            Log.e(tag, errorMsg)
            throw Exception(errorMsg, e)
        } catch (e: Exception) {
            Log.e(tag, "Request failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Parses tools list from REST API response
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseToolsFromRestResponse(json: String): List<McpTool> {
        return try {
            val jsonObj = gson.fromJson(json, Map::class.java) as? Map<*, *>
            
            // Handle different response formats
            val toolsList = when {
                jsonObj?.containsKey("tools") == true -> {
                    jsonObj["tools"] as? List<Map<*, *>>
                }
                jsonObj is List<*> -> {
                    jsonObj as? List<Map<*, *>>
                }
                else -> {
                    emptyList()
                }
            }
            
            toolsList?.mapNotNull { toolMap ->
                try {
                    val name = toolMap["name"] as? String ?: return@mapNotNull null
                    val description = toolMap["description"] as? String
                    val inputSchemaMap = toolMap["inputSchema"] as? Map<*, *>
                    
                    val inputSchema = parseInputSchema(inputSchemaMap)
                    
                    McpTool(
                        name = name,
                        description = description,
                        inputSchema = inputSchema
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse tool", e)
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse tools from REST response", e)
            emptyList()
        }
    }
    
    /**
     * Parses tool result from REST API response
     */
    private fun parseToolResultFromRestResponse(json: String): McpToolResult {
        return try {
            val jsonObj = gson.fromJson(json, Map::class.java) as? Map<*, *>
            
            // Handle different response formats
            val content = when {
                // Direct content field
                jsonObj?.containsKey("content") == true -> {
                    val contentList = jsonObj["content"] as? List<Map<*, *>>
                    contentList?.mapNotNull { parseContentItem(it) } ?: emptyList()
                }
                // Direct text/result field
                jsonObj?.containsKey("text") == true -> {
                    listOf(ToolResultContent(
                        type = "text",
                        text = jsonObj["text"] as? String
                    ))
                }
                jsonObj?.containsKey("result") == true -> {
                    listOf(ToolResultContent(
                        type = "text",
                        text = jsonObj["result"].toString()
                    ))
                }
                // Entire response is the content
                else -> {
                    listOf(ToolResultContent(
                        type = "text",
                        text = json
                    ))
                }
            }
            
            val isError = jsonObj?.get("isError") as? Boolean ?: false
            
            McpToolResult(content, isError)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse tool result from REST response", e)
            McpToolResult(emptyList(), isError = true)
        }
    }
    
    /**
     * Parses content item from map
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseContentItem(contentMap: Map<*, *>): ToolResultContent? {
        return try {
            val type = contentMap["type"] as? String ?: "text"
            val text = contentMap["text"] as? String
            val data = contentMap["data"] as? String
            val mimeType = contentMap["mimeType"] as? String
            
            ToolResultContent(type, text, data, mimeType)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse content item", e)
            null
        }
    }
    
    /**
     * Parses input schema from map
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseInputSchema(schemaMap: Map<*, *>?): com.example.aichat.data.mcp.model.ToolInputSchema {
        if (schemaMap == null) {
            return com.example.aichat.data.mcp.model.ToolInputSchema()
        }
        
        val type = schemaMap["type"] as? String ?: "object"
        val propertiesMap = schemaMap["properties"] as? Map<*, *>
        val requiredList = schemaMap["required"] as? List<*>
        
        val properties = propertiesMap?.mapNotNull { (key, value) ->
            try {
                val propMap = value as? Map<*, *> ?: return@mapNotNull null
                val propType = propMap["type"] as? String ?: "string"
                val description = propMap["description"] as? String
                val enumList = propMap["enum"] as? List<*>
                
                key.toString() to com.example.aichat.data.mcp.model.SchemaProperty(
                    type = propType,
                    description = description,
                    enum = enumList?.mapNotNull { it as? String }
                )
            } catch (e: Exception) {
                null
            }
        }?.toMap()
        
        val required = requiredList?.mapNotNull { it as? String }
        
        return com.example.aichat.data.mcp.model.ToolInputSchema(
            type = type,
            properties = properties,
            required = required
        )
    }
    
    /**
     * Converts tool to map for JSON-RPC compatibility
     */
    private fun toolToMap(tool: McpTool): Map<String, Any> {
        val propertiesMap: Map<String, Any> = tool.inputSchema.properties?.mapValues { (_, prop) ->
            val propMap = mutableMapOf<String, Any>(
                "type" to prop.type,
                "description" to (prop.description ?: "")
            )
            if (prop.enum != null) {
                propMap["enum"] = prop.enum
            }
            propMap
        } ?: emptyMap()
        
        return mapOf(
            "name" to tool.name,
            "description" to (tool.description ?: ""),
            "inputSchema" to mapOf(
                "type" to tool.inputSchema.type,
                "properties" to propertiesMap,
                "required" to (tool.inputSchema.required ?: emptyList())
            )
        )
    }
    
    /**
     * Converts content to map for JSON-RPC compatibility
     */
    private fun contentToMap(content: ToolResultContent): Map<String, Any> {
        return mapOf(
            "type" to content.type,
            "text" to (content.text ?: ""),
            "data" to (content.data ?: ""),
            "mimeType" to (content.mimeType ?: "")
        )
    }
    
    override fun sendRequestStream(request: McpRequest): Flow<McpResponse> = flow {
        emit(sendRequest(request))
    }
    
    override fun receiveNotifications(): Flow<McpRequest> = notificationChannel.receiveAsFlow()
}

