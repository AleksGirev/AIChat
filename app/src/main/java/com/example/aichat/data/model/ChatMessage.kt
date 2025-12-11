package com.example.aichat.data.model

/**
 * Represents a single message in a chat conversation
 */
data class ChatMessage(
    val role: String, // "user", "assistant", or "system"
    val content: String
)



