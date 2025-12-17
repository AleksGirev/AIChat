package com.example.aichat.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.aichat.data.Config
import com.example.aichat.data.mcp.McpClient
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.mcp.transport.RestMcpTransport
import com.google.gson.Gson
import okhttp3.OkHttpClient

/**
 * Configuration for the MCP server used in background sync tasks.
 * 
 * Supports two modes:
 * 1. STDIO: Local process-based MCP server (for development/desktop)
 * 2. HTTP: Remote HTTP-based MCP server (for production Android deployment)
 * 
 * Default configuration uses the OpenWeather MCP server via stdio transport.
 * 
 * IMPORTANT for Android deployment:
 * - STDIO mode requires the MCP server process to be accessible on the device
 * - For production, use HTTP mode with a backend proxy to the MCP server
 * - Or bundle the MCP server in a container/service accessible via HTTP
 */
object BackgroundSyncMcpConfig {
    
    private const val TAG = "BackgroundSyncMcpConfig"
    
    /**
     * OpenWeather MCP Server Configuration (STDIO mode)
     * 
     * This configuration launches a local Python MCP server that wraps the OpenWeather API.
     * Works on macOS/Linux development machines with Python installed.
     * 
     * For Android devices:
     * - Use HTTP mode instead
     * - Or run the MCP server externally and connect via network
     */
    object OpenWeatherStdio {
        // Python interpreter path (homebrew on macOS)
        const val PYTHON_PATH = "/opt/homebrew/bin/python3.11"
        
        // MCP server script path
        const val SCRIPT_PATH = "/Users/Girev.Aleksandr2/mcp_server/mcp_openapi_server.py"
        
        // OpenAPI spec file for OpenWeather
        const val SPEC_FILE = "/Users/Girev.Aleksandr2/mcp_server/openweather-free-spec.yaml"
        
        // Environment variable name for API key
        const val API_KEY_ENV_NAME = "OPENWEATHER_API_KEY"
        
        // OpenWeather API key (store securely in production!)
        const val OPENWEATHER_API_KEY = "18df56bd5d77599af99c8231a9cac0ab"
        
        /**
         * Builds the command list for launching the MCP server
         */
        fun buildCommand(): List<String> = listOf(
            PYTHON_PATH,
            SCRIPT_PATH,
            "--spec-file", SPEC_FILE,
            "--api-key-env", API_KEY_ENV_NAME
        )
        
        /**
         * Builds the environment variables map
         */
        fun buildEnvironment(): Map<String, String> = mapOf(
            "PYTHONUNBUFFERED" to "1",  // Ensure unbuffered output for real-time communication
            API_KEY_ENV_NAME to OPENWEATHER_API_KEY
        )
    }
    
    /**
     * Transport mode for background sync MCP client
     */
    enum class TransportMode {
        REST    // REST API endpoint (GET /tools, POST /tools/{name})
    }
    
    /**
     * Current transport mode - REST API is the default and only supported mode
     * 
     * REST: Connects to REST-based MCP server
     * - GET /health - Health check
     * - GET /tools - List tools
     * - POST /tools/{toolName} - Call tool with {"arguments": {...}}
     */
    var currentMode: TransportMode = TransportMode.REST
    
    /**
     * REST API base URL (e.g., http://192.168.1.100:8000)
     * 
     * Defaults to the MCP server URL from Config.kt
     * For Android Emulator: Use http://10.0.2.2:8000
     * For Physical Device: Use http://YOUR_COMPUTER_IP:8000
     */
    var httpEndpoint: String = Config.MCP_SERVER_URL
    
    /**
     * Creates the MCP repository for background sync based on current configuration
     * 
     * @param context Android context (used for secure preferences if needed)
     * @param okHttpClient OkHttp client for HTTP transport
     * @param gson Gson instance for JSON serialization
     * @return McpRepository configured for background sync, or null if setup fails
     */
    fun createSyncMcpRepository(
        context: Context,
        okHttpClient: OkHttpClient,
        gson: Gson
    ): McpRepository? {
        return try {
            when (currentMode) {
                TransportMode.REST -> createRestRepository(okHttpClient, gson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sync MCP repository", e)
            null
        }
    }
    
    /**
     * Creates MCP repository using REST API transport
     * 
     * REST API format:
     * - GET {baseUrl}/health - Health check
     * - GET {baseUrl}/tools - List tools
     * - POST {baseUrl}/tools/{toolName} - Call tool with {"arguments": {...}}
     */
    private fun createRestRepository(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): McpRepository {
        Log.d(TAG, "Creating REST MCP repository at: $httpEndpoint")
        
        val transport = RestMcpTransport(
            baseUrl = httpEndpoint,
            apiKey = null, // Add if needed
            httpClient = okHttpClient,
            gson = gson
        )
        
        val client = McpClient(transport)
        return McpRepository(client)
    }
    
    /**
     * Validates that the STDIO configuration is available.
     * Checks if Python and script files exist at configured paths.
     * 
     * @return true if configuration is valid and files exist
     */
    fun validateStdioConfig(): Boolean {
        return try {
            val pythonFile = java.io.File(OpenWeatherStdio.PYTHON_PATH)
            val scriptFile = java.io.File(OpenWeatherStdio.SCRIPT_PATH)
            val specFile = java.io.File(OpenWeatherStdio.SPEC_FILE)
            
            val pythonExists = pythonFile.exists() && pythonFile.canExecute()
            val scriptExists = scriptFile.exists() && scriptFile.canRead()
            val specExists = specFile.exists() && specFile.canRead()
            
            Log.d(TAG, "STDIO config validation:")
            Log.d(TAG, "  Python ($pythonExists): ${OpenWeatherStdio.PYTHON_PATH}")
            Log.d(TAG, "  Script ($scriptExists): ${OpenWeatherStdio.SCRIPT_PATH}")
            Log.d(TAG, "  Spec ($specExists): ${OpenWeatherStdio.SPEC_FILE}")
            
            pythonExists && scriptExists && specExists
        } catch (e: Exception) {
            Log.e(TAG, "Error validating STDIO config", e)
            false
        }
    }
}

/**
 * Extension function to configure sync MCP from JSON config string
 * Matches the format used by Claude Desktop / MCP clients
 * 
 * Example JSON:
 * {
 *   "mcpServers": {
 *     "openweather": {
 *       "command": "/opt/homebrew/bin/python3.11",
 *       "args": [...],
 *       "env": {...}
 *     }
 *   }
 * }
 */
fun BackgroundSyncMcpConfig.configureFromJson(jsonConfig: String, gson: Gson): Boolean {
    return try {
        @Suppress("UNCHECKED_CAST")
        val config = gson.fromJson(jsonConfig, Map::class.java) as Map<String, Any>
        val mcpServers = config["mcpServers"] as? Map<String, Any> ?: return false
        
        // Get first server config (or specific named server)
        val serverConfig = mcpServers.values.firstOrNull() as? Map<String, Any> ?: return false
        
        // This would update the config based on JSON
        // For now, using hardcoded values from OpenWeatherStdio object
        
        Log.d("BackgroundSyncMcpConfig", "Loaded MCP config from JSON")
        true
    } catch (e: Exception) {
        Log.e("BackgroundSyncMcpConfig", "Failed to parse MCP config JSON", e)
        false
    }
}

