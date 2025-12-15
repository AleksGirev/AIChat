package com.example.aichat.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for OpenAI Chat API
 */
data class ChatRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerializedName("stream")
    val stream: Boolean = false,
    val tools: List<Map<String, Any>>? = null,
    @SerializedName("tool_choice")
    val toolChoice: String? = null // "none", "auto", or function name
)



