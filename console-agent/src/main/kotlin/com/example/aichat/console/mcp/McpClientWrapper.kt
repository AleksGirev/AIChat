package com.example.aichat.console.mcp

import com.example.aichat.console.model.*
import com.example.aichat.console.util.JsonUtils
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.*
import kotlinx.serialization.json.*
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wrapper around the official Kotlin MCP SDK Client
 * Provides a simplified interface for our use case
 */
class McpClientWrapper(
    private val inputStream: InputStream,
    private val outputStream: OutputStream
) {
    
    private val client: Client by lazy {
        Client(
            clientInfo = Implementation(
                name = "Console-Agent",
                version = "1.0.0"
            ),
            options = ClientOptions()
        )
    }
    
    private val transport: StdioClientTransport by lazy {
        StdioClientTransport(
            input = inputStream.asSource().buffered(),
            output = outputStream.asSink().buffered()
        )
    }
    
    private val isConnectedFlag = AtomicBoolean(false)
    private val toolsCache = mutableMapOf<String, com.example.aichat.console.model.McpTool>()
    
    /**
     * Connects to the MCP server
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            if (isConnectedFlag.get()) {
                println("[MCP]: Already connected")
                return@withContext
            }
            
            println("[MCP]: Connecting to MCP server using official SDK...")
            client.connect(transport)
            isConnectedFlag.set(true)
            
            println("[MCP]: Connected successfully")
            println("[MCP]: Server version: ${client.serverVersion}")
            println("[MCP]: Server capabilities: ${client.serverCapabilities}")
        } catch (e: Exception) {
            println("[MCP]: Failed to connect: ${e.message}")
            isConnectedFlag.set(false)
            throw e
        }
    }
    
    /**
     * Lists available tools from the MCP server
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            if (!isConnectedFlag.get()) {
                return@withContext Result.failure(
                    Exception("Not connected to MCP server")
                )
            }
            
            println("[MCP]: Requesting tools list...")
            val response = client.listTools(ListToolsRequest())
            
            val tools = response.tools.map { tool ->
                // Parse the full input schema from the SDK tool
                val inputSchema = parseInputSchemaFromSdkTool(tool.inputSchema)
                
                val mcpTool = McpTool(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = inputSchema
                )
                
                // Cache the tool for later use when calling it
                toolsCache[tool.name] = mcpTool
                
                mcpTool
            }
            
            println("[MCP]: Found ${tools.size} tools: ${tools.joinToString(", ") { it.name }}")
            Result.success(tools)
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
            if (!isConnectedFlag.get()) {
                return@withContext Result.failure(
                    Exception("Not connected to MCP server")
                )
            }
            
            // Get the tool schema to check for required parameters
            val tool = toolsCache[toolName]
            val toolSchema = tool?.inputSchema
            
            // Normalize arguments: check if tool requires specific parameters
            val normalizedArguments = normalizeToolArguments(toolName, arguments, toolSchema)
            
            println("[MCP]: Calling tool: $toolName with arguments: ${if (normalizedArguments.isEmpty()) "{} (no arguments)" else normalizedArguments}")
            
            // The SDK's callTool can accept Map<String, Any> directly
            // Note: Even for tools with no arguments, we must pass emptyMap() as the SDK requires it
            val response = client.callTool(
                name = toolName,
                arguments = normalizedArguments
            ) ?: return@withContext Result.failure(
                Exception("Tool call returned null response")
            )
            
            // Convert response to our format
            val content = response.content.map { contentItem ->
                when (contentItem) {
                    is io.modelcontextprotocol.kotlin.sdk.types.TextContent -> {
                        ToolResultContent(
                            type = "text",
                            text = contentItem.text
                        )
                    }
                    is io.modelcontextprotocol.kotlin.sdk.types.ImageContent -> {
                        ToolResultContent(
                            type = "image",
                            data = contentItem.data,
                            mimeType = contentItem.mimeType
                        )
                    }
//                    is io.modelcontextprotocol.kotlin.sdk.types.ResourceContent -> {
//                        ToolResultContent(
//                            type = "resource",
//                            text = contentItem.uri,
//                            mimeType = contentItem.mimeType
//                        )
//                    }
                    else -> {
                        ToolResultContent(
                            type = "text",
                            text = contentItem.toString()
                        )
                    }
                }
            }
            
            // Check for error indicators in the response
            val errorText = content.joinToString("\n") { it.text ?: "" }
            val hasError = response.isError == true || 
                          errorText.contains("error", ignoreCase = true) ||
                          errorText.contains("-32602", ignoreCase = true) ||
                          errorText.contains("Invalid arguments", ignoreCase = true)
            
            val result = McpToolResult(
                content = content,
                isError = hasError
            )
            
            // Check if the response indicates an error
            if (hasError) {
                val errorMessage = if (errorText.isNotEmpty() && !errorText.contains("executed successfully", ignoreCase = true)) {
                    "Tool $toolName returned an error: $errorText"
                } else {
                    "Tool $toolName returned an error (code: -32602 - Invalid params). " +
                            "This may indicate that the tool was called with invalid arguments. " +
                            "For tools with no required parameters (like mobile_list_available_devices), " +
                            "the server may reject empty argument objects. This is a known issue with some MCP servers."
                }
                println("[MCP]: $errorMessage")
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            println("[MCP]: Tool $toolName executed successfully")
            Result.success(result)
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
            if (isConnectedFlag.get()) {
                client.close()
                isConnectedFlag.set(false)
                println("[MCP]: Disconnected")
            }
        } catch (e: Exception) {
            println("[MCP]: Error during disconnect: ${e.message}")
        }
    }
    
    /**
     * Checks if client is connected
     */
    fun isConnected(): Boolean = isConnectedFlag.get()
    
    /**
     * Parses input schema from SDK tool's inputSchema
     * The SDK's inputSchema type may not be directly accessible, so we use a simplified approach
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseInputSchemaFromSdkTool(inputSchema: Any?): ToolInputSchema {
        if (inputSchema == null) {
            return ToolInputSchema()
        }
        
        return try {
            // Try to convert the schema to a map/object we can parse
            // The SDK's inputSchema might be a JsonObject or similar structure
            val schemaMap = when (inputSchema) {
                is Map<*, *> -> inputSchema
                is kotlinx.serialization.json.JsonObject -> JsonUtils.jsonObjectToMap(inputSchema)
                else -> {
                    // Try to serialize and parse if it's a serializable object
                    try {
                        val json = kotlinx.serialization.json.Json { 
                            ignoreUnknownKeys = true
                            encodeDefaults = false
                        }
                        // Try to serialize as JSON string and parse back
                        val jsonString = inputSchema.toString()
                        val jsonElement = json.parseToJsonElement(jsonString)
                        if (jsonElement is kotlinx.serialization.json.JsonObject) {
                            JsonUtils.jsonObjectToMap(jsonElement)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            } ?: return ToolInputSchema()
            
            val type = schemaMap["type"] as? String ?: "object"
            val propertiesMap = schemaMap["properties"] as? Map<*, *>
            val requiredList = schemaMap["required"] as? List<*>
            
            val properties = propertiesMap?.mapNotNull { (key, value) ->
                try {
                    val propMap = value as? Map<*, *> ?: return@mapNotNull null
                    val propType = propMap["type"] as? String ?: "string"
                    val description = propMap["description"] as? String
                    
                    key.toString() to SchemaProperty(
                        type = propType,
                        description = description,
                        enum = null
                    )
                } catch (e: Exception) {
                    null
                }
            }?.toMap()
            
            val required = requiredList?.mapNotNull { it as? String }
            
            ToolInputSchema(
                type = type,
                properties = properties,
                required = required
            )
        } catch (e: Exception) {
            // If parsing fails, return basic schema - we'll handle noParams via the workaround
            ToolInputSchema()
        }
    }
    
    /**
     * Normalizes tool arguments based on the tool's schema
     * Handles special cases like tools that require noParams object
     */
    private fun normalizeToolArguments(
        toolName: String,
        arguments: Map<String, Any>?,
        toolSchema: ToolInputSchema?
    ): Map<String, Any> {
        val args = arguments?.toMutableMap() ?: mutableMapOf()
        
        // Check if tool requires noParams and it's missing
        // Some MCP servers (like mobile-mcp) use noParams as a required empty object parameter
        // when the tool has no actual parameters
        val hasNoParams = args.containsKey("noParams")
        
        // If arguments are empty and noParams is not present, add it
        // This handles the case where tools like mobile_list_available_devices require noParams: {}
        // even though they have no logical parameters
        if (args.isEmpty() && !hasNoParams) {
            // Based on the error message, the server expects noParams as an object
            // We'll add it when arguments are empty - the server will validate
            args["noParams"] = emptyMap<String, Any>()
            println("[MCP]: Adding 'noParams' parameter for tool $toolName (required by server for empty arguments)")
        }
        
        return args
    }
    
}
