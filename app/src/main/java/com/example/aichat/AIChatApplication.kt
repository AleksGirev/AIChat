package com.example.aichat

import android.app.Application
import android.util.Log
import com.example.aichat.data.brightdata.BrightDataMcpClient
import com.example.aichat.data.crm.CrmMcpClient
import com.example.aichat.data.rag.RagService
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
        
        // Initialize CRM MCP server for support service
        initializeCrmMcp()
        
        // Initialize RAG documentation
        initializeRagDocumentation()
        
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
    
    /**
     * Initializes CRM MCP server for support service.
     * 
     * This method:
     * 1. Gets CrmMcpClient from Koin
     * 2. Initializes connection to CRM MCP server
     * 3. Logs initialization status
     * 
     * Runs in background scope to avoid blocking app startup.
     */
    private fun initializeCrmMcp() {
        applicationScope.launch {
            try {
                val crmClient: CrmMcpClient = GlobalContext.get().get()
                
                Log.d(TAG, "=== CRM MCP Server Initialization ===")
                
                val initResult = crmClient.initialize()
                if (initResult.isSuccess) {
                    Log.d(TAG, "✓ CRM MCP server connected successfully")
                } else {
                    val error = initResult.exceptionOrNull()
                    Log.w(TAG, "✗ Failed to connect to CRM MCP server: ${error?.message}", error)
                    Log.w(TAG, "  Support mode will work without CRM context")
                    Log.w(TAG, "  Check your CRM_MCP_BRIDGE_URL in Config.kt and ensure server is running")
                }
                
                Log.d(TAG, "=== CRM MCP Server Initialization Complete ===")
            } catch (e: Exception) {
                Log.w(TAG, "Exception during CRM MCP initialization (non-critical)", e)
                Log.w(TAG, "Support mode will work without CRM context")
            }
        }
    }
    
    /**
     * Initializes RAG documentation service.
     * Copies product documentation from assets to app files directory for search.
     */
    private fun initializeRagDocumentation() {
        applicationScope.launch {
            try {
                val ragService: RagService = GlobalContext.get().get()
                
                Log.d(TAG, "=== RAG Documentation Initialization ===")
                
                val initResult = ragService.initializeDocumentation()
                if (initResult.isSuccess) {
                    Log.d(TAG, "✓ RAG documentation initialized successfully")
                } else {
                    val error = initResult.exceptionOrNull()
                    Log.w(TAG, "⚠ RAG documentation initialization warning: ${error?.message}", error)
                    Log.w(TAG, "  Support mode will work without RAG context")
                }
                
                Log.d(TAG, "=== RAG Documentation Initialization Complete ===")
            } catch (e: Exception) {
                Log.w(TAG, "Exception during RAG documentation initialization (non-critical)", e)
                Log.w(TAG, "Support mode will work without RAG context")
            }
        }
    }
}

