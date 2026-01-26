package com.example.aichat.data.analyst

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Загружает данные из различных форматов файлов (CSV, JSON, TXT для логов)
 */
class DataLoader(private val context: Context) {
    
    private val tag = "DataLoader"
    
    /**
     * Загружает данные из URI (файл, выбранный пользователем)
     */
    suspend fun loadFromUri(uri: Uri): LoadedData = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Не удалось открыть файл")
            
            val fileName = getFileName(uri)
            val fileExtension = fileName.substringAfterLast('.', "").lowercase()
            
            val content = inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
            
            when (fileExtension) {
                "csv" -> LoadedData.CsvData(fileName, content)
                "json" -> LoadedData.JsonData(fileName, content)
                "txt", "log" -> LoadedData.LogData(fileName, content)
                else -> LoadedData.TextData(fileName, content)
            }
        } catch (e: Exception) {
            Log.e(tag, "Ошибка загрузки файла", e)
            throw Exception("Ошибка загрузки файла: ${e.message}")
        }
    }
    
    /**
     * Получает имя файла из URI
     */
    private fun getFileName(uri: Uri): String {
        var fileName = uri.lastPathSegment ?: "unknown"
        
        // Пытаемся получить имя файла из ContentResolver
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                fileName = it.getString(nameIndex) ?: fileName
            }
        }
        
        return fileName
    }
}

/**
 * Представляет загруженные данные в различных форматах
 */
sealed class LoadedData {
    abstract val fileName: String
    abstract val rawContent: String
    
    data class CsvData(
        override val fileName: String,
        override val rawContent: String
    ) : LoadedData()
    
    data class JsonData(
        override val fileName: String,
        override val rawContent: String
    ) : LoadedData()
    
    data class LogData(
        override val fileName: String,
        override val rawContent: String
    ) : LoadedData()
    
    data class TextData(
        override val fileName: String,
        override val rawContent: String
    ) : LoadedData()
}
