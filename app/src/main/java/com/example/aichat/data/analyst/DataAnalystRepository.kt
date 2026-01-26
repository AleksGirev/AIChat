package com.example.aichat.data.analyst

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.api.LocalLLMApiService
import com.example.aichat.data.local.AppLogRepository
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequest
import com.example.aichat.data.model.ChatResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Репозиторий для анализа данных с помощью локальной LLM
 */
class DataAnalystRepository(
    private val localLLMApiService: LocalLLMApiService,
    private val dataParser: DataParser,
    private val appLogRepository: AppLogRepository,
    private val gson: Gson
) {
    
    private val tag = "DataAnalystRepository"
    private var currentData: ParsedData? = null
    private var useAppLogs: Boolean = false
    
    /**
     * Устанавливает данные для анализа
     */
    fun setData(parsedData: ParsedData) {
        currentData = parsedData
        Log.d(tag, "Данные установлены: ${parsedData.format}")
    }
    
    /**
     * Получает текущие данные
     */
    fun getCurrentData(): ParsedData? = currentData
    
    /**
     * Включает/выключает использование логов приложения
     */
    fun setUseAppLogs(enabled: Boolean) {
        useAppLogs = enabled
        Log.d(tag, "Использование логов приложения: $enabled")
    }
    
    /**
     * Анализирует данные с помощью локальной LLM
     */
    suspend fun analyzeData(question: String): Result<String> = withContext(Dispatchers.IO) {
        // Если включены логи приложения и нет загруженных данных, используем логи
        val data = if (useAppLogs && currentData == null) {
            loadAppLogsAsData()
        } else {
            currentData
        }
        
        if (data == null) {
            return@withContext Result.failure(Exception("Данные не загружены. Пожалуйста, сначала загрузите файл или включите использование логов приложения."))
        }
        
        try {
            // Формируем контекст с данными для LLM
            val context = buildDataContext(data)
            
            // Формируем системный промпт для аналитика
            val systemPrompt = """
                Ты - опытный аналитик данных. Твоя задача - анализировать предоставленные данные и отвечать на вопросы пользователя.
                
                Доступные данные:
                $context
                
                Отвечай точно, используя конкретные числа и факты из данных. Если информации недостаточно, честно скажи об этом.
            """.trimIndent()
            
            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = question)
            )
            
            // Используем модель без префикса "local:" для прямого вызова API
            // Префикс нужен только для маршрутизации в ChatRepository
            val modelName = "qwen2:7b"
            
            val request = ChatRequest(
                model = modelName,
                messages = messages,
                maxTokens = 2000,
                temperature = 0.3 // Низкая температура для более точных ответов
            )
            
            val authorization = Config.LOCAL_LLM_API_KEY?.let { "Bearer $it" }
            val response = localLLMApiService.sendChatRequest(
                authorization = authorization,
                request = request
            )
            
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val answer = responseBody.choices.firstOrNull()?.message?.content
                    ?: "Не удалось получить ответ от модели"
                Result.success(answer)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Ошибка API: ${response.code()} - $errorBody"))
            }
        } catch (e: HttpException) {
            Log.e(tag, "HTTP ошибка", e)
            Result.failure(Exception("HTTP ошибка: ${e.code()} - ${e.message}"))
        } catch (e: IOException) {
            Log.e(tag, "Ошибка сети", e)
            Result.failure(Exception("Ошибка сети: ${e.message}. Убедитесь, что локальная LLM сервер запущен."))
        } catch (e: Exception) {
            Log.e(tag, "Неожиданная ошибка", e)
            Result.failure(Exception("Ошибка: ${e.message}"))
        }
    }
    
    /**
     * Формирует контекст из данных для LLM
     */
    private fun buildDataContext(data: ParsedData): String {
        return when (data) {
            is ParsedData.Structured -> {
                buildString {
                    appendLine("Формат: ${data.format}")
                    appendLine(data.summary)
                    appendLine("\nПервые 10 строк данных:")
                    data.rows.take(10).forEachIndexed { index, row ->
                        appendLine("Строка ${index + 1}:")
                        row.forEach { (key, value) ->
                            appendLine("  $key: $value")
                        }
                    }
                    if (data.rows.size > 10) {
                        appendLine("\n... и еще ${data.rows.size - 10} строк")
                    }
                }
            }
            is ParsedData.Log -> {
                buildString {
                    appendLine("Формат: Логи")
                    appendLine(data.summary)
                    appendLine("\nПримеры ошибок:")
                    data.errors.take(5).forEach { error ->
                        appendLine("  - $error")
                    }
                    if (data.errors.size > 5) {
                        appendLine("  ... и еще ${data.errors.size - 5} ошибок")
                    }
                }
            }
            is ParsedData.Text -> {
                "Текстовые данные:\n${data.content.take(5000)}" // Ограничиваем размер
            }
            is ParsedData.Empty -> {
                "Данные пусты: ${data.message}"
            }
        }
    }
    
    /**
     * Загружает логи приложения и преобразует их в ParsedData
     */
    private suspend fun loadAppLogsAsData(): ParsedData? = withContext(Dispatchers.IO) {
        try {
            val logs = appLogRepository.getAllLogsList()
            if (logs.isEmpty()) {
                return@withContext null
            }
            
            // Преобразуем логи в текстовый формат
            val logContent = buildString {
                logs.forEach { log ->
                    appendLine("${log.timestamp} [${log.level}] ${log.tag}: ${log.message}")
                    log.stackTrace?.let {
                        appendLine(it)
                    }
                }
            }
            
            // Парсим как логи
            val parsedData = dataParser.parseLogs(logContent)
            currentData = parsedData
            parsedData
        } catch (e: Exception) {
            Log.e(tag, "Ошибка загрузки логов приложения", e)
            null
        }
    }
    
    /**
     * Очищает загруженные данные
     */
    fun clearData() {
        currentData = null
        useAppLogs = false
    }
}
