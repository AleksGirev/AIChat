package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity representing a chat message stored in the local database
 */
@Entity(tableName = "chat_messages")
@TypeConverters(StringListConverter::class)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val isUser: Boolean, // true for user messages, false for assistant messages
    val timestamp: Long,
    val requestTokens: Int? = null,
    val responseTokens: Int? = null,
    val totalTokens: Int? = null,
    val isSummary: Boolean = false, // true if this message is a summary of previous messages
    val compressedMessageIds: List<String> = emptyList() // IDs of messages that were compressed into this summary
)
