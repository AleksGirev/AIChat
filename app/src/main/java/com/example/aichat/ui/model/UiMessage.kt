package com.example.aichat.ui.model

/**
 * UI model for chat messages
 */
data class UiMessage(
    val id: String,
    val content: String,
    val isUser: Boolean, // true for user messages, false for assistant messages
    val timestamp: Long = System.currentTimeMillis(),
    // Token information
    val requestTokens: Int? = null, // Estimated tokens for this request
    val responseTokens: Int? = null, // Actual tokens in response (from API)
    val totalTokens: Int? = null // Total tokens (request + response)
)


