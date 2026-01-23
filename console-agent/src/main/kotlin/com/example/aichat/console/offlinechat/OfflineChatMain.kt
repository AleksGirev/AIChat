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
fun main(args: Array<String>) {
    // Parse configuration from environment or arguments
    val baseUrl = args.getOrNull(0) 
        ?: System.getenv("OLLAMA_BASE_URL") 
        ?: "http://localhost:11434"
    
    val model = args.getOrNull(1)
        ?: System.getenv("OLLAMA_MODEL")
        ?: "qwen2:7b-instruct"
    
    println("🧠 Offline AI Assistant - Vegetarian Recipes")
    println("=" .repeat(60))
    println("Server: $baseUrl")
    println("Model: $model")
    println("Mode: Vegetarian recipes (eggs, fish, dairy allowed)")
    println("Type 'help' for commands, 'recipe' for recipe mode, 'exit' to quit")
    println("=" .repeat(60))
    println()

    // Use OkHttp implementation (avoids Ktor dependency issues)
    val llmClient = LlmClientOkHttp(
        baseUrl = baseUrl,
        model = model
    )

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
                        input.equals("exit", ignoreCase = true) || 
                        input.equals("quit", ignoreCase = true) -> {
                            running = false
                            println("Goodbye! 👋")
                        }
                        input.equals("help", ignoreCase = true) -> {
                            println()
                            println("Available commands:")
                            println("  help   - Show this help message")
                            println("  recipe - Generate a vegetarian recipe (optimized mode)")
                            println("  info   - Show server and model information")
                            println("  exit   - Exit the chat")
                            println("  quit   - Exit the chat")
                            println()
                            println("Recipe Mode:")
                            println("  Use 'recipe' command followed by your request")
                            println("  Example: recipe gluten-free dinner for two")
                            println()
                        }
                        input.equals("info", ignoreCase = true) -> {
                            println()
                            println("Server Information:")
                            println("  URL: $baseUrl")
                            println("  Model: $model")
                            println("  Mode: Vegetarian recipes")
                            println("  Allowed: Eggs, fish, dairy products")
                            println("  Prohibited: Meat, poultry")
                            println()
                        }
                        input.startsWith("recipe", ignoreCase = true) -> {
                            // Recipe mode with optimized parameters
                            val recipeRequest = input.substringAfter("recipe").trim()
                            if (recipeRequest.isBlank()) {
                                println("\nUsage: recipe <your request>")
                                println("Example: recipe gluten-free dinner for two")
                                continue
                            }
                            
                            try {
                                print("AI (Recipe Mode): ")
                                System.out.flush()
                                val response = llmClient.generate(
                                    prompt = recipeRequest,
                                    system = VegetarianRecipePrompt.SYSTEM_PROMPT,
                                    temperature = VegetarianRecipePrompt.OPTIMAL_TEMPERATURE,
                                    maxTokens = VegetarianRecipePrompt.OPTIMAL_MAX_TOKENS,
                                    topP = VegetarianRecipePrompt.OPTIMAL_TOP_P
                                )
                                println(response)
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: "Unknown error"
                                if (errorMsg.contains("timeout", ignoreCase = true)) {
                                    println("\n✗ Request timed out")
                                    println("  The server may be processing your request, but it's taking too long.")
                                } else {
                                    println("\n✗ Error: $errorMsg")
                                    println("  Check your connection to: $baseUrl")
                                }
                            }
                        }
                        else -> {
                            // Regular chat mode with vegetarian system prompt
                            try {
                                print("AI: ")
                                System.out.flush()
                                val response = llmClient.generate(
                                    prompt = input,
                                    system = VegetarianRecipePrompt.SYSTEM_PROMPT
                                )
                                println(response)
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: "Unknown error"
                                if (errorMsg.contains("timeout", ignoreCase = true)) {
                                    println("\n✗ Request timed out")
                                    println("  The server may be processing your request, but it's taking too long.")
                                } else {
                                    println("\n✗ Error: $errorMsg")
                                    println("  Check your connection to: $baseUrl")
                                }
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
