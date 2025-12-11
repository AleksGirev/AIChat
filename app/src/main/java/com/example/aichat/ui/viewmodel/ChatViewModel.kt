package com.example.aichat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.ui.model.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing chat state and interactions
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val networkDeps = NetworkModule.createNetworkDependencies()
    private val repository = ChatRepository(
        apiService = networkDeps.apiService,
        apiKey = Config.OPENAI_API_KEY,
        yandexApiService = networkDeps.yandexApiService
    )
    
    // Initialize database and history repository
    private val database = ChatDatabase.getDatabase(application)
    private val historyRepository = ChatHistoryRepository(database.chatMessageDao())
    
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Available models
    val availableModels = listOf(
        "amazon/nova-2-lite-v1:free" to "Amazon Nova 2 Lite",
        "gpt://${Config.YANDEX_FOLDER_ID}/yandexgpt/latest" to "YandexGPT"
    )
    
    // Settings
    private val _modelName = MutableStateFlow("amazon/nova-2-lite-v1:free")
    val modelName: StateFlow<String> = _modelName.asStateFlow()
    
    private val _temperature = MutableStateFlow(0.7)
    val temperature: StateFlow<Double> = _temperature.asStateFlow()
    
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()
    
    init {
        // Load chat history on initialization
        loadChatHistory()
    }
    
    /**
     * Loads chat history from the database
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val savedMessages = historyRepository.getLastMessages()
                if (savedMessages.isNotEmpty()) {
                    _messages.value = savedMessages
                }
            } catch (e: Exception) {
                // If loading fails, start with empty list
                _messages.value = emptyList()
            }
        }
    }
    
    /**
     * Gets display name for current model
     */
    fun getModelDisplayName(): String {
        return availableModels.find { it.first == _modelName.value }?.second 
            ?: _modelName.value.split("/").lastOrNull() 
            ?: _modelName.value
    }
    
    /**
     * Sets the model to use for chat
     */
    fun setModel(model: String) {
        _modelName.value = model
    }
    
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
    
    /**
     * Sends a user message and gets response from the model
     */
    fun sendMessage(messageText: String) {
        if (messageText.isBlank() || _isLoading.value) return
        
        // Add user message to the list
        val userMessage = UiMessage(
            id = UUID.randomUUID().toString(),
            content = messageText.trim(),
            isUser = true
        )
        _messages.value = _messages.value + userMessage
        
        // Save user message to database
        viewModelScope.launch {
            historyRepository.saveMessage(userMessage)
        }
        
        // Convert UI messages to ChatMessage format for API
        // Build conversation history from existing messages (excluding the one we just added)
        val conversationHistory = _messages.value
            .dropLast(1) // Exclude the user message we just added
            .map { uiMessage ->
                ChatMessage(
                    role = if (uiMessage.isUser) "user" else "assistant",
                    content = uiMessage.content
                )
            }
            .takeLast(20) // Keep last 20 messages for context
        
        // Build messages list with system prompt if set
        val allMessages = buildList {
            // Add system prompt if configured
            if (_systemPrompt.value.isNotBlank()) {
                add(ChatMessage(role = "system", content = _systemPrompt.value))
            }
            // Add conversation history
            addAll(conversationHistory)
            // Add current user message
            add(ChatMessage(role = "user", content = messageText.trim()))
        }
        
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            val result = repository.sendChatRequest(
                messages = allMessages,
                model = _modelName.value,
                temperature = _temperature.value
            )
            
            _isLoading.value = false
            
            result.onSuccess { response ->
                val assistantMessage = response.choices.firstOrNull()?.message?.content
                if (assistantMessage != null) {
                    // Get token information from response
                    val usage = response.usage
                    val requestTokens = response.estimatedRequestTokens
                    val responseTokens = usage?.completionTokens
                    val totalTokens = usage?.totalTokens
                    
                    val assistantUiMessage = UiMessage(
                        id = UUID.randomUUID().toString(),
                        content = assistantMessage,
                        isUser = false,
                        requestTokens = requestTokens,
                        responseTokens = responseTokens,
                        totalTokens = totalTokens
                    )
                    
                    // Update the last user message with request tokens info
                    val updatedMessages = _messages.value.toMutableList()
                    if (updatedMessages.isNotEmpty() && updatedMessages.last().isUser) {
                        val lastUserMessage = updatedMessages.last()
                        val updatedUserMessage = lastUserMessage.copy(
                            requestTokens = requestTokens
                        )
                        updatedMessages[updatedMessages.size - 1] = updatedUserMessage
                        // Update user message in database
                        viewModelScope.launch {
                            historyRepository.saveMessage(updatedUserMessage)
                        }
                    }
                    updatedMessages.add(assistantUiMessage)
                    _messages.value = updatedMessages
                    
                    // Save assistant message to database
                    viewModelScope.launch {
                        historyRepository.saveMessage(assistantUiMessage)
                    }
                } else {
                    _errorMessage.value = "No response from model"
                }
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Unknown error occurred"
            }
        }
    }
    
    /**
     * Clears the error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Starts a new chat by clearing all messages and error state
     * This resets the conversation context for the LLM
     */
    fun startNewChat() {
        _messages.value = emptyList()
        _errorMessage.value = null
        // Clear chat history from database
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
        // Note: Temperature and system prompt settings are preserved
    }
}

