package com.example.aichat.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.aichat.MainActivity
import com.example.aichat.R
import com.example.aichat.data.local.SyncUpdateEntity

/**
 * Manages notifications for background sync updates.
 * 
 * Architecture Decisions:
 * 1. Separate notification channels for different priority levels
 * 2. Grouped notifications to avoid overwhelming the user
 * 3. Deep link support for navigating to specific updates
 * 4. Android 13+ POST_NOTIFICATIONS permission handling
 * 
 * Notification Strategy:
 * - HIGH_PRIORITY_CHANNEL: Immediate notifications for critical updates (priority >= 4)
 * - SUMMARY_CHANNEL: Hourly summary notifications aggregating all updates
 */
class SyncNotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SyncNotificationManager"
        
        // Notification Channels
        const val HIGH_PRIORITY_CHANNEL_ID = "sync_high_priority"
        const val SUMMARY_CHANNEL_ID = "sync_summary"
        
        // Notification IDs
        const val SUMMARY_NOTIFICATION_ID = 1001
        const val HIGH_PRIORITY_BASE_ID = 2000
        
        // Group Keys for notification grouping
        const val SYNC_GROUP_KEY = "com.example.aichat.SYNC_UPDATES"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Creates notification channels for Android 8.0+
     * Must be called before showing any notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager = 
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // High Priority Channel - for critical updates that need immediate attention
            val highPriorityChannel = NotificationChannel(
                HIGH_PRIORITY_CHANNEL_ID,
                "Critical Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for critical sync updates that require immediate attention"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Summary Channel - for periodic summary notifications
            val summaryChannel = NotificationChannel(
                SUMMARY_CHANNEL_ID,
                "Sync Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Hourly summary notifications for accumulated updates"
                setShowBadge(true)
            }
            
            systemNotificationManager.createNotificationChannels(
                listOf(highPriorityChannel, summaryChannel)
            )
            
            Log.d(TAG, "Notification channels created")
        }
    }
    
    /**
     * Checks if the app has notification permission (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required below Android 13
        }
    }
    
    /**
     * Shows an hourly summary notification with aggregated update count
     * 
     * @param updates List of updates to summarize
     * @param timeRangeHours Time range covered by this summary (default: 1 hour)
     * @param belarusAverageTemperature Average temperature from Belarusian cities (optional)
     */
    fun showSummaryNotification(
        updates: List<SyncUpdateEntity>,
        timeRangeHours: Int = 1,
        belarusAverageTemperature: Double? = null
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, skipping summary notification")
            return
        }
        
        // Show notification even if no updates, if we have weather data
        if (updates.isEmpty() && belarusAverageTemperature == null) {
            Log.d(TAG, "No updates or weather data to show in summary notification")
            return
        }
        
        val updateCount = updates.size
        val importantCount = updates.count { it.priority >= 4 }
        
        // Build summary text
        val summaryText = buildString {
            append("You received $updateCount update")
            if (updateCount != 1) append("s")
            append(" in the last ")
            if (timeRangeHours == 1) append("hour")
            else append("$timeRangeHours hours")
            
            if (importantCount > 0) {
                append(" ($importantCount important)")
            }
        }
        
        // Build expanded content
        val expandedContent = buildString {
            // Add Belarus temperature summary if available
            if (belarusAverageTemperature != null) {
                val tempFormatted = String.format("%.1f", belarusAverageTemperature)
                append("Средняя температура в Беларуси составляет $tempFormatted градусов\n\n")
            }
            
            // Group by type
            val byType = updates.groupBy { it.type }
            byType.forEach { (type, typeUpdates) ->
                append("• ${type.replaceFirstChar { it.uppercase() }}: ${typeUpdates.size}\n")
            }
            
            // Show most recent high-priority items
            val recentHighPriority = updates
                .filter { it.priority >= 4 }
                .take(3)
            
            if (recentHighPriority.isNotEmpty()) {
                append("\nRecent important:\n")
                recentHighPriority.forEach { update ->
                    append("  - ${update.title}\n")
                }
            }
        }.trim()
        
        // Update summary text to include Belarus temperature if available
        val finalSummaryText = if (belarusAverageTemperature != null) {
            val tempFormatted = String.format("%.1f", belarusAverageTemperature)
            "🌡️ Средняя температура в Беларуси: $tempFormatted°C"
        } else {
            summaryText
        }
        
        // Update title to include temperature if available
        val notificationTitle = if (belarusAverageTemperature != null) {
            val tempFormatted = String.format("%.1f", belarusAverageTemperature)
            "Погода в Беларуси: $tempFormatted°C"
        } else {
            "Sync Summary"
        }
        
        // Create intent for notification tap
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_sync_updates", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(finalSummaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(SYNC_GROUP_KEY)
            .setGroupSummary(true)
            .setNumber(updateCount)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        try {
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, notification)
            Log.d(TAG, "Summary notification shown with $updateCount updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show summary notification: ${e.message}")
        }
    }
    
    /**
     * Shows immediate notification for high-priority updates
     * Called from BackgroundSyncWorker when critical updates are detected
     * 
     * @param update The high-priority update to notify about
     */
    fun showHighPriorityNotification(update: SyncUpdateEntity) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, skipping high-priority notification")
            return
        }
        
        if (update.priority < 4) {
            Log.d(TAG, "Update priority ${update.priority} is not high enough for immediate notification")
            return
        }
        
        // Create intent for notification tap
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sync_update_id", update.id)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            update.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use unique notification ID based on update ID
        val notificationId = HIGH_PRIORITY_BASE_ID + (update.id.hashCode() and 0xFFF)
        
        val notification = NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(update.title)
            .setContentText(update.content.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(update.content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(SYNC_GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        
        try {
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "High-priority notification shown for update: ${update.id}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show high-priority notification: ${e.message}")
        }
    }
    
    /**
     * Shows a notification for manual sync completion
     * Called when user manually triggers sync from settings
     * 
     * @param updateCount Number of updates found
     * @param errorMessage Optional error message if sync failed
     */
    fun showManualSyncCompleteNotification(
        updateCount: Int,
        errorMessage: String? = null
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, skipping manual sync notification")
            return
        }
        
        val (title, content) = if (errorMessage != null) {
            "Sync Failed" to "Error: $errorMessage"
        } else if (updateCount > 0) {
            "Sync Complete" to "Found $updateCount new update${if (updateCount != 1) "s" else ""}"
        } else {
            "Sync Complete" to "No new updates found"
        }
        
        // Create intent for notification tap
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_sync_updates", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        try {
            notificationManager.notify(SUMMARY_NOTIFICATION_ID + 1, notification)
            Log.d(TAG, "Manual sync notification shown: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show manual sync notification: ${e.message}")
        }
    }
    
    /**
     * Cancels all sync-related notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
        // Note: Individual high-priority notifications are auto-cancelled on tap
    }
    
    /**
     * Cancels summary notification only
     */
    fun cancelSummaryNotification() {
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
    }
}

