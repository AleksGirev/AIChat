package com.example.aichat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.local.ChatSessionRepository
import com.example.aichat.data.local.ChatSessionEntity
import com.example.aichat.data.local.ExternalMemoryRepository
import com.example.aichat.data.util.JsonMemoryExporter
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
    
    // Initialize database and repositories
    private val database = ChatDatabase.getDatabase(application)
    private val historyRepository = ChatHistoryRepository(database.chatMessageDao())
    private val sessionRepository = ChatSessionRepository(database.chatSessionDao())
    private val externalMemoryRepository = ExternalMemoryRepository(database.externalMemoryDao())
    
    // Initialize compression service
    private val compressionService = HistoryCompressionService(
        chatRepository = repository
    )
    
    // Initialize JSON exporter
    private val jsonExporter = JsonMemoryExporter(
        context = application,
        sessionRepository = sessionRepository,
        historyRepository = historyRepository,
        externalMemoryRepository = externalMemoryRepository
    )
    
    // Current session ID
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    
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
        // Create a new session on initialization if none exists
        viewModelScope.launch {
            if (_currentSessionId.value == null) {
                createNewSession()
            }
        }
    }
    
    /**
     * Creates a new chat session
     */
    private suspend fun createNewSession(title: String = "New Chat"): String {
        val sessionId = UUID.randomUUID().toString()
        sessionRepository.createSession(
            id = sessionId,
            title = title,
            summary = null
        )
        _currentSessionId.value = sessionId
        _allMessages.value = emptyList()
        return sessionId
    }
    
    /**
     * Loads a chat session by ID
     * Checks for uns summarized messages and summarizes them if needed
     * Only loads recent messages for UI display (last 50 messages)
     * Summary is stored separately and will be used as context for API requests
     */
    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val session = sessionRepository.getSessionById(sessionId)
                if (session != null) {
                    _currentSessionId.value = sessionId
                    
                    // Check for uns summarized messages
                    val lastSummarizedTimestamp = session.lastSummarizedTimestamp ?: 0L
                    val unsSummarizedMessages = historyRepository.getNonSummaryMessagesAfterTimestamp(
                        sessionId, 
                        lastSummarizedTimestamp
                    )
                    
                    // If there are uns summarized messages, summarize them
                    if (unsSummarizedMessages.isNotEmpty()) {
                        summarizeMessages(sessionId, unsSummarizedMessages, session.summary)
                    }
                    
                    // Load only recent messages for UI display (last 50 messages)
                    // Summary will be used as context, not all messages
                    val allNonSummaryMessages = historyRepository.getNonSummaryMessagesBySessionId(sessionId)
                    _allMessages.value = allNonSummaryMessages.takeLast(50)
                    
                    // Load external memory for this session
                    loadExternalMemoryForSession(sessionId)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load session: ${e.message}"
            }
        }
    }
    
    /**
     * Summarizes messages and updates session summary
     */
    private suspend fun summarizeMessages(
        sessionId: String,
        messagesToSummarize: List<UiMessage>,
        existingSummary: String?
    ) {
        if (messagesToSummarize.isEmpty()) return
        
        try {
            // Generate summary of new messages
            val conversationText = messagesToSummarize.joinToString("\n") { message ->
                "${if (message.isUser) "User" else "Assistant"}: ${message.content}"
            }
            
            val summaryPrompt = if (existingSummary != null) {
                """
                Объедини существующее резюме диалога с новыми сообщениями в одно краткое резюме.
                Сохрани всю ключевую информацию и контекст для продолжения разговора.
                Резюме должно быть на русском языке и содержать только важные детали.
                
                Существующее резюме:
                $existingSummary
                
                Новые сообщения:
                $conversationText
                
                Объединенное резюме:
                """.trimIndent()
            } else {
                """
                Создай краткое резюме следующего диалога, сохраняя ключевую информацию и контекст для продолжения разговора.
                Резюме должно быть на русском языке и содержать только важные детали.
                
                Диалог:
                $conversationText
                
                Резюме:
                """.trimIndent()
            }
            
            val summaryMessages = listOf(
                ChatMessage(role = "user", content = summaryPrompt)
            )
            
            val result = repository.sendChatRequest(
                messages = summaryMessages,
                model = _modelName.value,
                temperature = 0.3 // Lower temperature for more consistent summaries
            )
            
            val newSummary = result.getOrNull()?.choices?.firstOrNull()?.message?.content
            
            if (newSummary != null) {
                // Update session summary and last summarized timestamp
                val lastMessageTimestamp = messagesToSummarize.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                sessionRepository.updateSummaryAndTimestamp(sessionId, newSummary, lastMessageTimestamp)
                
                // Also save to external memory
                externalMemoryRepository.saveMemory(
                    key = "session_summary_$sessionId",
                    content = newSummary,
                    category = "session_summary",
                    sessionId = sessionId
                )
            }
        } catch (e: Exception) {
            // If summarization fails, continue without updating summary
            // The messages will be available for next time
        }
    }
    
    /**
     * Loads external memory for a session
     */
    private suspend fun loadExternalMemoryForSession(sessionId: String) {
        // External memory will be used when building context for API requests
        // This is handled in sendMessage method
    }
    
    /**
     * Gets the summary for the current session
     */
    private suspend fun getSessionSummary(): String? {
        val sessionId = _currentSessionId.value ?: return null
        val session = sessionRepository.getSessionById(sessionId)
        return session?.summary
    }
    
    /**
     * Updates the session summary
     */
    private suspend fun updateSessionSummary(summary: String?) {
        val sessionId = _currentSessionId.value ?: return
        sessionRepository.updateSummary(sessionId, summary)
        
        // Also save to external memory
        if (summary != null) {
            externalMemoryRepository.saveMemory(
                key = "session_summary_$sessionId",
                content = summary,
                category = "session_summary",
                sessionId = sessionId
            )
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
        
        // Ensure we have a session
        val sessionId = _currentSessionId.value ?: run {
            viewModelScope.launch {
                val newSessionId = createNewSession()
                sendMessage(messageText) // Retry with new session
            }
            return
        }
        
        // Add user message to the list immediately (for UX - user sees it right away)
        val userMessage = UiMessage(
            id = UUID.randomUUID().toString(),
            content = messageText.trim(),
            isUser = true,
            sessionId = sessionId
        )
        _allMessages.value = _allMessages.value + userMessage
        
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                // Update session title if it's the first message
                if (_allMessages.value.size == 1) {
                    val title = messageText.trim().take(50)
                    val session = sessionRepository.getSessionById(sessionId)
                    session?.let {
                        sessionRepository.updateSession(it.copy(title = title))
                    }
                }
                
                // Get session summary for context (from SQLite - долговременная память)
                val session = sessionRepository.getSessionById(sessionId)
                var sessionSummary = session?.summary
                
                // Get messages BEFORE adding the current user message
                val messagesBeforeCurrent = _allMessages.value.dropLast(1)
                
                // Check if there are uns summarized messages that need to be summarized
                val lastSummarizedTimestamp = session?.lastSummarizedTimestamp ?: 0L
                val unsSummarizedMessages = messagesBeforeCurrent.filter { 
                    it.timestamp > lastSummarizedTimestamp && !it.isSummary 
                }
                
                // If there are uns summarized messages, summarize them before sending
                if (unsSummarizedMessages.isNotEmpty()) {
                    summarizeMessages(sessionId, unsSummarizedMessages, sessionSummary)
                    // Get updated summary after summarization
                    val updatedSession = sessionRepository.getSessionById(sessionId)
                    sessionSummary = updatedSession?.summary
                }
                
                // Save user message to database
                historyRepository.saveMessage(userMessage)
                
                // Build messages list for API request
                // Use ONLY summary from SQLite (долговременная память), NOT all messages
                val apiMessages = buildList {
                    val systemContent = buildString {
                        // Use summary from SQLite (долговременная память)
                        if (sessionSummary != null && sessionSummary.isNotBlank()) {
                            append("Контекст предыдущего общения:\n")
                            append(sessionSummary)
                        }
                        
                        // Add system prompt if set
                        if (_systemPrompt.value.isNotBlank()) {
                            if (length > 0) {
                                append("\n\n")
                            }
                            append(_systemPrompt.value)
                        }
                    }
                    
                    // Add system message if we have any context
                    if (systemContent.isNotBlank()) {
                        add(ChatMessage(role = "system", content = systemContent))
                    }
                    
                    // Add current user message
                    add(ChatMessage(role = "user", content = messageText.trim()))
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
                            sessionId = sessionId,
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
                        
                        // Update session metadata
                        val messageCount = historyRepository.getMessageCountBySessionId(sessionId)
                        sessionRepository.updateMessageCount(sessionId, messageCount)
                        sessionRepository.updateLastMessage(sessionId, assistantMessage.take(100))
                        
                        // Save intermediate result to external memory
                        externalMemoryRepository.saveMemory(
                            key = "intermediate_${assistantUiMessage.id}",
                            content = assistantMessage,
                            category = "intermediate_result",
                            sessionId = sessionId,
                            relatedMessageIds = listOf(assistantUiMessage.id)
                        )
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
     * Starts a new chat by creating a new session
     * This preserves the previous session in the database
     */
    fun startNewChat() {
        _allMessages.value = emptyList()
        _errorMessage.value = null
        viewModelScope.launch {
            createNewSession()
        }
        // Note: Temperature and system prompt settings are preserved
    }
    
    /**
     * Gets all chat sessions
     */
    fun getAllSessions() = sessionRepository.getAllSessions()
    
    /**
     * Deletes a chat session
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(sessionId)
                historyRepository.deleteMessagesBySessionId(sessionId)
                externalMemoryRepository.deleteBySessionId(sessionId)
                
                // If we deleted the current session, create a new one
                if (_currentSessionId.value == sessionId) {
                    createNewSession()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete session: ${e.message}"
            }
        }
    }
    
    /**
     * Exports all data to JSON file
     */
    suspend fun exportAllToJson(): Result<java.io.File> {
        return try {
            val file = jsonExporter.exportAllToJson()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Exports current session to JSON file
     */
    suspend fun exportCurrentSessionToJson(): Result<java.io.File> {
        val sessionId = _currentSessionId.value
        return if (sessionId != null) {
            try {
                val file = jsonExporter.exportSessionToJson(sessionId)
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException("No active session"))
        }
    }
    
    /**
     * Imports data from JSON string
     */
    fun importFromJson(json: String) {
        viewModelScope.launch {
            try {
                val result = jsonExporter.importFromJson(json)
                result.onSuccess {
                    // Reload current session if it exists
                    _currentSessionId.value?.let { loadSession(it) }
                }.onFailure { error ->
                    _errorMessage.value = "Failed to import: ${error.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to import: ${e.message}"
            }
        }
    }
    
    /**
     * Imports data from JSON file
     */
    fun importFromJsonFile(file: java.io.File) {
        viewModelScope.launch {
            try {
                val result = jsonExporter.importFromJsonFile(file)
                result.onSuccess {
                    // Reload current session if it exists
                    _currentSessionId.value?.let { loadSession(it) }
                }.onFailure { error ->
                    _errorMessage.value = "Failed to import: ${error.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to import: ${e.message}"
            }
        }
    }
}

