package com.example.aichat.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single message in a chat conversation
 */
data class ChatMessage(
    val role: String, // "user", "assistant", "system", or "tool"
    val content: String? = null, // null when tool_calls is present
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null, // present when assistant wants to call tools
    @SerializedName("tool_call_id")
    val toolCallId: String? = null // present when role is "tool"
)

/**
 * Represents a tool call in a message
 */
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

/**
 * Represents a function call within a tool call
 */
data class FunctionCall(
    val name: String,
    val arguments: String // JSON string
)



