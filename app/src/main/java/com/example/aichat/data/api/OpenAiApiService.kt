package com.example.aichat.data.api

import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for OpenAI Chat API
 */
interface OpenAiApiService {
    
    @POST("v1/chat/completions")
    suspend fun sendChatRequest(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): Response<ChatResponse>
}






