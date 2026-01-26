package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity для хранения логов приложения
 */
@Entity(tableName = "app_logs")
data class AppLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Уровень логирования (DEBUG, INFO, WARN, ERROR)
     */
    val level: String,
    
    /**
     * Тег лога (обычно имя класса или компонента)
     */
    val tag: String,
    
    /**
     * Сообщение лога
     */
    val message: String,
    
    /**
     * Stack trace (если есть, для ошибок)
     */
    val stackTrace: String? = null,
    
    /**
     * Время создания лога
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Дополнительная информация (например, thread name)
     */
    val threadName: String? = null
)
