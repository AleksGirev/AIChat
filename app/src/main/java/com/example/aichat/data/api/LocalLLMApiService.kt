package com.example.aichat.data.api

import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for Local LLM API (OpenAI-compatible)
 * 
 * Supports local LLM servers like:
 * - Ollama (http://localhost:11434/v1)
 * - LM Studio (http://localhost:1234/v1)
 * - vLLM (http://localhost:8000/v1)
 * - Any other OpenAI-compatible local server
 * 
 * For Android Emulator: Use http://10.0.2.2:PORT/v1 (10.0.2.2 maps to host machine's localhost)
 * For Physical Device: Use http://YOUR_COMPUTER_IP:PORT/v1
 */
interface LocalLLMApiService {
    
    @POST("chat/completions")
    suspend fun sendChatRequest(
        @Header("Authorization") authorization: String? = null, // Optional, most local LLMs don't require auth
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): Response<ChatResponse>
}
