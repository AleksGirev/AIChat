package com.example.aichat.console.mcp.repo

import kotlinx.coroutines.runBlocking

/**
 * Main entry point for the Repo MCP Server
 * 
 * This server runs as a standalone process that communicates via stdin/stdout
 * using the MCP (Model Context Protocol) JSON-RPC format.
 * 
 * Usage:
 * ```bash
 * java -jar repo-mcp-server.jar
 * ```
 * 
 * Or via Gradle:
 * ```bash
 * ./gradlew :console-agent:runRepoMcpServer
 * ```
 */
fun main(args: Array<String>) {
    // Note: stdout is reserved for JSON-RPC protocol
    // All logging should go to stderr
    val server = RepoMcpServer()
    runBlocking {
        server.run()
    }
}

