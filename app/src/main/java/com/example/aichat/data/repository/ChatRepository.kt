package com.example.aichat.data.repository

import com.example.aichat.data.api.OpenAiApiService
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Repository class that handles data operations for chat functionality
 */
class ChatRepository(
    private val apiService: OpenAiApiService,
    private val apiKey: String
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
            val request = ChatRequest(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature
            )
            
            val authorization = "Bearer $apiKey"
            val response = apiService.sendChatRequest(
                authorization = authorization,
                request = request
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
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
}

