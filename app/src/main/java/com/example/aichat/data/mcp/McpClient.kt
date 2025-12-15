package com.example.aichat.data.mcp

import android.util.Log
import com.example.aichat.data.mcp.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * MCP Client for communicating with MCP servers
 * Handles initialization, tool discovery, and tool execution
 */
class McpClient(
    private val transport: McpTransport
) {
    
    private val tag = "McpClient"
    private var initialized = false
    
    /**
     * Initializes the MCP connection
     * Sends initialize request and handles handshake
     */
    suspend fun initialize(
        protocolVersion: String = "2024-11-05",
        clientName: String = "AIChat-Android",
        clientVersion: String = "1.0.0",
        skipIfNotSupported: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d("GIREV", "try to init mcpClient")
        try {
            if (!transport.isConnected()) {
                transport.connect()
            }
            
            // Build initialize request according to MCP spec
            val initializeRequest = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to protocolVersion,
                    "capabilities" to mapOf<String, Any>(
                        "roots" to mapOf<String, Any>(
                            "listChanged" to true
                        ),
                        "sampling" to mapOf<String, Any>()
                    ),
                    "clientInfo" to mapOf<String, Any>(
                        "name" to clientName,
                        "version" to clientVersion
                    )
                )
            )
            
            Log.d(tag, "Sending initialize request with protocolVersion: $protocolVersion")
            
            val response = transport.sendRequest(initializeRequest)
            
            if (response.error != null) {
                val errorCode = response.error.code
                val errorMessage = response.error.message
                
                Log.d("GIREV", "init error ${response.error.message} (code: $errorCode)")
                
                // If method not found and skipIfNotSupported is true, skip initialization
                if (skipIfNotSupported && errorCode == -32601) {
                    Log.d(tag, "Server doesn't support initialize method, skipping initialization")
                    initialized = true // Mark as initialized anyway
                    return@withContext Result.success(Unit)
                }
                
                return@withContext Result.failure(
                    Exception("Initialize error: $errorMessage (code: $errorCode)")
                )
            }
            
            // Send initialized notification
            val initializedNotification = McpRequest(
                id = null, // Notification
                method = "notifications/initialized"
            )
            
            // For notifications, we don't wait for response
            // But HTTP transport requires a request, so we'll send it anyway
            try {
                transport.sendRequest(initializedNotification)
            } catch (e: Exception) {
                // Some transports may not support notifications
                Log.d(tag, "Initialized notification not supported or failed", e)
            }
            
            initialized = true
            Log.d(tag, "MCP client initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize MCP client", e)
            Result.failure(e)
        }
    }
    
    /**
     * Lists available tools from the MCP server
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            // Try to initialize if not already initialized
            // But don't fail if initialization is not supported
            if (!initialized) {
                initialize(skipIfNotSupported = true).getOrElse { error ->
                    // If initialization fails, continue anyway (skipIfNotSupported should handle -32601)
                    Log.d(tag, "Initialize failed, continuing anyway: ${error.message}")
                    initialized = true // Mark as initialized to avoid retrying
                }
            }
            
            val request = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/list"
            )
            
            val response = transport.sendRequest(request)
            
            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("List tools error: ${response.error.message}")
                )
            }
            
            // Parse response
            val toolsList = parseToolsList(response.result)
            Log.d(tag, "Found ${toolsList.size} tools")
            
            Result.success(toolsList)
        } catch (e: Exception) {
            Log.e(tag, "Failed to list tools", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calls a tool with given arguments
     */
    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>? = null
    ): Result<McpToolResult> = withContext(Dispatchers.IO) {
        try {
            // Try to initialize if not already initialized
            // But don't fail if initialization is not supported
            if (!initialized) {
                initialize(skipIfNotSupported = true).getOrElse { error ->
                    // If initialization fails, continue anyway (skipIfNotSupported should handle -32601)
                    Log.d(tag, "Initialize failed, continuing anyway: ${error.message}")
                    initialized = true // Mark as initialized to avoid retrying
                }
            }
            
            val request = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/call",
                params = mapOf(
                    "name" to toolName,
                    "arguments" to (arguments ?: emptyMap<String, Any>())
                )
            )
            
            val response = transport.sendRequest(request)
            
            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("Call tool error: ${response.error.message}")
                )
            }
            
            val toolResult = parseToolResult(response.result)
            Log.d(tag, "Tool $toolName executed successfully")
            
            Result.success(toolResult)
        } catch (e: Exception) {
            Log.e(tag, "Failed to call tool: $toolName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Disconnects from the MCP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            transport.disconnect()
            initialized = false
            Log.d(tag, "MCP client disconnected")
        } catch (e: Exception) {
            Log.e(tag, "Error during disconnect", e)
        }
    }
    
    /**
     * Checks if client is connected
     */
    fun isConnected(): Boolean = transport.isConnected() && initialized
    
    /**
     * Parses tools list from response result
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseToolsList(result: Any?): List<McpTool> {
        if (result == null) return emptyList()
        
        return try {
            val resultMap = result as? Map<*, *>
            val toolsList = resultMap?.get("tools") as? List<Map<*, *>>
            
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
            Log.e(tag, "Failed to parse tools list", e)
            emptyList()
        }
    }
    
    /**
     * Parses input schema from map
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseInputSchema(schemaMap: Map<*, *>?): ToolInputSchema {
        if (schemaMap == null) {
            return ToolInputSchema()
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
                
                key.toString() to SchemaProperty(
                    type = propType,
                    description = description,
                    enum = enumList?.mapNotNull { it as? String }
                )
            } catch (e: Exception) {
                null
            }
        }?.toMap()
        
        val required = requiredList?.mapNotNull { it as? String }
        
        return ToolInputSchema(
            type = type,
            properties = properties,
            required = required
        )
    }
    
    /**
     * Parses tool result from response
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseToolResult(result: Any?): McpToolResult {
        if (result == null) {
            return McpToolResult(emptyList(), isError = true)
        }
        
        return try {
            val resultMap = result as? Map<*, *>
            val contentList = resultMap?.get("content") as? List<Map<*, *>>
            val isError = resultMap?.get("isError") as? Boolean ?: false
            
            val content = contentList?.mapNotNull { contentMap ->
                try {
                    val type = contentMap["type"] as? String ?: "text"
                    val text = contentMap["text"] as? String
                    val data = contentMap["data"] as? String
                    val mimeType = contentMap["mimeType"] as? String
                    
                    ToolResultContent(
                        type = type,
                        text = text,
                        data = data,
                        mimeType = mimeType
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse content item", e)
                    null
                }
            } ?: emptyList()
            
            McpToolResult(content, isError)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse tool result", e)
            McpToolResult(emptyList(), isError = true)
        }
    }
}

