package com.example.aichat.console.remotechat

import com.example.aichat.console.offlinechat.LlmClientOkHttp
import kotlinx.coroutines.runBlocking

/**
 * Main entry point for the Remote AI Chat CLI.
 * 
 * This is a simple REPL (Read-Eval-Print Loop) that allows users to chat
 * with a remote LLM running via Ollama on a remote server.
 * 
 * Configuration:
 *   - OLLAMA_BASE_URL: Base URL of remote Ollama server (default: http://193.42.127.171:11434)
 *   - OLLAMA_MODEL: Model name to use (default: qwen2:7b-instruct)
 * 
 * Usage:
 *   ./gradlew :console-agent:runRemoteChat
 * 
 * Or with custom URL:
 *   OLLAMA_BASE_URL=http://your-server:11434 ./gradlew :console-agent:runRemoteChat
 */
fun main(args: Array<String>) {
    // Parse arguments or use environment variables
    val baseUrl = args.getOrNull(0) 
        ?: System.getenv("OLLAMA_BASE_URL") 
        ?: "http://193.42.127.171:11434"
    
    val model = args.getOrNull(1)
        ?: System.getenv("OLLAMA_MODEL")
        ?: "qwen2:7b-instruct"
    
    println("🌐 Remote AI Assistant")
    println("=" .repeat(60))
    println("Server: $baseUrl")
    println("Model: $model")
    println("Type 'exit' to quit, 'help' for commands")
    println("=" .repeat(60))
    println()
    
    // Use OkHttp implementation with remote server URL
    val llmClient = LlmClientOkHttp(
        baseUrl = baseUrl,
        model = model
    )
    
    runBlocking {
        try {
            // Test connection with a simple prompt
            print("Connecting to remote Ollama server... ")
            System.out.flush()
            try {
                llmClient.generate("Hello")
                println("✓ Connected")
                println("  Server: $baseUrl")
                println("  Model: $model")
                println()
            } catch (e: Exception) {
                println("✗ Failed")
                println("  Server: $baseUrl")
                println("  Error: ${e.message}")
                println("  Make sure the server is running and accessible")
                return@runBlocking
            }
            
            // REPL loop
            val console = System.console()
            var running = true
            
            println("Chat started. Type 'help' for commands, 'exit' to quit.")
            println("─".repeat(60))
            println()
            
            while (running) {
                try {
                    // Read user input with clean prompt
                    val input = if (console != null) {
                        console.readLine("\nYou: ")?.trim()
                    } else {
                        print("\nYou: ")
                        System.out.flush()
                        readLine()?.trim()
                    }
                    
                    when {
                        input.isNullOrBlank() -> continue
                        input.equals("exit", ignoreCase = true) || 
                        input.equals("quit", ignoreCase = true) -> {
                            running = false
                            println("\nGoodbye! 👋")
                        }
                        input.equals("help", ignoreCase = true) -> {
                            println("\nAvailable commands:")
                            println("  help  - Show this help message")
                            println("  exit  - Exit the chat")
                            println("  quit  - Exit the chat")
                            println("  info  - Show server and model information")
                        }
                        input.equals("info", ignoreCase = true) -> {
                            println("\nServer Information:")
                            println("  URL: $baseUrl")
                            println("  Model: $model")
                        }
                        else -> {
                            // Send to LLM and print response
                            try {
                                print("AI: ")
                                System.out.flush()
                                // Show progress indicator for long-running requests
                                val response = llmClient.generate(input)
                                println(response)
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: "Unknown error"
                                if (errorMsg.contains("timeout", ignoreCase = true) || 
                                    errorMsg.contains("timed out", ignoreCase = true)) {
                                    println("\n✗ Request timed out")
                                    println("  The server may be processing your request, but it's taking too long.")
                                    println("  Try a shorter prompt or check server load.")
                                } else {
                                    println("\n✗ Error: $errorMsg")
                                    println("  Check your connection to: $baseUrl")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("\n✗ Error processing input: ${e.message}")
                }
            }
        } finally {
            llmClient.close()
        }
    }
}
