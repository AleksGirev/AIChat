package com.example.aichat.data.mcp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.aichat.data.Config

/**
 * Configuration manager for MCP servers
 * Stores MCP server configuration securely using EncryptedSharedPreferences
 */
class McpConfig(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mcp_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        // Note: McpFactory supports "http", "rest", and "websocket" transport types
        // stdio is stored here for compatibility but not used by McpFactory
        private const val KEY_SERVER_TYPE = "server_type" // "http", "rest", "websocket"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_STDIO_COMMAND = "stdio_command" // Not used by McpFactory
        private const val KEY_ENABLED = "mcp_enabled"
        
        // Default configuration (REST API MCP server)
        const val DEFAULT_SERVER_TYPE = "rest"
        val DEFAULT_SERVER_URL = Config.MCP_SERVER_URL
        val DEFAULT_API_KEY = Config.CONTEXT7_API_KEY.takeIf { it.isNotBlank() }
    }
    
    /**
     * MCP server configuration
     * 
     * Note: McpFactory.createTransport() supports "http", "rest", and "websocket" types.
     * - "rest": REST API format (GET /tools, POST /tools/{name})
     * - "http": JSON-RPC 2.0 format
     * - "websocket": WebSocket connection
     * stdioCommand is stored for backward compatibility but not used.
     */
    data class ServerConfig(
        val type: String, // "http", "rest", or "websocket" (stdio not supported by McpFactory)
        val url: String? = null,
        val apiKey: String? = null,
        val stdioCommand: List<String>? = null // Not used by McpFactory
    )
    
    /**
     * Gets current MCP server configuration
     */
    fun getServerConfig(): ServerConfig {
        val type = DEFAULT_SERVER_TYPE
        val url = sharedPreferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
        val apiKey = sharedPreferences.getString(KEY_API_KEY, DEFAULT_API_KEY)
        val stdioCommand = sharedPreferences.getStringSet(KEY_STDIO_COMMAND, null)
            ?.toList()
        
        return ServerConfig(
            type = type,
            url = url,
            apiKey = apiKey,
            stdioCommand = stdioCommand
        )
    }
    
    /**
     * Saves MCP server configuration
     */
    fun setServerConfig(config: ServerConfig) {
        sharedPreferences.edit()
            .putString(KEY_SERVER_TYPE, config.type)
            .putString(KEY_SERVER_URL, config.url)
            .putString(KEY_API_KEY, config.apiKey)
            .putStringSet(KEY_STDIO_COMMAND, config.stdioCommand?.toSet())
            .apply()
    }
    
    /**
     * Checks if MCP is enabled
     */
    fun isEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLED, true)
    }
    
    /**
     * Enables or disables MCP
     */
    fun setEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}

