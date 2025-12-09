package com.example.aichat.data.api

import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for YandexGPT API (OpenAI-compatible)
 * Documentation: https://yandex.cloud/ru/docs/ai-studio/concepts/openai-compatibility
 */
interface YandexApiService {
    
    @POST("chat/completions")
    suspend fun sendChatRequest(
        @Header("Authorization") authorization: String,
        @Header("x-folder-id") folderId: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): Response<ChatResponse>
}

