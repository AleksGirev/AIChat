package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity for storing background sync updates.
 * Contains structured data fetched from MCP server and processed by LLM.
 * 
 * Architecture Decision: Single entity for sync updates
 * - Stores LLM-processed structured data (events, metrics, updates)
 * - Includes metadata for notification aggregation
 * - Supports query by time range for hourly notification summaries
 */
@Entity(tableName = "sync_updates")
@TypeConverters(StringListConverter::class)
data class SyncUpdateEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * Type of update: "event", "metric", "update", "alert"
     * Used for categorizing and filtering notifications
     */
    val type: String,
    
    /**
     * Priority level: 1 (low) to 5 (critical)
     * Determines notification importance and display order
     */
    val priority: Int,
    
    /**
     * Short title for notification display
     */
    val title: String,
    
    /**
     * Full content/description from LLM processing
     */
    val content: String,
    
    /**
     * Raw JSON data from MCP server (before LLM processing)
     * Stored for debugging and potential reprocessing
     */
    val rawMcpData: String? = null,
    
    /**
     * Source MCP tool that provided the data
     */
    val sourceTool: String? = null,
    
    /**
     * Timestamp when the update was synced
     */
    val syncTimestamp: Long,
    
    /**
     * Whether this update has been shown in a notification
     */
    val notified: Boolean = false,
    
    /**
     * Timestamp when notification was sent (null if not yet notified)
     */
    val notifiedTimestamp: Long? = null,
    
    /**
     * Whether user has seen/acknowledged this update
     */
    val seen: Boolean = false,
    
    /**
     * Additional metadata as JSON string (e.g., tool arguments, LLM model used)
     */
    val metadata: String? = null,
    
    /**
     * List of related entity IDs (for linking updates)
     */
    val relatedIds: List<String> = emptyList()
)

/**
 * Represents the sync status stored in SharedPreferences
 */
data class SyncStatus(
    val lastSyncTimestamp: Long,
    val lastSyncSuccess: Boolean,
    val errorMessage: String? = null,
    val updateCount: Int = 0
)

/**
 * Structured response from LLM after processing MCP data
 * Used for parsing LLM output into SyncUpdateEntity records
 */
data class LlmSyncResponse(
    val updates: List<ParsedUpdate>,
    val summary: String? = null
)

data class ParsedUpdate(
    val type: String,
    val priority: Int,
    val title: String,
    val content: String,
    val sourceTool: String? = null,
    val metadata: Map<String, Any>? = null
)

