package com.example.aichat.data.mcp.model

import kotlinx.coroutines.flow.Flow

/**
 * Base interface for MCP transport layers
 * Supports different connection types: stdio, HTTP, WebSocket
 */
interface McpTransport {
    /**
     * Connects to the MCP server
     */
    suspend fun connect()
    
    /**
     * Disconnects from the MCP server
     */
    suspend fun disconnect()
    
    /**
     * Checks if transport is connected
     */
    fun isConnected(): Boolean
    
    /**
     * Sends a request and returns the response
     */
    suspend fun sendRequest(request: McpRequest): McpResponse
    
    /**
     * Sends a request and returns a Flow of responses (for streaming)
     */
    fun sendRequestStream(request: McpRequest): Flow<McpResponse>
    
    /**
     * Receives notifications from the server
     */
    fun receiveNotifications(): Flow<McpRequest>
}

