package com.example.aichat.data.repository

import com.example.aichat.data.Config
import com.example.aichat.data.api.OpenAiApiService
import com.example.aichat.data.api.YandexApiService
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import com.example.aichat.data.model.ModelComparisonResult
import com.example.aichat.data.util.ModelCostCalculator
import com.example.aichat.data.util.TokenCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * Repository class that handles data operations for chat functionality
 */
class ChatRepository(
    private val apiService: OpenAiApiService,
    private val apiKey: String,
    private val yandexApiService: YandexApiService? = null
) {
    
    /**
     * Sends a chat request to the LLM and returns the response
     * 
     * @param messages List of chat messages (conversation history)
     * @param model The model to use (default: gpt-3.5-turbo)
     * @param maxTokens Maximum tokens in the response
     * @param temperature Temperature setting for the model (0.0 to 2.0)
     * @return Result containing either ChatResponse or an error message
     */
    suspend fun sendChatRequest(
        messages: List<ChatMessage>,
        model: String = "amazon/nova-2-lite-v1:free",
        maxTokens: Int? = 120000,
        temperature: Double? = null
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            // Estimate request tokens before sending
            val estimatedRequestTokens = TokenCounter.estimateRequestTokens(messages)
            
            val request = ChatRequest(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature
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
                // Add estimated request tokens to response
                val responseBody = response.body()!!
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
        model: String = "gpt-3.5-turbo",
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
}

