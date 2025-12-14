package com.example.aichat.ui.model

/**
 * UI model for chat messages
 */
data class UiMessage(
    val id: String,
    val content: String,
    val isUser: Boolean, // true for user messages, false for assistant messages
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String, // ID of the chat session this message belongs to
    // Token information
    val requestTokens: Int? = null, // Estimated tokens for this request
    val responseTokens: Int? = null, // Actual tokens in response (from API)
    val totalTokens: Int? = null, // Total tokens (request + response)
    val isSummary: Boolean = false, // true if this message is a summary of previous messages
    val compressedMessageIds: List<String> = emptyList() // IDs of messages that were compressed into this summary
)


