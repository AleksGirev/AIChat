package com.example.aichat.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Centralized WorkManager scheduler for all background tasks.
 * 
 * Scheduling Strategy:
 * 1. Background Sync (30 min): PeriodicWorkRequest with network constraint
 * 2. Notification Summary (60 min): PeriodicWorkRequest, no network needed
 * 
 * Android Background Execution Considerations:
 * - Uses setBackoffCriteria for retry exponential backoff
 * - Network constraint for sync (CONNECTED)
 * - Battery optimization respected via WorkManager
 * - Doze mode: WorkManager handles deferrals automatically
 * - App Standby: Constraints ensure proper execution timing
 * 
 * Manual Triggers:
 * - One-time work requests for immediate execution
 * - Expedited work for user-initiated sync
 */
class WorkManagerScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkManagerScheduler"
        
        // Periodic intervals
        private const val SYNC_INTERVAL_MINUTES = 30L
        private const val NOTIFICATION_INTERVAL_MINUTES = 60L
        
        // Flex intervals (WorkManager may execute within this window before repeat interval ends)
        private const val SYNC_FLEX_MINUTES = 10L
        private const val NOTIFICATION_FLEX_MINUTES = 15L
        
        // Retry backoff
        private const val BACKOFF_DELAY_MINUTES = 5L
    }
    
    private val workManager: WorkManager = WorkManager.getInstance(context)
    
    /**
     * Schedules the periodic background sync worker (30 minutes).
     * 
     * Constraints:
     * - Requires network connectivity (CONNECTED, not METERED for cell data too)
     * - Retry on failure with exponential backoff
     * 
     * @param replace If true, replaces existing work; if false, keeps existing
     */
    fun schedulePeriodicSync(replace: Boolean = false) {
        Log.d(TAG, "Scheduling periodic sync (replace: $replace)")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES,
            SYNC_FLEX_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_MINUTES,
                TimeUnit.MINUTES
            )
            .addTag(BackgroundSyncWorker.WORK_NAME)
            .build()
        
        val policy = if (replace) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        
        workManager.enqueueUniquePeriodicWork(
            BackgroundSyncWorker.WORK_NAME,
            policy,
            syncRequest
        )
        
        Log.d(TAG, "Periodic sync scheduled: every $SYNC_INTERVAL_MINUTES minutes")
    }
    
    /**
     * Schedules the periodic notification summary worker (60 minutes).
     * 
     * Constraints:
     * - No network required (works with local data)
     * - Battery not low (respects system battery saving)
     */
    fun schedulePeriodicNotifications(replace: Boolean = false) {
        Log.d(TAG, "Scheduling periodic notifications (replace: $replace)")
        
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        
        val notificationRequest = PeriodicWorkRequestBuilder<NotificationSummaryWorker>(
            NOTIFICATION_INTERVAL_MINUTES, TimeUnit.MINUTES,
            NOTIFICATION_FLEX_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(NotificationSummaryWorker.WORK_NAME)
            .build()
        
        val policy = if (replace) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        
        workManager.enqueueUniquePeriodicWork(
            NotificationSummaryWorker.WORK_NAME,
            policy,
            notificationRequest
        )
        
        Log.d(TAG, "Periodic notifications scheduled: every $NOTIFICATION_INTERVAL_MINUTES minutes")
    }
    
    /**
     * Schedules all periodic workers.
     * Call this from Application.onCreate() to ensure workers are registered.
     */
    fun scheduleAllPeriodicWork() {
        schedulePeriodicSync(replace = false)
        schedulePeriodicNotifications(replace = false)
        Log.d(TAG, "All periodic work scheduled")
    }
    
    /**
     * Triggers an immediate background sync.
     * Used when user manually requests sync from settings.
     * 
     * Flow:
     * 1. Execute sync immediately (expedited if possible)
     * 2. On completion, show notification with results
     * 
     * @param expedited If true, requests expedited execution (Android 12+)
     * @return OneTimeWorkRequest for observing work state
     */
    fun triggerImmediateSync(expedited: Boolean = true): OneTimeWorkRequest {
        Log.d(TAG, "Triggering immediate sync (expedited: $expedited)")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val inputData = workDataOf(
            BackgroundSyncWorker.KEY_IS_MANUAL_SYNC to true
        )
        
        val syncRequestBuilder = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("manual_sync")
        
        // Request expedited execution for better user experience
        if (expedited) {
            syncRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        
        val syncRequest = syncRequestBuilder.build()
        
        workManager.enqueue(syncRequest)
        
        Log.d(TAG, "Immediate sync enqueued: ${syncRequest.id}")
        return syncRequest
    }
    
    /**
     * Triggers an immediate notification summary.
     * Used when user manually requests notification from settings.
     * 
     * Special behavior:
     * - Queries all unnotified updates since last notification
     * - Shows notification even if count is low
     */
    fun triggerImmediateNotificationSummary(): OneTimeWorkRequest {
        Log.d(TAG, "Triggering immediate notification summary")
        
        val inputData = workDataOf(
            NotificationSummaryWorker.KEY_IS_MANUAL_TRIGGER to true,
            NotificationSummaryWorker.KEY_TIME_RANGE_HOURS to 24 // Last 24 hours for manual
        )
        
        val notificationRequest = OneTimeWorkRequestBuilder<NotificationSummaryWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("manual_notification")
            .build()
        
        workManager.enqueue(notificationRequest)
        
        Log.d(TAG, "Immediate notification summary enqueued: ${notificationRequest.id}")
        return notificationRequest
    }
    
    /**
     * Chains sync and notification for manual trigger.
     * First syncs data, then shows notification with results.
     * 
     * @return UUID of the chain for observing completion
     */
    fun triggerSyncThenNotify(): java.util.UUID {
        Log.d(TAG, "Triggering sync-then-notify chain")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncInputData = workDataOf(
            BackgroundSyncWorker.KEY_IS_MANUAL_SYNC to true
        )
        
        val syncRequest = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(constraints)
            .setInputData(syncInputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("manual_sync_chain")
            .build()
        
        val notificationInputData = workDataOf(
            NotificationSummaryWorker.KEY_IS_MANUAL_TRIGGER to true
        )
        
        val notificationRequest = OneTimeWorkRequestBuilder<NotificationSummaryWorker>()
            .setInputData(notificationInputData)
            .addTag("manual_notification_chain")
            .build()
        
        // Chain: sync first, then notification
        workManager
            .beginWith(syncRequest)
            .then(notificationRequest)
            .enqueue()
        
        Log.d(TAG, "Sync-then-notify chain enqueued")
        return syncRequest.id
    }
    
    /**
     * Cancels all periodic sync work.
     * Call when user disables background sync in settings.
     */
    fun cancelPeriodicSync() {
        Log.d(TAG, "Cancelling periodic sync")
        workManager.cancelUniqueWork(BackgroundSyncWorker.WORK_NAME)
    }
    
    /**
     * Cancels all periodic notification work.
     */
    fun cancelPeriodicNotifications() {
        Log.d(TAG, "Cancelling periodic notifications")
        workManager.cancelUniqueWork(NotificationSummaryWorker.WORK_NAME)
    }
    
    /**
     * Cancels all scheduled work.
     * Use when user wants to completely disable background features.
     */
    fun cancelAllWork() {
        Log.d(TAG, "Cancelling all work")
        workManager.cancelAllWorkByTag(BackgroundSyncWorker.WORK_NAME)
        workManager.cancelAllWorkByTag(NotificationSummaryWorker.WORK_NAME)
        workManager.cancelAllWorkByTag("manual_sync")
        workManager.cancelAllWorkByTag("manual_notification")
        workManager.cancelAllWorkByTag("manual_sync_chain")
        workManager.cancelAllWorkByTag("manual_notification_chain")
    }
    
    /**
     * Gets work info for observing sync status in UI.
     */
    fun getSyncWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(BackgroundSyncWorker.WORK_NAME)
    
    /**
     * Gets work info for observing notification status in UI.
     */
    fun getNotificationWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(NotificationSummaryWorker.WORK_NAME)
    
    /**
     * Observes work by ID (for one-time work requests).
     */
    fun observeWork(workId: java.util.UUID) = workManager.getWorkInfoByIdLiveData(workId)
}

