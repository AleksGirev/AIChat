package com.example.aichat.data.mcp

import android.util.Log
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.mcp.model.McpToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Repository for MCP operations
 * Provides high-level interface for working with MCP servers
 */
class McpRepository(
    private val mcpClient: McpClient
) {
    
    private val tag = "McpRepository"
    
    /**
     * Initializes connection to MCP server
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        mcpClient.initialize()
    }
    
    /**
     * Lists all available tools from the MCP server
     */
    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        mcpClient.listTools()
    }
    
    /**
     * Calls a tool with given arguments
     */
    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any>? = null
    ): Result<McpToolResult> = withContext(Dispatchers.IO) {
        mcpClient.callTool(toolName, arguments)
    }
    
    /**
     * Calls a tool and returns result as Flow (for streaming support)
     */
    fun callToolStream(
        toolName: String,
        arguments: Map<String, Any>? = null
    ): Flow<Result<McpToolResult>> = flow {
        emit(callTool(toolName, arguments))
    }
    
    /**
     * Checks if MCP client is connected
     */
    fun isConnected(): Boolean = mcpClient.isConnected()
    
    /**
     * Disconnects from MCP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mcpClient.disconnect()
    }
    
    /**
     * Converts MCP tools to OpenAI-compatible format
     * OpenAI API expects tools in a specific format
     */
    fun convertToOpenAiTools(mcpTools: List<McpTool>): List<Map<String, Any>> {
        return mcpTools.map { tool ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to (tool.description ?: ""),
                    "parameters" to mapOf(
                        "type" to tool.inputSchema.type,
                        "properties" to (tool.inputSchema.properties?.mapValues { (_, prop): Map.Entry<String, com.example.aichat.data.mcp.model.SchemaProperty> ->
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
}

