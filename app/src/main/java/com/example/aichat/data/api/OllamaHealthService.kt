package com.example.aichat.data.api

import android.util.Log
import com.example.aichat.data.Config
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Сервис для проверки доступности Ollama сервера и модели
 */
class OllamaHealthService(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    
    private val tag = "OllamaHealthService"
    
    /**
     * Результат проверки здоровья Ollama
     */
    data class HealthStatus(
        val isServerAvailable: Boolean,
        val serverVersion: String? = null,
        val isModelAvailable: Boolean = false,
        val modelName: String? = null,
        val availableModels: List<String> = emptyList(),
        val errorMessage: String? = null
    )
    
    /**
     * Проверяет доступность Ollama сервера и модели
     */
    suspend fun checkHealth(modelName: String = "qwen2:7b"): HealthStatus = withContext(Dispatchers.IO) {
        try {
            // 1. Проверяем доступность сервера через /api/version
            val serverStatus = checkServerHealth()
            if (!serverStatus.isServerAvailable) {
                return@withContext HealthStatus(
                    isServerAvailable = false,
                    errorMessage = serverStatus.errorMessage ?: "Сервер недоступен"
                )
            }
            
            // 2. Проверяем доступные модели
            val modelsStatus = checkAvailableModels()
            
            // 3. Проверяем доступность конкретной модели
            val modelAvailable = modelsStatus.availableModels.any { 
                it.equals(modelName, ignoreCase = true) || 
                it.contains(modelName, ignoreCase = true)
            }
            
            return@withContext HealthStatus(
                isServerAvailable = true,
                serverVersion = serverStatus.serverVersion,
                isModelAvailable = modelAvailable,
                modelName = if (modelAvailable) modelName else null,
                availableModels = modelsStatus.availableModels,
                errorMessage = if (!modelAvailable) {
                    "Модель '$modelName' не найдена. Доступные модели: ${modelsStatus.availableModels.joinToString(", ")}"
                } else null
            )
        } catch (e: Exception) {
            Log.e(tag, "Ошибка проверки здоровья Ollama", e)
            return@withContext HealthStatus(
                isServerAvailable = false,
                errorMessage = "Ошибка проверки: ${e.message}"
            )
        }
    }
    
    /**
     * Проверяет доступность сервера через /api/version
     */
    private suspend fun checkServerHealth(): HealthStatus = withContext(Dispatchers.IO) {
        try {
            // Получаем базовый URL без /v1/
            val baseUrl = Config.LOCAL_LLM_BASE_URL
                .removeSuffix("/v1/")
                .removeSuffix("/v1")
            
            val request = Request.Builder()
                .url("$baseUrl/api/version")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    try {
                        val versionResponse = gson.fromJson(responseBody, OllamaVersionResponse::class.java)
                        return@withContext HealthStatus(
                            isServerAvailable = true,
                            serverVersion = versionResponse.version
                        )
                    } catch (e: Exception) {
                        // Если не удалось распарсить, но ответ успешный - сервер работает
                        return@withContext HealthStatus(
                            isServerAvailable = true,
                            serverVersion = "Unknown"
                        )
                    }
                }
            }
            
            HealthStatus(
                isServerAvailable = false,
                errorMessage = "HTTP ${response.code}: ${response.message}"
            )
        } catch (e: IOException) {
            HealthStatus(
                isServerAvailable = false,
                errorMessage = "Не удалось подключиться к серверу: ${e.message}"
            )
        } catch (e: Exception) {
            HealthStatus(
                isServerAvailable = false,
                errorMessage = "Ошибка: ${e.message}"
            )
        }
    }
    
    /**
     * Проверяет доступные модели через /api/tags
     */
    private suspend fun checkAvailableModels(): ModelsStatus = withContext(Dispatchers.IO) {
        try {
            val baseUrl = Config.LOCAL_LLM_BASE_URL
                .removeSuffix("/v1/")
                .removeSuffix("/v1")
            
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    try {
                        val modelsResponse = gson.fromJson(responseBody, OllamaModelsResponse::class.java)
                        val modelNames = modelsResponse.models.map { it.name }
                        return@withContext ModelsStatus(
                            success = true,
                            availableModels = modelNames
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Ошибка парсинга списка моделей", e)
                    }
                }
            }
            
            ModelsStatus(
                success = false,
                availableModels = emptyList(),
                errorMessage = "HTTP ${response.code}: ${response.message}"
            )
        } catch (e: IOException) {
            ModelsStatus(
                success = false,
                availableModels = emptyList(),
                errorMessage = "Не удалось получить список моделей: ${e.message}"
            )
        } catch (e: Exception) {
            ModelsStatus(
                success = false,
                availableModels = emptyList(),
                errorMessage = "Ошибка: ${e.message}"
            )
        }
    }
    
    /**
     * Вспомогательный класс для статуса моделей
     */
    private data class ModelsStatus(
        val success: Boolean,
        val availableModels: List<String>,
        val errorMessage: String? = null
    )
}

/**
 * Ответ от Ollama /api/version
 */
private data class OllamaVersionResponse(
    val version: String
)

/**
 * Ответ от Ollama /api/tags
 */
private data class OllamaModelsResponse(
    val models: List<OllamaModel>
)

/**
 * Модель из списка Ollama
 */
private data class OllamaModel(
    val name: String,
    @SerializedName("modified_at")
    val modifiedAt: String? = null,
    val size: Long? = null
)
