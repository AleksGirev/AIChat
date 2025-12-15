package com.example.aichat.data.mcp

import android.content.Context
import com.example.aichat.data.mcp.model.McpTransport
import com.example.aichat.data.mcp.transport.HttpMcpTransport
import com.example.aichat.data.mcp.transport.StdioMcpTransport
import com.example.aichat.data.mcp.transport.WebSocketMcpTransport
import com.example.aichat.data.network.NetworkModule
import com.google.gson.Gson

/**
 * Factory for creating MCP clients and repositories
 */
object McpFactory {
    
    /**
     * Creates MCP transport based on configuration
     */
    fun createTransport(
        config: McpConfig.ServerConfig,
        httpClient: okhttp3.OkHttpClient,
        gson: Gson
    ): McpTransport {
        return when (config.type) {
            "http" -> {
                HttpMcpTransport(
                    baseUrl = config.url ?: throw IllegalArgumentException("HTTP transport requires URL"),
                    apiKey = config.apiKey,
                    httpClient = httpClient,
                    gson = gson
                )
            }
            "websocket" -> {
                WebSocketMcpTransport(
                    wsUrl = config.url ?: throw IllegalArgumentException("WebSocket transport requires URL"),
                    apiKey = config.apiKey,
                    httpClient = httpClient,
                    gson = gson
                )
            }
            "stdio" -> {
                val command = config.stdioCommand
                    ?: throw IllegalArgumentException("Stdio transport requires command")
                StdioMcpTransport(
                    command = command,
                    gson = gson
                )
            }
            else -> {
                throw IllegalArgumentException("Unknown transport type: ${config.type}")
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

