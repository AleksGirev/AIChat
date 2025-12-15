package com.example.aichat.data.mcp.model

import com.google.gson.annotations.SerializedName

/**
 * MCP JSON-RPC 2.0 Request model
 * Specification: https://modelcontextprotocol.io/docs/specification/jsonrpc
 */
data class McpRequest(
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    val id: String? = null, // null for notifications
    val method: String,
    val params: Map<String, Any>? = null
)

/**
 * MCP JSON-RPC 2.0 Response model
 */
data class McpResponse(
    @SerializedName("jsonrpc")
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: Any? = null,
    val error: McpError? = null
)

/**
 * MCP Error model
 */
data class McpError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

/**
 * MCP Tool definition
 */
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerializedName("inputSchema")
    val inputSchema: ToolInputSchema
)

/**
 * Tool input schema (JSON Schema)
 */
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null
)

/**
 * Schema property definition
 */
data class SchemaProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

/**
 * Tool call request
 */
data class McpToolCall(
    val name: String,
    val arguments: Map<String, Any>? = null
)

/**
 * Tool call result
 */
data class McpToolResult(
    val content: List<ToolResultContent>,
    val isError: Boolean = false
)

/**
 * Tool result content
 */
data class ToolResultContent(
    val type: String, // "text" | "image" | "resource"
    val text: String? = null,
    val data: String? = null, // base64 encoded for images
    val mimeType: String? = null
)

