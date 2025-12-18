package com.example.aichat

import android.app.Application
import android.util.Log
import com.example.aichat.data.brightdata.BrightDataMcpClient
import com.example.aichat.di.allModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
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
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application onCreate - initializing")
        
        // Initialize Koin dependency injection
        initializeKoin()
        
        // Initialize BrightData MCP server and log available tools
        initializeBrightDataMcp()
        
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
    
    /**
     * Initializes BrightData MCP server and logs available tools.
     * 
     * This method:
     * 1. Gets BrightDataMcpClient from Koin
     * 2. Initializes connection to BrightData MCP server
     * 3. Lists available tools and logs them
     * 
     * Runs in background scope to avoid blocking app startup.
     */
    private fun initializeBrightDataMcp() {
        applicationScope.launch {
            try {
                // Get BrightData client from Koin (after Koin is initialized)
                // In Koin 3.x, use GlobalContext.get() to access the Koin instance
                val brightDataClient: BrightDataMcpClient = GlobalContext.get().get()
                
                Log.d(TAG, "=== BrightData MCP Server Initialization ===")
                
                // Initialize connection
                val initResult = brightDataClient.initialize()
                if (initResult.isSuccess) {
                    Log.d(TAG, "✓ BrightData MCP server connected successfully")
                    
                    // List available tools
                    val toolsResult = brightDataClient.listTools()
                    if (toolsResult.isSuccess) {
                        val tools = toolsResult.getOrNull() ?: emptyList()
                        Log.d(TAG, "✓ BrightData MCP server provides ${tools.size} tool(s):")
                        tools.forEach { tool ->
                            Log.d(TAG, "  - ${tool.name}: ${tool.description ?: "No description"}")
                            tool.inputSchema.properties?.forEach { (propName, prop) ->
                                Log.d(TAG, "    Parameter: $propName (${prop.type}) - ${prop.description ?: ""}")
                            }
                        }
                    } else {
                        val error = toolsResult.exceptionOrNull()
                        Log.e(TAG, "✗ Failed to list BrightData MCP tools: ${error?.message}", error)
                    }
                } else {
                    val error = initResult.exceptionOrNull()
                    Log.e(TAG, "✗ Failed to connect to BrightData MCP server: ${error?.message}", error)
                    Log.e(TAG, "  Check your BRIGHTDATA_MCP_URL and BRIGHTDATA_API_KEY in Config.kt")
                }
                
                Log.d(TAG, "=== BrightData MCP Server Initialization Complete ===")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during BrightData MCP initialization", e)
            }
        }
    }
}

