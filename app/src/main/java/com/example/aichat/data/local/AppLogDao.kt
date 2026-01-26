package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с логами приложения
 */
@Dao
interface AppLogDao {
    
    /**
     * Вставляет новый лог
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AppLogEntity): Long
    
    /**
     * Вставляет несколько логов
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<AppLogEntity>)
    
    /**
     * Получает все логи
     */
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AppLogEntity>>
    
    /**
     * Получает логи по уровню
     */
    @Query("SELECT * FROM app_logs WHERE level = :level ORDER BY timestamp DESC")
    fun getLogsByLevel(level: String): Flow<List<AppLogEntity>>
    
    /**
     * Получает логи по тегу
     */
    @Query("SELECT * FROM app_logs WHERE tag = :tag ORDER BY timestamp DESC")
    fun getLogsByTag(tag: String): Flow<List<AppLogEntity>>
    
    /**
     * Получает логи за период времени
     */
    @Query("SELECT * FROM app_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<AppLogEntity>
    
    /**
     * Получает последние N логов
     */
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<AppLogEntity>
    
    /**
     * Получает все логи как список (для экспорта)
     */
    @Query("SELECT * FROM app_logs ORDER BY timestamp ASC")
    suspend fun getAllLogsList(): List<AppLogEntity>
    
    /**
     * Получает логи с ошибками (ERROR level)
     */
    @Query("SELECT * FROM app_logs WHERE level = 'ERROR' ORDER BY timestamp DESC")
    suspend fun getErrorLogs(): List<AppLogEntity>
    
    /**
     * Получает логи с предупреждениями (WARN level)
     */
    @Query("SELECT * FROM app_logs WHERE level = 'WARN' ORDER BY timestamp DESC")
    suspend fun getWarningLogs(): List<AppLogEntity>
    
    /**
     * Удаляет старые логи (старше указанного времени)
     */
    @Query("DELETE FROM app_logs WHERE timestamp < :beforeTime")
    suspend fun deleteOldLogs(beforeTime: Long)
    
    /**
     * Удаляет все логи
     */
    @Query("DELETE FROM app_logs")
    suspend fun deleteAllLogs()
    
    /**
     * Получает количество логов
     */
    @Query("SELECT COUNT(*) FROM app_logs")
    suspend fun getLogCount(): Int
    
    /**
     * Получает количество логов по уровню
     */
    @Query("SELECT COUNT(*) FROM app_logs WHERE level = :level")
    suspend fun getLogCountByLevel(level: String): Int
}
