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
import com.example.aichat.data.util.HistoryCompressionService
import com.example.aichat.ui.model.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    
    // Initialize compression service
    private val compressionService = HistoryCompressionService(
        chatRepository = repository
    )
    
    // Internal flow with all messages including summaries (for compression logic)
    private val _allMessages = MutableStateFlow<List<UiMessage>>(emptyList())
    
    // Expose only non-summary messages to UI (summary messages are hidden from user)
    // Limit to 100 most recent messages for display
    val messages: StateFlow<List<UiMessage>> = _allMessages.map { messages ->
        messages.filter { !it.isSummary }
            .takeLast(100) // Keep only the 100 most recent messages for display
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
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
                    _allMessages.value = savedMessages
                }
            } catch (e: Exception) {
                // If loading fails, start with empty list
                _allMessages.value = emptyList()
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
        
        // Add user message to the list immediately (for UX - user sees it right away)
        val userMessage = UiMessage(
            id = UUID.randomUUID().toString(),
            content = messageText.trim(),
            isUser = true
        )
        _allMessages.value = _allMessages.value + userMessage
        
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                // Get messages BEFORE adding the current user message (for compression check)
                val messagesBeforeCurrent = _allMessages.value.dropLast(1)
                
                // Check if compression threshold is reached BEFORE sending
                val shouldCompress = compressionService.shouldCompressBeforeSending(messagesBeforeCurrent)
                
                var summaryMessage: UiMessage? = null
                var compressedIds: List<String> = emptyList()
                
                if (shouldCompress) {
                    // Compress all previous messages (excluding current user message)
                    val compressionResult = compressionService.compressAllPreviousMessages(
                        messagesBeforeCurrent,
                        _modelName.value
                    )
                    
                    if (compressionResult != null) {
                        val (summary, ids) = compressionResult
                        summaryMessage = summary
                        compressedIds = ids
                        
                        // Replace compressed messages in database with summary
                        // This is for storage efficiency - summaries replace originals in DB
                        // BUT we keep original messages in memory for UI display
                        if (compressedIds.isNotEmpty()) {
                            historyRepository.deleteMessagesByIds(compressedIds)
                        }
                        historyRepository.saveMessage(summary)
                        
                        // Update internal state: keep original messages for UI display,
                        // but add summary for API/compression logic (summary is filtered from UI)
                        // This ensures user sees all original messages while API uses compressed context
                        val compressedIdsSet = compressedIds.toSet()
                        
                        // Build updated list: summary (for API/compression) + ALL original messages + current user message
                        // Original messages stay visible in UI, summary is used for API requests
                        val updatedMessages = mutableListOf<UiMessage>()
                        updatedMessages.add(summary) // Add summary first (will be filtered from UI, used for API)
                        updatedMessages.addAll(messagesBeforeCurrent) // Keep ALL original messages for UI display
                        updatedMessages.add(userMessage) // Add current user message
                        
                        _allMessages.value = updatedMessages
                    }
                } else {
                    // No compression needed - just save the user message
                    historyRepository.saveMessage(userMessage)
                }
                
                // Build messages list for API request
                // If compression occurred, send only summary + current user message
                // Otherwise, use normal conversation history
                val apiMessages = buildList {
                    if (summaryMessage != null) {
                        // Compression occurred - send only summary + current message
                        // Summary goes into system message for context
                        val systemContent = buildString {
                            append("Контекст предыдущего разговора:\n")
                            append(summaryMessage.content)
                            if (_systemPrompt.value.isNotBlank()) {
                                append("\n\n")
                                append(_systemPrompt.value)
                            }
                        }
                        add(ChatMessage(role = "system", content = systemContent))
                        add(ChatMessage(role = "user", content = messageText.trim()))
                    } else {
                        // No compression - use normal flow
                        val messagesForContext = messagesBeforeCurrent
                        val (conversationHistory, summaryContext) = compressionService.buildConversationHistory(messagesForContext)
                        val limitedHistory = conversationHistory.takeLast(20) // Keep last 20 messages for context
                        
                        // Build system message content
                        val systemContent = buildString {
                            if (summaryContext != null && summaryContext.isNotBlank()) {
                                append("Контекст предыдущего разговора:\n")
                                append(summaryContext)
                                if (_systemPrompt.value.isNotBlank()) {
                                    append("\n\n")
                                    append(_systemPrompt.value)
                                }
                            } else if (_systemPrompt.value.isNotBlank()) {
                                append(_systemPrompt.value)
                            }
                        }
                        
                        var hasSystemMessage = false
                        
                        if (summaryContext != null && summaryContext.isNotBlank()) {
                            add(ChatMessage(role = "system", content = systemContent))
                            hasSystemMessage = true
                        } else if (systemContent.isNotBlank()) {
                            add(ChatMessage(role = "system", content = systemContent))
                            hasSystemMessage = true
                        }
                        
                        if (limitedHistory.isNotEmpty()) {
                            val firstMessage = limitedHistory.first()
                            if (firstMessage.role == "assistant" && !hasSystemMessage) {
                                if (summaryContext != null && summaryContext.isNotBlank()) {
                                    add(0, ChatMessage(role = "system", content = systemContent))
                                    hasSystemMessage = true
                                } else {
                                    add(ChatMessage(role = "system", content = "Продолжи разговор на основе предыдущего контекста."))
                                    hasSystemMessage = true
                                }
                            }
                            addAll(limitedHistory)
                        }
                        
                        add(ChatMessage(role = "user", content = messageText.trim()))
                    }
                }
                
                // Send API request
                val result = repository.sendChatRequest(
                    messages = apiMessages,
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
                        val updatedMessages = _allMessages.value.toMutableList()
                        if (updatedMessages.isNotEmpty() && updatedMessages.last().isUser) {
                            val lastUserMessage = updatedMessages.last()
                            val updatedUserMessage = lastUserMessage.copy(
                                requestTokens = requestTokens
                            )
                            updatedMessages[updatedMessages.size - 1] = updatedUserMessage
                            // Update user message in database
                            historyRepository.saveMessage(updatedUserMessage)
                        }
                        updatedMessages.add(assistantUiMessage)
                        _allMessages.value = updatedMessages
                        
                        // Save assistant message to database
                        historyRepository.saveMessage(assistantUiMessage)
                    } else {
                        _errorMessage.value = "No response from model"
                    }
                }.onFailure { error ->
                    _errorMessage.value = error.message ?: "Unknown error occurred"
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Unknown error occurred"
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
        _allMessages.value = emptyList()
        _errorMessage.value = null
        // Clear chat history from database
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
        // Note: Temperature and system prompt settings are preserved
    }
}

