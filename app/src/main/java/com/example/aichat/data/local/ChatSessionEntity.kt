package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a chat session
 * Each session represents a separate conversation thread
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String, // Title of the chat (first message or generated title)
    val createdAt: Long,
    val updatedAt: Long,
    val summary: String? = null, // Summary of previous messages in this session
    val messageCount: Int = 0, // Number of messages in this session
    val lastMessagePreview: String? = null, // Preview of the last message
    val lastSummarizedTimestamp: Long? = null // Timestamp of the last message that was included in summary
)

