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
    val stream: Boolean = false
)

