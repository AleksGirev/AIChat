package com.example.aichat.console.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI Chat Completion Request
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

/**
 * Chat message
 */
@Serializable
data class ChatMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

/**
 * Tool definition for OpenAI API
 */
@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

/**
 * Function definition
 */
@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: kotlinx.serialization.json.JsonObject? = null
)

/**
 * Tool call from LLM response
 */
@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

/**
 * Function call details
 */
@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String // JSON string
)

/**
 * OpenAI Chat Completion Response
 */
@Serializable
data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

/**
 * Choice in response
 */
@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * Token usage
 */
@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
