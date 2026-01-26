package com.example.aichat.ui.analyst

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.analyst.DataAnalystRepository
import com.example.aichat.data.analyst.DataLoader
import com.example.aichat.data.analyst.DataParser
import com.example.aichat.data.analyst.LoadedData
import com.example.aichat.data.analyst.ParsedData
import com.example.aichat.data.local.AnalystSettingsManager
import com.example.aichat.data.local.AppLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана анализа данных
 */
class DataAnalystViewModel(
    private val dataLoader: DataLoader,
    private val dataParser: DataParser,
    private val repository: DataAnalystRepository,
    private val appLogRepository: AppLogRepository,
    private val settingsManager: AnalystSettingsManager
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _currentFileName = MutableStateFlow<String?>(null)
    val currentFileName: StateFlow<String?> = _currentFileName.asStateFlow()
    
    private val _dataSummary = MutableStateFlow<String?>(null)
    val dataSummary: StateFlow<String?> = _dataSummary.asStateFlow()
    
    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult.asStateFlow()
    
    private val _useAppLogs = MutableStateFlow(settingsManager.getUseAppLogs())
    val useAppLogs: StateFlow<Boolean> = _useAppLogs.asStateFlow()
    
    private val _appLogsCount = MutableStateFlow(0)
    val appLogsCount: StateFlow<Int> = _appLogsCount.asStateFlow()
    
    private val _conversationHistory = MutableStateFlow<List<AnalystMessage>>(emptyList())
    val conversationHistory: StateFlow<List<AnalystMessage>> = _conversationHistory.asStateFlow()
    
    /**
     * Загружает данные из файла
     */
    fun loadData(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _analysisResult.value = null
            
            try {
                // Загружаем файл
                val loadedData = dataLoader.loadFromUri(uri)
                _currentFileName.value = loadedData.fileName
                
                // Парсим данные
                val parsedData = when (loadedData) {
                    is LoadedData.CsvData -> dataParser.parseCsv(loadedData.rawContent)
                    is LoadedData.JsonData -> dataParser.parseJson(loadedData.rawContent)
                    is LoadedData.LogData -> dataParser.parseLogs(loadedData.rawContent)
                    is LoadedData.TextData -> ParsedData.Text("TEXT", loadedData.rawContent)
                }
                
                // Устанавливаем данные в репозиторий
                repository.setData(parsedData)
                
                // Показываем сводку
                _dataSummary.value = when (parsedData) {
                    is ParsedData.Structured -> parsedData.summary
                    is ParsedData.Log -> parsedData.summary
                    is ParsedData.Text -> "Текстовые данные загружены (${parsedData.content.length} символов)"
                    is ParsedData.Empty -> parsedData.message
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки файла: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Анализирует данные по вопросу пользователя
     */
    fun analyze(question: String) {
        if (question.isBlank()) return
        if (_isLoading.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // Добавляем вопрос пользователя в историю
            val userMessage = AnalystMessage.User(question)
            _conversationHistory.value = _conversationHistory.value + userMessage
            
            try {
                val result = repository.analyzeData(question)
                
                result.onSuccess { answer ->
                    _analysisResult.value = answer
                    // Добавляем ответ в историю
                    val assistantMessage = AnalystMessage.Assistant(answer)
                    _conversationHistory.value = _conversationHistory.value + assistantMessage
                }.onFailure { error ->
                    _errorMessage.value = error.message
                    // Добавляем ошибку в историю
                    val errorMessage = AnalystMessage.Error(error.message ?: "Неизвестная ошибка")
                    _conversationHistory.value = _conversationHistory.value + errorMessage
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка анализа: ${e.message}"
                val errorMessage = AnalystMessage.Error(e.message ?: "Неизвестная ошибка")
                _conversationHistory.value = _conversationHistory.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Переключает использование логов приложения
     */
    fun toggleUseAppLogs() {
        viewModelScope.launch {
            val newValue = !_useAppLogs.value
            _useAppLogs.value = newValue
            repository.setUseAppLogs(newValue)
            
            // Сохраняем настройку в SharedPreferences
            settingsManager.setUseAppLogs(newValue)
            
            if (newValue) {
                // Загружаем статистику логов
                val count = appLogRepository.getLogCount()
                _appLogsCount.value = count
                
                val errorCount = appLogRepository.getLogCountByLevel("ERROR")
                val warnCount = appLogRepository.getLogCountByLevel("WARN")
                
                _dataSummary.value = buildString {
                    appendLine("Используются логи приложения")
                    appendLine("Всего логов: $count")
                    appendLine("Ошибок: $errorCount")
                    appendLine("Предупреждений: $warnCount")
                }
                _currentFileName.value = "Логи приложения"
            } else {
                _currentFileName.value = null
                _dataSummary.value = null
            }
        }
    }
    
    /**
     * Загружает статистику логов приложения
     */
    fun loadAppLogsStats() {
        viewModelScope.launch {
            try {
                val count = appLogRepository.getLogCount()
                _appLogsCount.value = count
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки статистики логов: ${e.message}"
            }
        }
    }
    
    /**
     * Очищает загруженные данные
     */
    fun clearData() {
        repository.clearData()
        _currentFileName.value = null
        _dataSummary.value = null
        _analysisResult.value = null
        _conversationHistory.value = emptyList()
        _useAppLogs.value = false
    }
    
    /**
     * Очищает сообщение об ошибке
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    init {
        // Загружаем статистику логов при старте
        loadAppLogsStats()
        
        // Создаем моковые логи, если их еще нет
        viewModelScope.launch {
            if (!settingsManager.areMockLogsCreated()) {
                try {
                    appLogRepository.createMockLogs()
                    settingsManager.setMockLogsCreated(true)
                    loadAppLogsStats() // Обновляем статистику после создания
                } catch (e: Exception) {
                    _errorMessage.value = "Ошибка создания демо-логов: ${e.message}"
                }
            }
            
            // Если настройка использования логов включена, применяем её
            if (_useAppLogs.value) {
                repository.setUseAppLogs(true)
                // Обновляем UI
                val count = appLogRepository.getLogCount()
                _appLogsCount.value = count
                
                val errorCount = appLogRepository.getLogCountByLevel("ERROR")
                val warnCount = appLogRepository.getLogCountByLevel("WARN")
                
                _dataSummary.value = buildString {
                    appendLine("Используются логи приложения")
                    appendLine("Всего логов: $count")
                    appendLine("Ошибок: $errorCount")
                    appendLine("Предупреждений: $warnCount")
                }
                _currentFileName.value = "Логи приложения"
            }
        }
    }
}

/**
 * Сообщения в разговоре с аналитиком
 */
sealed class AnalystMessage {
    abstract val text: String
    
    data class User(override val text: String) : AnalystMessage()
    data class Assistant(override val text: String) : AnalystMessage()
    data class Error(override val text: String) : AnalystMessage()
}
