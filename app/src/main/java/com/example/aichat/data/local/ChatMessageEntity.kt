package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a chat message stored in the local database
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val isUser: Boolean, // true for user messages, false for assistant messages
    val timestamp: Long,
    val requestTokens: Int? = null,
    val responseTokens: Int? = null,
    val totalTokens: Int? = null
)
