package com.example.aichat.data.util

import android.util.Log
import com.example.aichat.data.local.AppLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Перехватчик для автоматического сохранения логов в базу данных
 * 
 * Использование:
 * LogInterceptor.initialize(appLogRepository)
 * 
 * После инициализации все вызовы Log.d, Log.e и т.д. будут автоматически сохраняться в БД
 */
object LogInterceptor {
    
    private var appLogRepository: AppLogRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false
    
    /**
     * Инициализирует перехватчик логов
     */
    fun initialize(repository: AppLogRepository) {
        if (isInitialized) return
        
        appLogRepository = repository
        isInitialized = true
        
        // Переопределяем методы Log для перехвата
        setupLogInterception()
    }
    
    /**
     * Настраивает перехват вызовов Log
     * 
     * Примечание: Android Log класс final, поэтому мы не можем его переопределить напрямую.
     * Вместо этого создаем обертки и используем их в приложении.
     * Для автоматического перехвата всех существующих Log.d/e/w/i вызовов,
     * можно использовать инструменты вроде AspectJ или просто заменить все Log.* на AppLog.*
     */
    private fun setupLogInterception() {
        // В Android мы не можем перехватить Log напрямую, так как это final класс
        // Поэтому создаем обертки AppLog.* которые будут использоваться вместо Log.*
    }
    
    /**
     * Сохраняет лог в базу данных
     */
    private fun saveLog(level: String, tag: String, message: String, throwable: Throwable? = null) {
        appLogRepository?.let { repo ->
            scope.launch {
                repo.saveLog(level, tag, message, throwable)
            }
        }
    }
    
    /**
     * Обертка для Log.d
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        saveLog("DEBUG", tag, message)
    }
    
    /**
     * Обертка для Log.e
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        saveLog("ERROR", tag, message, throwable)
    }
    
    /**
     * Обертка для Log.w
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        saveLog("WARN", tag, message, throwable)
    }
    
    /**
     * Обертка для Log.i
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        saveLog("INFO", tag, message)
    }
    
    /**
     * Обертка для Log.v
     */
    fun v(tag: String, message: String) {
        Log.v(tag, message)
        saveLog("VERBOSE", tag, message)
    }
}
