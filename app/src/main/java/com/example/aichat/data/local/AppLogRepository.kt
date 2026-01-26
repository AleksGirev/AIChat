package com.example.aichat.data.local

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Репозиторий для работы с логами приложения
 */
class AppLogRepository(private val appLogDao: AppLogDao) {
    
    /**
     * Сохраняет лог в базу данных
     */
    suspend fun saveLog(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val stackTrace = throwable?.let {
                Log.getStackTraceString(it)
            }
            
            val threadName = Thread.currentThread().name
            
            val logEntity = AppLogEntity(
                level = level,
                tag = tag,
                message = message,
                stackTrace = stackTrace,
                threadName = threadName
            )
            
            appLogDao.insertLog(logEntity)
        } catch (e: Exception) {
            // Не логируем ошибки сохранения логов, чтобы избежать рекурсии
            android.util.Log.e("AppLogRepository", "Failed to save log", e)
        }
    }
    
    /**
     * Получает все логи как Flow
     */
    fun getAllLogs(): Flow<List<AppLogEntity>> = appLogDao.getAllLogs()
    
    /**
     * Получает логи по уровню
     */
    fun getLogsByLevel(level: String): Flow<List<AppLogEntity>> = appLogDao.getLogsByLevel(level)
    
    /**
     * Получает логи по тегу
     */
    fun getLogsByTag(tag: String): Flow<List<AppLogEntity>> = appLogDao.getLogsByTag(tag)
    
    /**
     * Получает логи за период времени
     */
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<AppLogEntity> =
        withContext(Dispatchers.IO) {
            appLogDao.getLogsByTimeRange(startTime, endTime)
        }
    
    /**
     * Получает последние N логов
     */
    suspend fun getRecentLogs(limit: Int = 1000): List<AppLogEntity> = withContext(Dispatchers.IO) {
        appLogDao.getRecentLogs(limit)
    }
    
    /**
     * Получает все логи как список (для экспорта)
     */
    suspend fun getAllLogsList(): List<AppLogEntity> = withContext(Dispatchers.IO) {
        appLogDao.getAllLogsList()
    }
    
    /**
     * Получает логи с ошибками
     */
    suspend fun getErrorLogs(): List<AppLogEntity> = withContext(Dispatchers.IO) {
        appLogDao.getErrorLogs()
    }
    
    /**
     * Получает логи с предупреждениями
     */
    suspend fun getWarningLogs(): List<AppLogEntity> = withContext(Dispatchers.IO) {
        appLogDao.getWarningLogs()
    }
    
    /**
     * Удаляет старые логи (старше указанного количества дней)
     */
    suspend fun deleteOldLogs(daysToKeep: Int = 7) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        appLogDao.deleteOldLogs(cutoffTime)
    }
    
    /**
     * Удаляет все логи
     */
    suspend fun deleteAllLogs() = withContext(Dispatchers.IO) {
        appLogDao.deleteAllLogs()
    }
    
    /**
     * Получает количество логов
     */
    suspend fun getLogCount(): Int = withContext(Dispatchers.IO) {
        appLogDao.getLogCount()
    }
    
    /**
     * Получает количество логов по уровню
     */
    suspend fun getLogCountByLevel(level: String): Int = withContext(Dispatchers.IO) {
        appLogDao.getLogCountByLevel(level)
    }
    
    /**
     * Экспортирует логи в текстовый формат
     */
    suspend fun exportLogsAsText(): String = withContext(Dispatchers.IO) {
        val logs = appLogDao.getAllLogsList()
        buildString {
            logs.forEach { log ->
                appendLine("${log.timestamp} [${log.level}] ${log.tag}: ${log.message}")
                log.stackTrace?.let {
                    appendLine(it)
                }
            }
        }
    }
    
    /**
     * Создает моковые логи для демонстрации
     */
    suspend fun createMockLogs() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val hourMs = 60 * 60 * 1000L
        val dayMs = 24 * hourMs
        
        val mockLogs = mutableListOf<AppLogEntity>()
        
        // Логи за последние 3 дня
        for (day in 0..2) {
            val dayStart = currentTime - (day * dayMs)
            
            // INFO логи - нормальная работа приложения
            for (i in 0..15) {
                val timestamp = dayStart + (i * hourMs / 2)
                mockLogs.add(AppLogEntity(
                    level = "INFO",
                    tag = "ChatRepository",
                    message = "Отправка сообщения пользователю. Модель: qwen2:7b, токены: ${150 + i * 10}",
                    timestamp = timestamp,
                    threadName = "main"
                ))
            }
            
            // DEBUG логи
            for (i in 0..20) {
                val timestamp = dayStart + (i * hourMs / 3)
                mockLogs.add(AppLogEntity(
                    level = "DEBUG",
                    tag = "DataAnalystRepository",
                    message = "Анализ данных: обработано ${i * 5} записей",
                    timestamp = timestamp,
                    threadName = "background"
                ))
            }
            
            // WARN логи - предупреждения
            for (i in 0..5) {
                val timestamp = dayStart + (i * 2 * hourMs)
                mockLogs.add(AppLogEntity(
                    level = "WARN",
                    tag = "NetworkModule",
                    message = "Медленный ответ от API: ${2000 + i * 100}ms",
                    timestamp = timestamp,
                    threadName = "network"
                ))
            }
            
            // ERROR логи - ошибки
            val errorMessages = listOf(
                "NullPointerException: Attempt to invoke virtual method 'toString()' on a null object reference",
                "IOException: Failed to connect to localhost/127.0.0.1:11434",
                "IllegalArgumentException: Model 'qwen2:7b' not found",
                "SQLException: Database locked",
                "TimeoutException: Request timeout after 30 seconds"
            )
            
            for (i in 0..3) {
                val timestamp = dayStart + (i * 3 * hourMs)
                val errorMsg = errorMessages[i % errorMessages.size]
                mockLogs.add(AppLogEntity(
                    level = "ERROR",
                    tag = if (i % 2 == 0) "ChatRepository" else "LocalLLMApiService",
                    message = errorMsg,
                    stackTrace = """
                        java.lang.RuntimeException: $errorMsg
                            at com.example.aichat.data.repository.ChatRepository.sendChatRequest(ChatRepository.kt:95)
                            at com.example.aichat.ui.viewmodel.ChatViewModel.sendMessage(ChatViewModel.kt:362)
                            at com.example.aichat.ui.chat.ChatScreen$1.invoke(ChatScreen.kt:245)
                        Caused by: java.net.ConnectException: Connection refused
                            at java.net.Socket.connect(Socket.java:589)
                            at okhttp3.internal.platform.Platform.connectSocket(Platform.kt:129)
                    """.trimIndent(),
                    timestamp = timestamp,
                    threadName = "main"
                ))
            }
        }
        
        // Вставляем все логи
        appLogDao.insertLogs(mockLogs)
    }
}
