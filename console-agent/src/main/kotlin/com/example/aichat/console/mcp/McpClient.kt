package com.example.aichat.console.mcp

import com.example.aichat.console.model.*
import com.example.aichat.console.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * MCP Client for communicating with MCP servers
 * Handles initialization, tool discovery, and tool execution
 */
class McpClient(
    private val transport: StdioMcpTransport
) {
    
    private var initialized = false
    
    /**
     * Initializes the MCP connection
     * Sends initialize request and handles handshake
     */
    suspend fun initialize(
        protocolVersion: String = "2024-11-05",
        clientName: String = "Console-Agent",
        clientVersion: String = "1.0.0",
        skipIfNotSupported: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!transport.isConnected()) {
                transport.connect()
            }
            
            // Build initialize request according to MCP spec
            val initializeRequest = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = JsonUtils.mapToJsonObject(
                    mapOf(
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
            )
            
            println("[MCP]: Sending initialize request...")
            val response = try {
                transport.sendRequest(initializeRequest)
            } catch (e: Exception) {
                if (skipIfNotSupported) {
                    println("[MCP]: Initialize request failed (${e.message}), but continuing anyway")
                    initialized = true
                    return@withContext Result.success(Unit)
                }
                return@withContext Result.failure(
                    Exception("Failed to send initialize request: ${e.message}")
                )
            }
            
            if (response.error != null) {
                val errorCode = response.error.code
                val errorMessage = response.error.message
                
                // If method not found and skipIfNotSupported is true, skip initialization
                if (skipIfNotSupported && errorCode == -32601) {
                    println("[MCP]: Server doesn't support initialize method, skipping")
                    initialized = true
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
            
            try {
                transport.sendRequest(initializedNotification)
            } catch (e: Exception) {
                // Some transports may not support notifications
                // Ignore
            }
            
            initialized = true
            println("[MCP]: Initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[MCP]: Failed to initialize: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Lists available tools from the MCP server
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            // Try to initialize if not already initialized
            if (!initialized) {
                val initResult = initialize(skipIfNotSupported = true)
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()
                    println("[MCP]: Initialize failed: ${error?.message}")
                    println("[MCP]: Attempting to list tools anyway (server might not require initialization)")
                    initialized = true // Mark as initialized to avoid retrying
                }
            }
            
            println("[MCP]: Requesting tools list...")
            val request = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/list"
            )
            
            val response = try {
                transport.sendRequest(request)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    Exception("Failed to send tools/list request: ${e.message}. " +
                            "Make sure a device is connected and the MCP server is running.")
                )
            }
            
            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("List tools error: ${response.error.message} (code: ${response.error.code})")
                )
            }
            
            // Parse response
            val toolsList = parseToolsList(response.result)
            println("[MCP]: Found ${toolsList.size} tools")
            
            Result.success(toolsList)
        } catch (e: Exception) {
            println("[MCP]: Failed to list tools: ${e.message}")
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
            if (!initialized) {
                initialize(skipIfNotSupported = true).getOrElse { error ->
                    println("[MCP]: Initialize failed, continuing anyway: ${error.message}")
                    initialized = true
                }
            }
            
            val request = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/call",
                params = JsonUtils.mapToJsonObject(
                    mapOf(
                        "name" to toolName,
                        "arguments" to (arguments ?: emptyMap<String, Any>())
                    )
                )
            )
            
            val response = transport.sendRequest(request)
            
            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("Call tool error: ${response.error.message}")
                )
            }
            
            val toolResult = parseToolResult(response.result)
            println("[MCP]: Tool $toolName executed successfully")
            
            Result.success(toolResult)
        } catch (e: Exception) {
            println("[MCP]: Failed to call tool $toolName: ${e.message}")
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
        } catch (e: Exception) {
            println("[MCP]: Error during disconnect: ${e.message}")
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
    private fun parseToolsList(result: JsonObject?): List<McpTool> {
        if (result == null) return emptyList()
        
        return try {
            val resultMap = JsonUtils.jsonObjectToMap(result) ?: return emptyList()
            val toolsList = resultMap["tools"] as? List<Map<*, *>>
            
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
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
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
    private fun parseToolResult(result: JsonObject?): McpToolResult {
        if (result == null) {
            return McpToolResult(emptyList(), isError = true)
        }
        
        return try {
            val resultMap = JsonUtils.jsonObjectToMap(result) ?: return McpToolResult(emptyList(), isError = true)
            val contentList = resultMap["content"] as? List<Map<*, *>>
            val isError = resultMap["isError"] as? Boolean ?: false
            
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
                    null
                }
            } ?: emptyList()
            
            McpToolResult(content, isError)
        } catch (e: Exception) {
            McpToolResult(emptyList(), isError = true)
        }
    }
}
