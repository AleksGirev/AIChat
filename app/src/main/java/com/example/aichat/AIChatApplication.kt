package com.example.aichat

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.aichat.di.allModules
import com.example.aichat.service.WeatherService
import com.example.aichat.sync.WorkManagerScheduler
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for AIChat.
 * 
 * Responsibilities:
 * 1. Initialize Koin dependency injection
 * 2. Configure WorkManager with Koin factory
 * 3. Schedule periodic background tasks
 * 
 * Architecture Decisions:
 * - Koin initialized at app startup for consistent DI across all components
 * - WorkManager configured with custom factory for Worker injection
 * - Background sync scheduled on first launch, persists across app restarts
 * 
 * Android Lifecycle:
 * - onCreate() called once when app process starts
 * - WorkManager tasks survive app restarts (persisted in SQLite)
 * - Koin modules available throughout app lifecycle
 */
class AIChatApplication : Application(), Configuration.Provider {
    
    companion object {
        private const val TAG = "AIChatApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application onCreate - initializing")
        
        // Initialize Koin dependency injection
        initializeKoin()
        
        // Schedule background work
        scheduleBackgroundWork()
        
        // Start weather service
        startWeatherService()
        
        Log.d(TAG, "Application initialized successfully")
    }
    
    /**
     * Initializes Koin DI framework with all modules.
     * 
     * Features:
     * - Android context available via androidContext()
     * - WorkManager factory for Worker injection
     * - Debug logging in development builds
     */
    private fun initializeKoin() {
        startKoin {
            // Enable Koin logging (use Level.ERROR for release)
            androidLogger(Level.DEBUG)
            
            // Provide Android context
            androidContext(this@AIChatApplication)
            
            // Configure WorkManager with Koin factory
            // This enables Worker injection via Koin
            workManagerFactory()
            
            // Load all DI modules
            modules(allModules)
        }
        
        Log.d(TAG, "Koin initialized")
    }
    
    /**
     * Schedules all periodic background workers.
     * Called once on app startup; WorkManager persists schedules.
     * 
     * Workers scheduled:
     * 1. BackgroundSyncWorker - every 30 minutes
     * 2. NotificationSummaryWorker - every 60 minutes
     */
    private fun scheduleBackgroundWork() {
        try {
            val scheduler: WorkManagerScheduler by inject()
            scheduler.scheduleAllPeriodicWork()
            Log.d(TAG, "Background work scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule background work", e)
        }
    }
    
    /**
     * Starts the WeatherService for periodic weather fetching and summary generation.
     * Service runs while app is active.
     */
    private fun startWeatherService() {
        try {
            val weatherService: WeatherService by inject()
            weatherService.start()
            Log.d(TAG, "WeatherService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WeatherService", e)
        }
    }
    
    /**
     * WorkManager configuration provider.
     * Required for custom WorkerFactory with Koin injection.
     * 
     * Implementation of Configuration.Provider allows WorkManager
     * to use our custom configuration instead of default initialization.
     * 
     * Important: When using Koin WorkManager integration, the factory
     * is automatically configured by workManagerFactory() in startKoin.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}

