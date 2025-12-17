package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for sync updates.
 * Provides all database operations for background sync data.
 * 
 * Design Decision: Flow-based queries for reactive UI updates
 * - getAllUpdates() returns Flow for real-time observation
 * - One-shot queries for Worker operations (suspending functions)
 */
@Dao
interface SyncUpdateDao {
    
    /**
     * Get all sync updates ordered by sync timestamp (most recent first)
     * Returns Flow for reactive UI observation
     */
    @Query("SELECT * FROM sync_updates ORDER BY syncTimestamp DESC")
    fun getAllUpdates(): Flow<List<SyncUpdateEntity>>
    
    /**
     * Get sync update by ID
     */
    @Query("SELECT * FROM sync_updates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncUpdateEntity?
    
    /**
     * Get updates within a time range (for hourly notification aggregation)
     * @param startTime Start of the time range (inclusive)
     * @param endTime End of the time range (exclusive)
     */
    @Query("SELECT * FROM sync_updates WHERE syncTimestamp >= :startTime AND syncTimestamp < :endTime ORDER BY priority DESC, syncTimestamp DESC")
    suspend fun getUpdatesByTimeRange(startTime: Long, endTime: Long): List<SyncUpdateEntity>
    
    /**
     * Get updates that haven't been notified yet
     */
    @Query("SELECT * FROM sync_updates WHERE notified = 0 ORDER BY priority DESC, syncTimestamp DESC")
    suspend fun getUnnotifiedUpdates(): List<SyncUpdateEntity>
    
    /**
     * Get high-priority updates (priority >= 4) that haven't been notified
     */
    @Query("SELECT * FROM sync_updates WHERE notified = 0 AND priority >= 4 ORDER BY priority DESC, syncTimestamp DESC")
    suspend fun getHighPriorityUnnotifiedUpdates(): List<SyncUpdateEntity>
    
    /**
     * Get updates by type
     */
    @Query("SELECT * FROM sync_updates WHERE type = :type ORDER BY syncTimestamp DESC")
    suspend fun getByType(type: String): List<SyncUpdateEntity>
    
    /**
     * Get unseen updates count (for badge display)
     */
    @Query("SELECT COUNT(*) FROM sync_updates WHERE seen = 0")
    fun getUnseenCount(): Flow<Int>
    
    /**
     * Get count of updates since a timestamp
     */
    @Query("SELECT COUNT(*) FROM sync_updates WHERE syncTimestamp >= :timestamp")
    suspend fun getCountSince(timestamp: Long): Int
    
    /**
     * Insert a sync update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(update: SyncUpdateEntity)
    
    /**
     * Insert multiple sync updates
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(updates: List<SyncUpdateEntity>)
    
    /**
     * Update a sync update
     */
    @Update
    suspend fun update(update: SyncUpdateEntity)
    
    /**
     * Mark updates as notified
     * @param ids List of update IDs to mark as notified
     * @param timestamp Timestamp when notification was sent
     */
    @Query("UPDATE sync_updates SET notified = 1, notifiedTimestamp = :timestamp WHERE id IN (:ids)")
    suspend fun markAsNotified(ids: List<String>, timestamp: Long)
    
    /**
     * Mark updates as seen
     */
    @Query("UPDATE sync_updates SET seen = 1 WHERE id IN (:ids)")
    suspend fun markAsSeen(ids: List<String>)
    
    /**
     * Mark all updates as seen
     */
    @Query("UPDATE sync_updates SET seen = 1 WHERE seen = 0")
    suspend fun markAllAsSeen()
    
    /**
     * Delete update by ID
     */
    @Query("DELETE FROM sync_updates WHERE id = :id")
    suspend fun deleteById(id: String)
    
    /**
     * Delete old updates (older than timestamp)
     * Used for cleanup to prevent database bloat
     */
    @Query("DELETE FROM sync_updates WHERE syncTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    /**
     * Delete all updates
     */
    @Query("DELETE FROM sync_updates")
    suspend fun deleteAll()
}

