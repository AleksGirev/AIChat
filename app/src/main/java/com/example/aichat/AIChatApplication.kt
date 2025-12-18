package com.example.aichat

import android.app.Application
import android.util.Log
import com.example.aichat.di.allModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for AIChat.
 * 
 * Responsibilities:
 * 1. Initialize Koin dependency injection
 * 
 * Architecture Decisions:
 * - Koin initialized at app startup for consistent DI across all components
 * 
 * Android Lifecycle:
 * - onCreate() called once when app process starts
 * - Koin modules available throughout app lifecycle
 */
class AIChatApplication : Application() {
    
    companion object {
        private const val TAG = "AIChatApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application onCreate - initializing")
        
        // Initialize Koin dependency injection
        initializeKoin()
        
        Log.d(TAG, "Application initialized successfully")
    }
    
    /**
     * Initializes Koin DI framework with all modules.
     * 
     * Features:
     * - Android context available via androidContext()
     * - Debug logging in development builds
     */
    private fun initializeKoin() {
        startKoin {
            // Enable Koin logging (use Level.ERROR for release)
            androidLogger(Level.DEBUG)
            
            // Provide Android context
            androidContext(this@AIChatApplication)
            
            // Load all DI modules
            modules(allModules)
        }
        
        Log.d(TAG, "Koin initialized")
    }
}

