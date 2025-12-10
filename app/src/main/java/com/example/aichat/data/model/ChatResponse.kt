package com.example.aichat.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for OpenAI Chat API
 */
data class ChatResponse(
    val id: String,
    @SerializedName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?,
    // Estimated request tokens (calculated before sending)
    val estimatedRequestTokens: Int? = null
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

