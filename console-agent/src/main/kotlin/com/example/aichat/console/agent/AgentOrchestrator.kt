package com.example.aichat.console.agent

import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.mcp.McpClientWrapper
import com.example.aichat.console.model.*
import com.example.aichat.console.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Orchestrates interactions between LLM and MCP server
 * Handles the full cycle: user input -> LLM -> tool execution -> LLM response
 */
class AgentOrchestrator(
    private val llmClient: OpenAiClient,
    private val mcpClient: McpClientWrapper? // Using official SDK wrapper
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var availableTools: List<com.example.aichat.console.model.McpTool> = emptyList()
    private var conversationHistory = mutableListOf<ChatMessage>()
    
    /**
     * Initializes the orchestrator by fetching available tools from MCP
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (mcpClient != null) {
                println("[Agent]: Получаю инструменты от MCP...")
                
                val toolsResult = mcpClient.listTools()
                if (toolsResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch tools: ${toolsResult.exceptionOrNull()?.message}")
                    )
                }
                
                availableTools = toolsResult.getOrThrow()
                println("[MCP]: Доступны инструменты: ${availableTools.joinToString(", ") { it.name }}")
            } else {
                // No MCP client - run without tools
                availableTools = emptyList()
                println("[Agent]: Running without MCP tools")
            }
            
            // Add system message with tool descriptions
            val systemMessage = createSystemMessage(availableTools)
            conversationHistory.clear()
            conversationHistory.add(systemMessage)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Processes a user command through the agent
     */
    suspend fun processCommand(userCommand: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Add user message
            conversationHistory.add(ChatMessage(role = "user", content = userCommand))
            
            // Convert MCP tools to OpenAI format
            val openAiTools = llmClient.convertMcpToolsToOpenAiTools(availableTools)
            
            var maxIterations = 10 // Prevent infinite loops
            var iteration = 0
            
            while (iteration < maxIterations) {
                iteration++
                
                // Get LLM response
                val responseResult = llmClient.chatCompletion(
                    messages = conversationHistory,
                    tools = openAiTools
                )
                
                if (responseResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("LLM error: ${responseResult.exceptionOrNull()?.message}")
                    )
                }
                
                val response = responseResult.getOrThrow()
                
                if (response.choices.isEmpty()) {
                    return@withContext Result.failure(Exception("Empty response from LLM"))
                }
                
                val choice = response.choices[0]
                val assistantMessage = choice.message
                
                // Add assistant message to history
                conversationHistory.add(assistantMessage)
                
                // Check if LLM wants to call tools
                if (assistantMessage.toolCalls != null && assistantMessage.toolCalls.isNotEmpty()) {
                    if (mcpClient == null) {
                        println("[LLM]: LLM requested tools, but MCP is not available: ${assistantMessage.toolCalls.joinToString { it.function.name }}")
                        println("[Agent]: Ignoring tool calls and returning LLM response directly")
                    } else {
                        println("[LLM]: Вызываю инструменты: ${assistantMessage.toolCalls.joinToString { it.function.name }}")
                        
                        // Execute all tool calls
                        val toolResults = mutableListOf<ChatMessage>()
                        
                        for (toolCall in assistantMessage.toolCalls) {
                            val toolName = toolCall.function.name
                            val toolArgumentsJson = toolCall.function.arguments
                            
                            // Parse arguments
                            val arguments = try {
                                val jsonElement = json.parseToJsonElement(toolArgumentsJson)
                                if (jsonElement is kotlinx.serialization.json.JsonObject) {
                                    JsonUtils.jsonObjectToMap(jsonElement) ?: emptyMap()
                                } else {
                                    emptyMap()
                                }
                            } catch (e: Exception) {
                                println("[Agent]: Failed to parse tool arguments: ${e.message}")
                                emptyMap()
                            }
                            
                            // Call MCP tool
                            val toolResult = mcpClient.callTool(toolName, arguments)
                            
                            if (toolResult.isSuccess) {
                                val result = toolResult.getOrThrow()
                                val resultText = result.content.joinToString("\n") { it.text ?: "" }
                                
                                println("[MCP]: ${resultText.take(100)}${if (resultText.length > 100) "..." else ""}")
                                
                                // Add tool result message
                                toolResults.add(
                                    ChatMessage(
                                        role = "tool",
                                        toolCallId = toolCall.id,
                                        content = resultText
                                    )
                                )
                            } else {
                                val error = toolResult.exceptionOrNull()?.message ?: "Unknown error"
                                println("[MCP]: Ошибка выполнения инструмента: $error")
                                
                                toolResults.add(
                                    ChatMessage(
                                        role = "tool",
                                        toolCallId = toolCall.id,
                                        content = "Error: $error"
                                    )
                                )
                            }
                        }
                        
                        // Add tool results to conversation
                        conversationHistory.addAll(toolResults)
                        
                        // Continue loop to get LLM response to tool results
                        continue
                    }
                }
                
                // No tool calls, return final response
                val finalResponse = assistantMessage.content ?: "No response content"
                return@withContext Result.success(finalResponse)
            }
            
            Result.failure(Exception("Max iterations reached"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Creates system message with tool descriptions
     */
    private fun createSystemMessage(tools: List<com.example.aichat.console.model.McpTool>): ChatMessage {
        val systemPrompt = if (tools.isEmpty()) {
            // No tools available - simple chat mode
            """
            You are a helpful AI assistant.
            Respond to user questions and requests in a clear and helpful manner.
            Respond in the same language as the user's command.
            """.trimIndent()
        } else {
            val toolsDescription = tools.joinToString("\n") { tool ->
                "- ${tool.name}: ${tool.description ?: "No description"}"
            }
            
            """
            You are an AI assistant that controls mobile devices through MCP (Model Context Protocol) tools.
            You can execute commands on connected Android/iOS devices or emulators.
            
            Available tools:
            $toolsDescription
            
            When the user gives you a natural language command, analyze it and use the appropriate tools to execute it.
            You can chain multiple tool calls if needed to complete the task.
            Always provide clear feedback about what you're doing.
            
            Respond in the same language as the user's command.
            """.trimIndent()
        }
        
        return ChatMessage(role = "system", content = systemPrompt)
    }
    
    /**
     * Clears conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
        // Re-add system message
        val systemMessage = createSystemMessage(availableTools)
        conversationHistory.add(systemMessage)
    }
    
    /**
     * Gets the current conversation history (for RAG integration)
     */
    fun getConversationHistory(): List<ChatMessage> {
        return conversationHistory.toList()
    }
    
    /**
     * Sets the conversation history (for RAG integration)
     */
    fun setConversationHistory(history: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(history)
    }
    
    /**
     * Shuts down the orchestrator
     */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        mcpClient?.disconnect()
    }
}
