package com.example.aichat.data.analyst

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Парсит данные из различных форматов и извлекает структурированную информацию
 */
class DataParser(private val gson: Gson) {
    
    private val tag = "DataParser"
    
    /**
     * Парсит CSV данные и возвращает структурированное представление
     */
    fun parseCsv(content: String): ParsedData {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParsedData.Empty("CSV файл пуст")
        }
        
        val headers = lines.first().split(",").map { it.trim() }
        val rows = lines.drop(1).map { line ->
            // Простой парсинг CSV (для сложных случаев можно использовать библиотеку)
            val values = parseCsvLine(line)
            headers.zip(values).toMap()
        }
        
        return ParsedData.Structured(
            format = "CSV",
            headers = headers,
            rows = rows,
            summary = generateCsvSummary(headers, rows)
        )
    }
    
    /**
     * Парсит одну строку CSV (учитывает кавычки)
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }
    
    /**
     * Парсит JSON данные
     */
    fun parseJson(content: String): ParsedData {
        return try {
            val jsonElement = JsonParser.parseString(content)
            
            when {
                jsonElement.isJsonArray -> {
                    val array = jsonElement.asJsonArray
                    val items = array.map { it.asJsonObject }
                    val headers = if (items.isNotEmpty()) {
                        items.first().keySet().toList()
                    } else {
                        emptyList()
                    }
                    
                    val rows = items.map { obj ->
                        headers.associateWith { key ->
                            obj[key]?.asString ?: obj[key]?.toString() ?: ""
                        }
                    }
                    
                    ParsedData.Structured(
                        format = "JSON Array",
                        headers = headers,
                        rows = rows,
                        summary = generateJsonSummary(items)
                    )
                }
                jsonElement.isJsonObject -> {
                    val obj = jsonElement.asJsonObject
                    val headers = obj.keySet().toList()
                    val rows = listOf(
                        headers.associateWith { key ->
                            obj[key]?.asString ?: obj[key]?.toString() ?: ""
                        }
                    )
                    
                    ParsedData.Structured(
                        format = "JSON Object",
                        headers = headers,
                        rows = rows,
                        summary = generateJsonSummary(listOf(obj))
                    )
                }
                else -> ParsedData.Text("JSON", content)
            }
        } catch (e: Exception) {
            Log.e(tag, "Ошибка парсинга JSON", e)
            ParsedData.Text("JSON", content)
        }
    }
    
    /**
     * Парсит логи
     */
    fun parseLogs(content: String): ParsedData {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParsedData.Empty("Лог файл пуст")
        }
        
        // Извлекаем ошибки, предупреждения и другую информацию
        val errors = lines.filter { 
            it.contains("ERROR", ignoreCase = true) || 
            it.contains("Exception", ignoreCase = true) ||
            it.contains("Error", ignoreCase = true)
        }
        
        val warnings = lines.filter { 
            it.contains("WARN", ignoreCase = true) || 
            it.contains("Warning", ignoreCase = true)
        }
        
        val info = lines.filter { 
            it.contains("INFO", ignoreCase = true) || 
            it.contains("Info", ignoreCase = true)
        }
        
        // Группируем ошибки по типу
        val errorGroups = errors.groupBy { line ->
            // Пытаемся извлечь тип ошибки
            when {
                line.contains("NullPointerException", ignoreCase = true) -> "NullPointerException"
                line.contains("IllegalArgumentException", ignoreCase = true) -> "IllegalArgumentException"
                line.contains("IOException", ignoreCase = true) -> "IOException"
                line.contains("SQLException", ignoreCase = true) -> "SQLException"
                else -> "Other"
            }
        }
        
        val summary = buildString {
            appendLine("Всего строк: ${lines.size}")
            appendLine("Ошибок: ${errors.size}")
            appendLine("Предупреждений: ${warnings.size}")
            appendLine("Информационных сообщений: ${info.size}")
            appendLine("\nТипы ошибок:")
            errorGroups.forEach { (type, count) ->
                appendLine("  - $type: ${count.size}")
            }
        }
        
        return ParsedData.Log(
            lines = lines,
            errors = errors,
            warnings = warnings,
            info = info,
            errorGroups = errorGroups,
            summary = summary
        )
    }
    
    /**
     * Генерирует сводку для CSV
     */
    private fun generateCsvSummary(headers: List<String>, rows: List<Map<String, String>>): String {
        val summary = StringBuilder()
        summary.appendLine("Всего строк: ${rows.size}")
        summary.appendLine("Колонок: ${headers.size}")
        summary.appendLine("\nКолонки: ${headers.joinToString(", ")}")
        
        // Статистика по числовым колонкам
        headers.forEach { header ->
            val numericValues = rows.mapNotNull { row ->
                row[header]?.toDoubleOrNull()
            }
            if (numericValues.isNotEmpty()) {
                summary.appendLine("\n$header:")
                summary.appendLine("  Среднее: ${numericValues.average()}")
                summary.appendLine("  Мин: ${numericValues.minOrNull()}")
                summary.appendLine("  Макс: ${numericValues.maxOrNull()}")
            }
        }
        
        return summary.toString()
    }
    
    /**
     * Генерирует сводку для JSON
     */
    private fun generateJsonSummary(items: List<JsonObject>): String {
        val summary = StringBuilder()
        summary.appendLine("Всего элементов: ${items.size}")
        
        if (items.isNotEmpty()) {
            val keys = items.first().keySet()
            summary.appendLine("Ключи: ${keys.joinToString(", ")}")
        }
        
        return summary.toString()
    }
}

/**
 * Представляет распарсенные данные
 */
sealed class ParsedData {
    abstract val format: String
    
    data class Structured(
        override val format: String,
        val headers: List<String>,
        val rows: List<Map<String, String>>,
        val summary: String
    ) : ParsedData()
    
    data class Log(
        val lines: List<String>,
        val errors: List<String>,
        val warnings: List<String>,
        val info: List<String>,
        val errorGroups: Map<String, List<String>>,
        val summary: String
    ) : ParsedData() {
        override val format = "LOG"
    }
    
    data class Text(
        override val format: String,
        val content: String
    ) : ParsedData()
    
    data class Empty(
        val message: String
    ) : ParsedData() {
        override val format = "EMPTY"
    }
}
