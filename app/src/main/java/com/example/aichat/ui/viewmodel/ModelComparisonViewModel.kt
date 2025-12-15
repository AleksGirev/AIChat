package com.example.aichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ComparisonMessage
import com.example.aichat.data.model.ModelComparisonResult
import com.example.aichat.data.model.ModelInfo
import java.util.UUID
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing model comparison state and interactions
 */
class ModelComparisonViewModel : ViewModel() {
    
    private val networkDeps = NetworkModule.createNetworkDependencies()
    private val repository = ChatRepository(
        apiService = networkDeps.apiService,
        apiKey = Config.OPENAI_API_KEY,
        yandexApiService = networkDeps.yandexApiService,
        gson = networkDeps.gson
    )
    
    // Fixed models for comparison: amazon/nova-2-lite-v1:free and YandexGPT
    private val _selectedModels = MutableStateFlow<List<ModelInfo>>(
        listOf(
            ModelInfo(
                id = "amazon/nova-2-lite-v1:free",
                name = "Amazon Nova 2 Lite",
                provider = "amazon"
            ),
            ModelInfo(
                id = "gpt://${Config.YANDEX_FOLDER_ID}/yandexgpt/latest",
                name = "Yandex GPT",
                provider = "yandex"
            )
        )
    )
    val selectedModels: StateFlow<List<ModelInfo>> = _selectedModels.asStateFlow()
    
    // Comparison messages (history of user prompts and their results)
    private val _comparisonMessages = MutableStateFlow<List<ComparisonMessage>>(emptyList())
    val comparisonMessages: StateFlow<List<ComparisonMessage>> = _comparisonMessages.asStateFlow()
    
    // Current comparison results (for backward compatibility and loading state)
    private val _comparisonResults = MutableStateFlow<List<ModelComparisonResult>>(emptyList())
    val comparisonResults: StateFlow<List<ModelComparisonResult>> = _comparisonResults.asStateFlow()
    
    // Loading states for each model
    private val _loadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingStates: StateFlow<Map<String, Boolean>> = _loadingStates.asStateFlow()
    
    // Overall loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Settings
    private val _temperature = MutableStateFlow(0.7)
    val temperature: StateFlow<Double> = _temperature.asStateFlow()
    
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()
    
    /**
     * Updates the temperature setting
     */
    fun setTemperature(value: Double) {
        _temperature.value = value.coerceIn(0.0, 2.0)
    }
    
    /**
     * Updates the system prompt setting
     */
    fun setSystemPrompt(prompt: String) {
        _systemPrompt.value = prompt
    }
    
    // Models are fixed, no need to change them
    
    /**
     * Compares models by sending the same request to all selected models
     */
    fun compareModels(userMessage: String, conversationHistory: List<ChatMessage> = emptyList()) {
        if (userMessage.isBlank() || _isLoading.value) return
        
        // Build messages list with system prompt if set
        val allMessages = buildList {
            // Add system prompt if configured
            if (_systemPrompt.value.isNotBlank()) {
                add(ChatMessage(role = "system", content = _systemPrompt.value))
            }
            // Add conversation history
            addAll(conversationHistory)
            // Add current user message
            add(ChatMessage(role = "user", content = userMessage.trim()))
        }
        
        val modelNames = _selectedModels.value.map { it.id }
        
        // Initialize loading states
        _loadingStates.value = modelNames.associateWith { true }
        _isLoading.value = true
        _errorMessage.value = null
        _comparisonResults.value = emptyList()
        
        viewModelScope.launch {
            try {
                val results = repository.compareModels(
                    messages = allMessages,
                    modelNames = modelNames,
                    temperature = _temperature.value
                )
                
                _comparisonResults.value = results
                
                // Add to messages history
                val comparisonMessage = ComparisonMessage(
                    id = UUID.randomUUID().toString(),
                    userPrompt = userMessage.trim(),
                    results = results
                )
                _comparisonMessages.value = _comparisonMessages.value + comparisonMessage
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unknown error occurred"
            } finally {
                _loadingStates.value = modelNames.associateWith { false }
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clears the comparison results and messages history
     */
    fun clearResults() {
        _comparisonResults.value = emptyList()
        _comparisonMessages.value = emptyList()
        _errorMessage.value = null
    }
    
    /**
     * Clears the error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}

