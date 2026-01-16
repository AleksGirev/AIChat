package com.example.aichat.console.mcp.tasks

import kotlinx.coroutines.runBlocking

/**
 * Main entry point for Tasks MCP Server
 * 
 * Runs as a standalone stdio-based MCP server
 */
fun main(args: Array<String>) {
    runBlocking {
        val server = TasksMcpServer()
        server.run()
    }
}
