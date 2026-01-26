package com.example.aichat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Менеджер настроек для аналитика данных
 * Сохраняет настройки использования логов приложения
 */
class AnalystSettingsManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "analyst_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_USE_APP_LOGS = "use_app_logs"
        private const val KEY_MOCK_LOGS_CREATED = "mock_logs_created"
    }
    
    /**
     * Сохраняет настройку использования логов приложения
     */
    fun setUseAppLogs(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_USE_APP_LOGS, enabled)
            .apply()
    }
    
    /**
     * Получает настройку использования логов приложения
     */
    fun getUseAppLogs(): Boolean {
        return preferences.getBoolean(KEY_USE_APP_LOGS, false)
    }
    
    /**
     * Проверяет, были ли созданы моковые логи
     */
    fun areMockLogsCreated(): Boolean {
        return preferences.getBoolean(KEY_MOCK_LOGS_CREATED, false)
    }
    
    /**
     * Отмечает, что моковые логи были созданы
     */
    fun setMockLogsCreated(created: Boolean) {
        preferences.edit()
            .putBoolean(KEY_MOCK_LOGS_CREATED, created)
            .apply()
    }
}
