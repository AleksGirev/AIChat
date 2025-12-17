package com.example.aichat.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aichat.data.local.SyncUpdateRepository
import com.example.aichat.data.local.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hourly Worker for aggregating and displaying notification summaries.
 * 
 * Execution Flow:
 * 1. Check if notifications are enabled
 * 2. Query unnotified updates from the last hour
 * 3. Build and show summary notification
 * 4. Mark updates as notified
 * 
 * Scheduling Strategy:
 * - Runs every 60 minutes via PeriodicWorkRequest
 * - Uses flex interval to allow WorkManager optimization
 * - Can also be triggered manually from settings
 * 
 * Android Considerations:
 * - Doze mode: WorkManager handles rescheduling
 * - Battery optimization: Low priority, respects system restrictions
 * - No network requirement (works with local data only)
 */
class NotificationSummaryWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val syncUpdateRepository: SyncUpdateRepository,
    private val weatherRepository: WeatherRepository,
    private val notificationManager: SyncNotificationManager
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "NotificationSummaryWorker"
        
        // Work request unique name
        const val WORK_NAME = "notification_summary_work"
        
        // Input data keys
        const val KEY_IS_MANUAL_TRIGGER = "is_manual_trigger"
        const val KEY_TIME_RANGE_HOURS = "time_range_hours"
        
        // Default time range for summary (1 hour)
        private const val DEFAULT_TIME_RANGE_HOURS = 1
        
        // Minimum updates required to show notification
        private const val MIN_UPDATES_FOR_NOTIFICATION = 1
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val isManualTrigger = inputData.getBoolean(KEY_IS_MANUAL_TRIGGER, false)
        val timeRangeHours = inputData.getInt(KEY_TIME_RANGE_HOURS, DEFAULT_TIME_RANGE_HOURS)
        
        Log.d(TAG, "Starting notification summary worker (manual: $isManualTrigger, range: ${timeRangeHours}h)")
        
        try {
            // Check notification permission
            if (!notificationManager.hasNotificationPermission()) {
                Log.w(TAG, "No notification permission, skipping summary")
                return@withContext Result.success()
            }
            
            // Check if sync is enabled (unless manual trigger)
            if (!isManualTrigger && !syncUpdateRepository.isSyncEnabled()) {
                Log.d(TAG, "Sync disabled, skipping notification summary")
                return@withContext Result.success()
            }
            
            // Calculate time range
            val endTime = System.currentTimeMillis()
            val startTime = if (isManualTrigger) {
                // For manual trigger, use last notification timestamp or 24 hours ago
                val lastNotification = syncUpdateRepository.getLastNotificationTimestamp()
                if (lastNotification > 0) {
                    lastNotification
                } else {
                    endTime - (24 * 60 * 60 * 1000L) // 24 hours ago
                }
            } else {
                endTime - (timeRangeHours * 60 * 60 * 1000L)
            }
            
            Log.d(TAG, "Querying updates from $startTime to $endTime")
            
            // Get unnotified updates in time range
            val updates = syncUpdateRepository.getUnnotifiedUpdates()
                .filter { it.syncTimestamp in startTime until endTime }
            
            Log.d(TAG, "Found ${updates.size} unnotified updates")
            
            // Calculate average temperature for Belarusian cities
            val averageTemperature = weatherRepository.getAverageTemperature()
            
            // Show notification even if no updates, if we have weather data
            if (updates.isEmpty() && averageTemperature == null && !isManualTrigger) {
                Log.d(TAG, "No updates or weather data to notify about")
                return@withContext Result.success()
            }
            
            // Show summary notification with Belarus weather summary
            // Show even with 0 updates if we have weather data
            notificationManager.showSummaryNotification(
                updates = updates,
                timeRangeHours = timeRangeHours,
                belarusAverageTemperature = averageTemperature
            )
            
            // Mark updates as notified
            if (updates.isNotEmpty()) {
                val updateIds = updates.map { it.id }
                syncUpdateRepository.markAsNotified(updateIds)
                Log.d(TAG, "Marked ${updateIds.size} updates as notified")
            }
            
            // Update last notification timestamp
            syncUpdateRepository.updateLastNotificationTimestamp(endTime)
            
            Log.d(TAG, "Notification summary completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Notification summary failed", e)
            
            // Don't retry notification failures
            Result.success()
        }
    }
}

