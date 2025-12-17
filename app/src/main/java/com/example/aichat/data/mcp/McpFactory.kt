package com.example.aichat.data.mcp

import android.content.Context
import com.example.aichat.data.mcp.model.McpTransport
import com.example.aichat.data.mcp.transport.HttpMcpTransport
import com.example.aichat.data.mcp.transport.RestMcpTransport
import com.example.aichat.data.mcp.transport.WebSocketMcpTransport
import com.example.aichat.data.network.NetworkModule
import com.google.gson.Gson

/**
 * Factory for creating MCP clients and repositories.
 * 
 * Supports only HTTP and WebSocket transports (no stdio).
 * This factory is used for the main chat MCP connection.
 * 
 * For background sync with stdio transport, see BackgroundSyncMcpConfig.
 */
object McpFactory {
    
    /**
     * Creates MCP transport based on configuration.
     * 
     * Supported transport types:
     * - "http": HTTP POST-based MCP server (JSON-RPC 2.0)
     * - "rest": REST API-based MCP server (GET /tools, POST /tools/{name})
     * - "websocket": WebSocket-based MCP server
     * 
     * @param config Server configuration from McpConfig
     * @param httpClient OkHttp client for network requests
     * @param gson Gson instance for JSON serialization
     * @return Configured MCP transport
     * @throws IllegalArgumentException if transport type is not supported or URL is missing
     */
    fun createTransport(
        config: McpConfig.ServerConfig,
        httpClient: okhttp3.OkHttpClient,
        gson: Gson
    ): McpTransport {
        return when (config.type.lowercase()) {
            "http" -> {
                HttpMcpTransport(
                    baseUrl = config.url ?: throw IllegalArgumentException("HTTP transport requires URL"),
                    apiKey = config.apiKey,
                    httpClient = httpClient,
                    gson = gson
                )
            }
            "rest" -> {
                RestMcpTransport(
                    baseUrl = config.url ?: throw IllegalArgumentException("REST transport requires URL"),
                    apiKey = config.apiKey,
                    httpClient = httpClient,
                    gson = gson
                )
            }
            "websocket", "ws" -> {
                WebSocketMcpTransport(
                    wsUrl = config.url ?: throw IllegalArgumentException("WebSocket transport requires URL"),
                    apiKey = config.apiKey,
                    httpClient = httpClient,
                    gson = gson
                )
            }
            else -> {
                throw IllegalArgumentException(
                    "Unsupported transport type: '${config.type}'. " +
                    "Supported types: 'http', 'rest', 'websocket'. " +
                    "For stdio transport, use BackgroundSyncMcpConfig."
                )
            }
        }
    }
    
    /**
     * Creates MCP client with transport
     */
    fun createClient(transport: McpTransport): McpClient {
        return McpClient(transport)
    }
    
    /**
     * Creates MCP repository
     */
    fun createRepository(client: McpClient): McpRepository {
        return McpRepository(client)
    }
    
    /**
     * Creates complete MCP setup from configuration
     */
    fun createMcpRepository(
        context: Context,
        httpClient: okhttp3.OkHttpClient? = null,
        gson: Gson? = null
    ): McpRepository {
        val config = McpConfig(context)
//
//        if (!config.isEnabled()) {
//            return null
//        }
        
        val serverConfig = config.getServerConfig()
        val okHttpClient = httpClient ?: NetworkModule.provideOkHttpClient()
        val gsonInstance = gson ?: NetworkModule.provideGson()
        
        val transport = createTransport(serverConfig, okHttpClient, gsonInstance)
        val client = createClient(transport)
        
        return createRepository(client)
    }
}

