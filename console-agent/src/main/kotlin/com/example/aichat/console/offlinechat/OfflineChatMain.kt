package com.example.aichat.console.offlinechat

import kotlinx.coroutines.runBlocking

/**
 * Main entry point for the Offline AI Chat CLI.
 * 
 * This is a simple REPL (Read-Eval-Print Loop) that allows users to chat
 * with a local LLM running via Ollama, entirely offline.
 * 
 * Usage:
 *   ./gradlew :console-agent:runOfflineChat
 * 
 * Or if running the JAR directly:
 *   java -jar console-agent.jar
 */
fun main() {
    println("🧠 Offline AI Assistant (Qwen2 7B)")
    println("=" .repeat(50))
    println("Connecting to Ollama at http://localhost:11434...")
    println("Model: qwen2:7b-instruct")
    println("Type 'exit' to quit")
    println("=" .repeat(50))
    println()

    // Use OkHttp implementation (avoids Ktor dependency issues)
    val llmClient = LlmClientOkHttp()

    runBlocking {
        try {
            // Test connection with a simple prompt
            try {
                llmClient.generate("Hello")
                println("✓ Connected to Ollama")
            } catch (e: Exception) {
                println("✗ Error: Could not connect to Ollama")
                println("  Make sure Ollama is running: ollama serve")
                println("  Error: ${e.message}")
                return@runBlocking
            }
            println()

            // REPL loop
            val console = System.console()
            var running = true

            while (running) {
                try {
                    // Read user input
                    val input = if (console != null) {
                        console.readLine("You: ")?.trim()
                    } else {
                        print("You: ")
                        System.out.flush()
                        readLine()?.trim()
                    }

                    when {
                        input.isNullOrBlank() -> continue
                        input.equals("exit", ignoreCase = true) -> {
                            running = false
                            println("Goodbye! 👋")
                        }
                        else -> {
                            // Send to LLM and print response
                            try {
                                print("AI: ")
                                val response = llmClient.generate(input)
                                println(response)
                                println()
                            } catch (e: Exception) {
                                println("Error: ${e.message}")
                                println()
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Error processing input: ${e.message}")
                    println()
                }
            }
        } finally {
            llmClient.close()
        }
    }
}
