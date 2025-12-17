package com.example.aichat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for managing sync updates and sync status.
 * Provides a clean API for accessing sync data from Workers and UI.
 * 
 * Architecture Decision: Repository pattern with encrypted preferences
 * - SyncUpdateDao for structured sync data (Room)
 * - EncryptedSharedPreferences for sync status/metadata (sensitive timing info)
 * - Separated concerns for testability (can mock DAO in tests)
 */
class SyncUpdateRepository(
    private val syncUpdateDao: SyncUpdateDao,
    context: Context,
    private val gson: Gson
) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val syncPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "sync_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_LAST_SYNC_SUCCESS = "last_sync_success"
        private const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
        private const val KEY_LAST_NOTIFICATION_TIMESTAMP = "last_notification_timestamp"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        
        // Cleanup: delete updates older than 7 days
        private const val CLEANUP_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L
    }
    
    // ==================== Sync Updates ====================
    
    /**
     * Get all sync updates as Flow for reactive UI
     */
    fun getAllUpdates(): Flow<List<SyncUpdateEntity>> = syncUpdateDao.getAllUpdates()
    
    /**
     * Get updates within time range (for notification summary)
     */
    suspend fun getUpdatesByTimeRange(startTime: Long, endTime: Long): List<SyncUpdateEntity> =
        withContext(Dispatchers.IO) {
            syncUpdateDao.getUpdatesByTimeRange(startTime, endTime)
        }
    
    /**
     * Get unnotified updates
     */
    suspend fun getUnnotifiedUpdates(): List<SyncUpdateEntity> =
        withContext(Dispatchers.IO) {
            syncUpdateDao.getUnnotifiedUpdates()
        }
    
    /**
     * Get high priority unnotified updates (for immediate notification)
     */
    suspend fun getHighPriorityUnnotifiedUpdates(): List<SyncUpdateEntity> =
        withContext(Dispatchers.IO) {
            syncUpdateDao.getHighPriorityUnnotifiedUpdates()
        }
    
    /**
     * Get count of updates since timestamp
     */
    suspend fun getCountSince(timestamp: Long): Int =
        withContext(Dispatchers.IO) {
            syncUpdateDao.getCountSince(timestamp)
        }
    
    /**
     * Get unseen count as Flow
     */
    fun getUnseenCount(): Flow<Int> = syncUpdateDao.getUnseenCount()
    
    /**
     * Save sync updates from LLM processing
     */
    suspend fun saveUpdates(updates: List<ParsedUpdate>, rawMcpData: String? = null) =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val entities = updates.map { parsed ->
                SyncUpdateEntity(
                    id = UUID.randomUUID().toString(),
                    type = parsed.type,
                    priority = parsed.priority,
                    title = parsed.title,
                    content = parsed.content,
                    rawMcpData = rawMcpData,
                    sourceTool = parsed.sourceTool,
                    syncTimestamp = timestamp,
                    metadata = parsed.metadata?.let { gson.toJson(it) }
                )
            }
            syncUpdateDao.insertAll(entities)
        }
    
    /**
     * Mark updates as notified
     */
    suspend fun markAsNotified(updateIds: List<String>) =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            syncUpdateDao.markAsNotified(updateIds, timestamp)
        }
    
    /**
     * Mark updates as seen
     */
    suspend fun markAsSeen(updateIds: List<String>) =
        withContext(Dispatchers.IO) {
            syncUpdateDao.markAsSeen(updateIds)
        }
    
    /**
     * Mark all updates as seen
     */
    suspend fun markAllAsSeen() =
        withContext(Dispatchers.IO) {
            syncUpdateDao.markAllAsSeen()
        }
    
    /**
     * Clean up old updates (call periodically from Worker)
     */
    suspend fun cleanupOldUpdates() =
        withContext(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - CLEANUP_THRESHOLD_MS
            syncUpdateDao.deleteOlderThan(threshold)
        }
    
    /**
     * Delete all updates
     */
    suspend fun deleteAllUpdates() =
        withContext(Dispatchers.IO) {
            syncUpdateDao.deleteAll()
        }
    
    // ==================== Sync Status ====================
    
    /**
     * Get last successful sync timestamp
     */
    fun getLastSyncTimestamp(): Long =
        syncPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    
    /**
     * Update sync status after a sync operation
     */
    fun updateSyncStatus(success: Boolean, errorMessage: String? = null) {
        syncPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
            .putBoolean(KEY_LAST_SYNC_SUCCESS, success)
            .putString(KEY_LAST_ERROR_MESSAGE, errorMessage)
            .apply()
    }
    
    /**
     * Get last sync status
     */
    fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            lastSyncTimestamp = syncPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L),
            lastSyncSuccess = syncPreferences.getBoolean(KEY_LAST_SYNC_SUCCESS, true),
            errorMessage = syncPreferences.getString(KEY_LAST_ERROR_MESSAGE, null)
        )
    }
    
    /**
     * Get last notification timestamp
     */
    fun getLastNotificationTimestamp(): Long =
        syncPreferences.getLong(KEY_LAST_NOTIFICATION_TIMESTAMP, 0L)
    
    /**
     * Update last notification timestamp
     */
    fun updateLastNotificationTimestamp(timestamp: Long = System.currentTimeMillis()) {
        syncPreferences.edit()
            .putLong(KEY_LAST_NOTIFICATION_TIMESTAMP, timestamp)
            .apply()
    }
    
    /**
     * Check if background sync is enabled
     */
    fun isSyncEnabled(): Boolean =
        syncPreferences.getBoolean(KEY_SYNC_ENABLED, true)
    
    /**
     * Enable or disable background sync
     */
    fun setSyncEnabled(enabled: Boolean) {
        syncPreferences.edit()
            .putBoolean(KEY_SYNC_ENABLED, enabled)
            .apply()
    }
}

