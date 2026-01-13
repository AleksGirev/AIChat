package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity for external memory storage
 * Stores intermediate results, context, and other persistent data
 */
@Entity(tableName = "external_memory")
@TypeConverters(StringListConverter::class)
data class ExternalMemoryEntity(
    @PrimaryKey
    val id: String,
    val key: String, // Key for quick lookup (e.g., "user_preferences", "conversation_context")
    val content: String, // JSON string with data or plain text
    val category: String, // Category: "context", "fact", "preference", "intermediate_result", "session_summary"
    val timestamp: Long,
    val lastAccessed: Long, // Last access time
    val accessCount: Int = 0, // Number of accesses
    val sessionId: String? = null, // Related session ID
    val relatedMessageIds: List<String> = emptyList() // Related message IDs
)







