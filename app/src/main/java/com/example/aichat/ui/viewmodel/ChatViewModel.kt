package com.example.aichat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
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
class ChatViewModel : ViewModel() {
    
    private val networkDeps = NetworkModule.createNetworkDependencies()
    private val repository = ChatRepository(
        apiService = networkDeps.apiService,
        apiKey = Config.OPENAI_API_KEY
    )
    
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
                model = "amazon/nova-2-lite-v1:free",
                temperature = _temperature.value
            )
            
            _isLoading.value = false
            
            result.onSuccess { response ->
                val assistantMessage = response.choices.firstOrNull()?.message?.content
                if (assistantMessage != null) {
                    val assistantUiMessage = UiMessage(
                        id = UUID.randomUUID().toString(),
                        content = assistantMessage,
                        isUser = false
                    )
                    _messages.value = _messages.value + assistantUiMessage
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
        // Note: Temperature and system prompt settings are preserved
    }
}

