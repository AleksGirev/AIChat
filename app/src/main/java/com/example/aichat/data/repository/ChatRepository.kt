package com.example.aichat.data.repository

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.api.OpenAiApiService
import com.example.aichat.data.api.YandexApiService
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import com.example.aichat.data.model.FunctionCall
import com.example.aichat.data.model.ModelComparisonResult
import com.example.aichat.data.model.ToolCall
import com.example.aichat.data.util.ModelCostCalculator
import com.example.aichat.data.util.TokenCounter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * Repository class that handles data operations for chat functionality
 * Now includes MCP (Model Context Protocol) integration for tool calling
 */
class ChatRepository(
    private val apiService: OpenAiApiService,
    private val apiKey: String,
    private val yandexApiService: YandexApiService? = null,
    private val mcpRepository: McpRepository? = null, // Optional MCP repository
    private val gson: Gson
) {
    
    private val tag = "ChatRepository"
    
    /**
     * Sends a chat request to the LLM and returns the response
     * Handles MCP tool calling if MCP repository is available
     * 
     * @param messages List of chat messages (conversation history)
     * @param model The model to use (default: YandexGPT)
     * @param maxTokens Maximum tokens in the response
     * @param temperature Temperature setting for the model (0.0 to 2.0)
     * @param enableTools Whether to enable MCP tools (default: true if MCP is available)
     * @return Result containing either ChatResponse or an error message
     */
    suspend fun sendChatRequest(
        messages: List<ChatMessage>,
        model: String = Config.DEFAULT_YANDEXGPT_MODEL,
        maxTokens: Int? = 120000,
        temperature: Double? = null,
        enableTools: Boolean = true
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            // Get MCP tools if available and enabled
            val tools = if (enableTools &&  mcpRepository != null && mcpRepository.isConnected()) {
                mcpRepository.listTools().getOrNull()?.let { mcpTools ->
                    mcpRepository.convertToOpenAiTools(mcpTools)
                }
            } else {
                null
            }
            Log.d("GIREV", "enabled tools $enableTools")
            Log.d("GIREV", "mcpRepository != null ${mcpRepository != null}")
            Log.d("GIREV", "mcpRepository.isConnected() ${mcpRepository?.isConnected()}")

            // Estimate request tokens before sending
            val estimatedRequestTokens = TokenCounter.estimateRequestTokens(messages)
            
            val request = ChatRequest(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature,
                tools = tools
            )
            
            // Route to appropriate API based on model name
            val response = if (model.lowercase().contains("yandex") || model.startsWith("gpt://")) {
                // YandexGPT API
                if (yandexApiService == null) {
                    return@withContext Result.failure(Exception("YandexGPT API service not configured"))
                }
                val authorization = "Bearer ${Config.YANDEX_IAM_TOKEN}"
                yandexApiService.sendChatRequest(
                    authorization = authorization,
                    folderId = Config.YANDEX_FOLDER_ID,
                    request = request
                )
            } else {
                // OpenRouter API (for amazon/nova-2-lite-v1:free and others)
                val authorization = "Bearer $apiKey"
                apiService.sendChatRequest(
                    authorization = authorization,
                    request = request
                )
            }
            
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                
                // Check if LLM wants to call tools
                val assistantMessage = responseBody.choices.firstOrNull()?.message
                val toolCalls = assistantMessage?.toolCalls
                
                if (toolCalls != null && toolCalls.isNotEmpty() && mcpRepository != null) {
                    // LLM wants to call tools - execute them and send results back
                    Log.d(tag, "LLM requested ${toolCalls.size} tool calls")
                    return@withContext handleToolCalls(messages, toolCalls, model, maxTokens, temperature)
                }
                
                // Add estimated request tokens to response
                val responseWithTokens = responseBody.copy(
                    estimatedRequestTokens = estimatedRequestTokens
                )
                Result.success(responseWithTokens)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("API Error: ${response.code()} - $errorBody"))
            }
        } catch (e: HttpException) {
            Result.failure(Exception("HTTP Error: ${e.code()} - ${e.message()}"))
        } catch (e: IOException) {
            Result.failure(Exception("Network Error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected Error: ${e.message}"))
        }
    }
    
    /**
     * Convenience method to send a single user message and get a response
     * 
     * @param userMessage The user's message
     * @param conversationHistory Optional conversation history
     * @param model The model to use
     * @return Result containing the assistant's response message or an error
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        model: String = Config.DEFAULT_YANDEXGPT_MODEL,
        maxTokens: Int? = null,
        temperature: Double? = null
    ): Result<String> {
        val messages = conversationHistory.toMutableList().apply {
            add(ChatMessage(role = "user", content = userMessage))
        }
        
        val result = sendChatRequest(messages, model, maxTokens, temperature)
        
        return if (result.isSuccess) {
            val responseMessage = result.getOrNull()?.choices?.firstOrNull()?.message?.content
            if (responseMessage != null) {
                Result.success(responseMessage)
            } else {
                Result.failure(Exception("No response message in API response"))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }
    
    /**
     * Compares multiple models by sending the same request to each model in parallel
     * 
     * @param messages List of chat messages (conversation history)
     * @param modelNames List of model names to compare
     * @param maxTokens Maximum tokens in the response
     * @param temperature Temperature setting for the model (0.0 to 2.0)
     * @return List of ModelComparisonResult, one for each model
     */
    suspend fun compareModels(
        messages: List<ChatMessage>,
        modelNames: List<String>,
        maxTokens: Int? = 120000,
        temperature: Double? = null
    ): List<ModelComparisonResult> = withContext(Dispatchers.IO) {
        // Execute all requests in parallel using async
        val deferredResults = modelNames.map { modelName ->
            async {
                var responseTimeMs = 0L
                var response: ChatResponse? = null
                var error: Exception? = null
                
                try {
                    responseTimeMs = measureTimeMillis {
                        val request = ChatRequest(
                            model = modelName,
                            messages = messages,
                            maxTokens = maxTokens,
                            temperature = temperature
                        )
                        
                        // Route to appropriate API based on model name
                        val apiResponse = if (modelName.lowercase().contains("yandex") || modelName == "yandexgpt") {
                            // YandexGPT API
                            if (yandexApiService == null) {
                                throw Exception("YandexGPT API service not configured")
                            }
                            val authorization = "Bearer ${Config.YANDEX_IAM_TOKEN}"
                            yandexApiService.sendChatRequest(
                                authorization = authorization,
                                folderId = Config.YANDEX_FOLDER_ID,
                                request = request
                            )
                        } else {
                            // OpenRouter API (for amazon/nova-2-lite-v1:free)
                            val authorization = "Bearer $apiKey"
                            apiService.sendChatRequest(
                                authorization = authorization,
                                request = request
                            )
                        }
                        
                        if (apiResponse.isSuccessful && apiResponse.body() != null) {
                            response = apiResponse.body()!!
                        } else {
                            val errorBody = apiResponse.errorBody()?.string() ?: "Unknown error"
                            error = Exception("API Error: ${apiResponse.code()} - $errorBody")
                        }
                    }
                } catch (e: HttpException) {
                    error = Exception("HTTP Error: ${e.code()} - ${e.message()}")
                } catch (e: IOException) {
                    error = Exception("Network Error: ${e.message}")
                } catch (e: Exception) {
                    error = Exception("Unexpected Error: ${e.message}")
                }
                
                // Build result
                if (error != null || response == null) {
                    ModelComparisonResult(
                        modelName = modelName,
                        responseContent = null,
                        responseTimeMs = responseTimeMs,
                        tokenCount = 0,
                        cost = 0.0,
                        error = error?.message ?: "Unknown error",
                        isSuccess = false
                    )
                } else {
                    val responseContent = response.choices.firstOrNull()?.message?.content
                    val usage = response.usage
                    val tokenCount = usage?.totalTokens ?: (usage?.promptTokens ?: 0) + (usage?.completionTokens ?: 0)
                    val cost = ModelCostCalculator.calculateCost(modelName, usage)
                    
                    ModelComparisonResult(
                        modelName = modelName,
                        responseContent = responseContent,
                        responseTimeMs = responseTimeMs,
                        tokenCount = tokenCount,
                        cost = cost,
                        error = null,
                        isSuccess = true
                    )
                }
            }
        }
        
        // Wait for all requests to complete and return results
        deferredResults.awaitAll()
    }
    
    /**
     * Handles tool calls from LLM response
     * Executes tools via MCP and sends results back to LLM
     */
    private suspend fun handleToolCalls(
        originalMessages: List<ChatMessage>,
        toolCalls: List<ToolCall>,
        model: String,
        maxTokens: Int?,
        temperature: Double?
    ): Result<ChatResponse> {
        if (mcpRepository == null) {
            return Result.failure(Exception("MCP repository not available"))
        }
        
        try {
            // Build messages with assistant's tool call request
            val messagesWithToolCalls = originalMessages.toMutableList().apply {
                add(ChatMessage(
                    role = "assistant",
                    content = null,
                    toolCalls = toolCalls
                ))
            }
            
            // Execute all tool calls in parallel
            val toolResults = coroutineScope {
                toolCalls.map { toolCall ->
                    async(Dispatchers.IO) {
                        try {
                            val functionCall = toolCall.function
                            val toolName = functionCall.name
                            
                            // Parse arguments JSON string
                            val arguments = try {
                                @Suppress("UNCHECKED_CAST")
                                gson.fromJson(functionCall.arguments, Map::class.java) as? Map<String, Any>
                                    ?: emptyMap<String, Any>()
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to parse tool arguments", e)
                                emptyMap<String, Any>()
                            }
                            
                            Log.d(tag, "Calling tool: $toolName with args: $arguments")
                            
                            // Call tool via MCP
                            val toolResult = mcpRepository.callTool(toolName, arguments)
                            
                            toolResult.getOrNull()?.let { result ->
                                // Convert tool result to string content
                                val content = result.content.joinToString("\n") { contentItem ->
                                    contentItem.text ?: contentItem.data ?: ""
                                }
                                
                                // Return tool message for LLM
                                ChatMessage(
                                    role = "tool",
                                    content = content,
                                    toolCallId = toolCall.id
                                )
                            } ?: run {
                                // Tool execution failed
                                ChatMessage(
                                    role = "tool",
                                    content = "Tool execution failed: ${toolResult.exceptionOrNull()?.message}",
                                    toolCallId = toolCall.id
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error executing tool call", e)
                            ChatMessage(
                                role = "tool",
                                content = "Error: ${e.message}",
                                toolCallId = toolCall.id
                            )
                        }
                    }
                }.awaitAll()
            }
            
            // Add tool results to messages
            messagesWithToolCalls.addAll(toolResults)
            
            // Send updated messages back to LLM
            Log.d(tag, "Sending tool results back to LLM")
            return sendChatRequest(
                messages = messagesWithToolCalls,
                model = model,
                maxTokens = maxTokens,
                temperature = temperature,
                enableTools = false // Disable tools on second request to avoid loops
            )
        } catch (e: Exception) {
            Log.e(tag, "Error handling tool calls", e)
            return Result.failure(e)
        }
    }
}

