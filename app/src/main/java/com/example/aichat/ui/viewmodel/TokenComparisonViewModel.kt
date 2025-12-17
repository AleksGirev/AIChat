package com.example.aichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.RequestType
import com.example.aichat.data.model.TokenComparisonResult
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.util.TokenCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * ViewModel for comparing token usage with different request lengths
 */
class TokenComparisonViewModel : ViewModel() {
    
    private val networkDeps = NetworkModule.createNetworkDependencies()
    private val repository = ChatRepository(
        apiService = networkDeps.apiService,
        apiKey = Config.OPENAI_API_KEY,
        yandexApiService = networkDeps.yandexApiService,
        gson = networkDeps.gson
    )
    
    // Available models
    val availableModels = listOf(
        "amazon/nova-2-lite-v1:free" to "Amazon Nova 2 Lite",
        "gpt://${Config.YANDEX_FOLDER_ID}/yandexgpt/latest" to "YandexGPT"
    )
    
    private val _modelName = MutableStateFlow(Config.DEFAULT_YANDEXGPT_MODEL)
    val modelName: StateFlow<String> = _modelName.asStateFlow()
    
    /**
     * Gets display name for current model
     */
    fun getModelDisplayName(): String {
        return availableModels.find { it.first == _modelName.value }?.second 
            ?: _modelName.value.split("/").lastOrNull() 
            ?: _modelName.value
    }
    
    private val _comparisonResults = MutableStateFlow<List<TokenComparisonResult>>(emptyList())
    val comparisonResults: StateFlow<List<TokenComparisonResult>> = _comparisonResults.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Sets the model to test
     */
    fun setModel(model: String) {
        _modelName.value = model
    }
    
    /**
     * Generates test messages of different lengths and compares them
     */
    fun runComparison() {
        if (_isLoading.value) return
        
        _isLoading.value = true
        _errorMessage.value = null
        _comparisonResults.value = emptyList()
        
        viewModelScope.launch {
            try {
                val model = _modelName.value
                val modelLimit = TokenCounter.getModelContextLimit(model)
                
                // Generate test messages
                val shortMessage = generateShortMessage()
                val longMessage = generateLongMessage(modelLimit)
                val exceedsLimitMessage = generateExceedsLimitMessage(modelLimit)
                
                val results = mutableListOf<TokenComparisonResult>()
                
                // Test short message
                results.add(testRequest(shortMessage, RequestType.SHORT, model, modelLimit))
                
                // Test long message
                results.add(testRequest(longMessage, RequestType.LONG, model, modelLimit))
                
                // Test exceeds limit message
                results.add(testRequest(exceedsLimitMessage, RequestType.EXCEEDS_LIMIT, model, modelLimit))
                
                _comparisonResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unknown error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Tests a single request
     */
    private suspend fun testRequest(
        message: String,
        requestType: RequestType,
        model: String,
        modelLimit: Int
    ): TokenComparisonResult {
        val messages = listOf(ChatMessage(role = "user", content = message))
        val estimatedTokens = TokenCounter.estimateRequestTokens(messages)
        val (exceedsLimit, _, _) = TokenCounter.checkTokenLimit(messages, model)
        
        var responseTimeMs = 0L
        var response: com.example.aichat.data.model.ChatResponse? = null
        var error: String? = null
        var isSuccess = false
        
        try {
            responseTimeMs = measureTimeMillis {
                val result = repository.sendChatRequest(
                    messages = messages,
                    model = model
                )
                
                result.onSuccess {
                    response = it
                    isSuccess = true
                }.onFailure {
                    error = it.message
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        }
        
        return TokenComparisonResult(
            requestType = requestType,
            userMessage = message,
            estimatedRequestTokens = estimatedTokens,
            modelLimit = modelLimit,
            exceedsLimit = exceedsLimit,
            response = response,
            error = error,
            isSuccess = isSuccess,
            responseTimeMs = responseTimeMs
        )
    }
    
    /**
     * Generates a short test message
     */
    private fun generateShortMessage(): String {
        return "Привет! Как дела? Расскажи что-нибудь интересное."
    }
    
    /**
     * Generates a long test message that stays within model limits
     */
    private fun generateLongMessage(modelLimit: Int): String {
        // Generate a message that uses approximately 30-40% of the model limit
        val targetTokens = (modelLimit * 0.35).toInt()
        val wordsPerToken = 1.3
        val targetWords = (targetTokens * wordsPerToken).toInt()
        
        val baseText = """
            Пожалуйста, напиши подробный анализ следующей темы. 
            Мне нужна детальная информация с примерами и объяснениями.
            Рассмотри различные аспекты и дай развернутый ответ.
        """.trimIndent()
        
        // Repeat and expand to reach target length
        val repetitions = (targetWords / baseText.split(" ").size) + 1
        val expandedText = baseText.repeat(repetitions)
        
        // Trim to approximate target
        val words = expandedText.split(" ")
        val trimmedWords = words.take(targetWords)
        
        return trimmedWords.joinToString(" ") + "."
    }
    
    /**
     * Generates a message that exceeds model limit
     */
    private fun generateExceedsLimitMessage(modelLimit: Int): String {
        // Generate a message that exceeds the model limit by ~20%
        val targetTokens = (modelLimit * 1.2).toInt()
        val wordsPerToken = 1.3
        val targetWords = (targetTokens * wordsPerToken).toInt()
        
        val baseText = """
            Это очень длинный запрос, который содержит много информации и деталей.
            Пожалуйста, обработай все эти данные и дай развернутый ответ.
            Здесь много текста, который нужно проанализировать и понять.
            Каждое предложение добавляет контекст и важную информацию.
        """.trimIndent()
        
        // Repeat to exceed limit
        val repetitions = (targetWords / baseText.split(" ").size) + 1
        val expandedText = baseText.repeat(repetitions)
        
        val words = expandedText.split(" ")
        val trimmedWords = words.take(targetWords)
        
        return trimmedWords.joinToString(" ") + "."
    }
    
    /**
     * Clears comparison results
     */
    fun clearResults() {
        _comparisonResults.value = emptyList()
        _errorMessage.value = null
    }
    
    /**
     * Clears error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}

