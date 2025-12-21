package com.example.aichat.console.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * MCP JSON-RPC 2.0 Request model
 * Specification: https://modelcontextprotocol.io/docs/specification/jsonrpc
 */
@Serializable
data class McpRequest(
    @SerialName("jsonrpc")
    val jsonrpc: String = "2.0",
    val id: String? = null, // null for notifications
    val method: String,
    val params: JsonObject? = null
)

/**
 * MCP JSON-RPC 2.0 Response model
 */
@Serializable
data class McpResponse(
    @SerialName("jsonrpc")
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: JsonObject? = null,
    val error: McpError? = null
)

/**
 * MCP Error model
 */
@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonObject? = null
)

/**
 * MCP Tool definition
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerialName("inputSchema")
    val inputSchema: ToolInputSchema
)

/**
 * Tool input schema (JSON Schema)
 */
@Serializable
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null
)

/**
 * Schema property definition
 */
@Serializable
data class SchemaProperty(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

/**
 * Tool call result
 */
@Serializable
data class McpToolResult(
    val content: List<ToolResultContent>,
    @SerialName("isError")
    val isError: Boolean = false
)

/**
 * Tool result content
 */
@Serializable
data class ToolResultContent(
    val type: String, // "text" | "image" | "resource"
    val text: String? = null,
    val data: String? = null, // base64 encoded for images
    @SerialName("mimeType")
    val mimeType: String? = null
)
