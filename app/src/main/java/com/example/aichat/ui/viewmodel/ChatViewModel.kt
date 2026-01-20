package com.example.aichat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.local.ChatSessionRepository
import com.example.aichat.data.local.ChatSessionEntity
import com.example.aichat.data.local.ExternalMemoryRepository
import com.example.aichat.data.support.SupportContextBuilder
import com.example.aichat.data.mcp.McpConfig
import com.example.aichat.data.mcp.McpFactory
import com.example.aichat.data.mcp.McpRepository
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
 * ViewModel for managing chat state and interactions.
 * 
 * Now supports both manual instantiation (backward compatibility) and Koin injection.
 * When using Koin, dependencies are injected; otherwise, they are created internally.
 * 
 * @param application Android Application context
 * @param injectedRepository Optional injected ChatRepository (from Koin)
 * @param injectedMcpRepository Optional injected McpRepository (from Koin)
 * @param injectedHistoryRepository Optional injected ChatHistoryRepository (from Koin)
 * @param injectedSessionRepository Optional injected ChatSessionRepository (from Koin)
 * @param injectedExternalMemoryRepository Optional injected ExternalMemoryRepository (from Koin)
 * @param injectedSupportContextBuilder Optional injected SupportContextBuilder (from Koin)
 */
class ChatViewModel(
    application: Application,
    private val injectedRepository: ChatRepository? = null,
    private val injectedMcpRepository: McpRepository? = null,
    private val injectedHistoryRepository: ChatHistoryRepository? = null,
    private val injectedSessionRepository: ChatSessionRepository? = null,
    private val injectedExternalMemoryRepository: ExternalMemoryRepository? = null,
    private val injectedSupportContextBuilder: SupportContextBuilder? = null
) : AndroidViewModel(application) {
    
    // Create dependencies if not injected (backward compatibility)
    private val networkDeps by lazy { NetworkModule.createNetworkDependencies() }
    
    // Initialize MCP repository if enabled (use injected or create)
    private val mcpRepository: McpRepository? = injectedMcpRepository ?: McpFactory.createMcpRepository(
        context = application,
        httpClient = networkDeps.okHttpClient,
        gson = networkDeps.gson
    )
    
    private val repository: ChatRepository = injectedRepository ?: ChatRepository(
        apiService = networkDeps.apiService,
        apiKey = Config.OPENAI_API_KEY,
        yandexApiService = networkDeps.yandexApiService,
        mcpRepository = mcpRepository,
        gson = networkDeps.gson
    )
    
    // Initialize database and repositories (use injected or create)
    private val database by lazy { ChatDatabase.getDatabase(application) }
    private val historyRepository: ChatHistoryRepository = injectedHistoryRepository 
        ?: ChatHistoryRepository(database.chatMessageDao())
    private val sessionRepository: ChatSessionRepository = injectedSessionRepository 
        ?: ChatSessionRepository(database.chatSessionDao())
    private val externalMemoryRepository: ExternalMemoryRepository = injectedExternalMemoryRepository 
        ?: ExternalMemoryRepository(database.externalMemoryDao())
    
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
    // Remote models are controlled by Config.ENABLE_REMOTE_MODELS feature toggle
    val availableModels = buildList {
        // Cloud models (only included if ENABLE_REMOTE_MODELS is true)
        if (Config.ENABLE_REMOTE_MODELS) {
            add("amazon/nova-2-lite-v1:free" to "Amazon Nova 2 Lite")
            add("gpt://${Config.YANDEX_FOLDER_ID}/yandexgpt/latest" to "YandexGPT")
        }
        // Local LLM models (always available)
        // These use the "local:" prefix which is automatically handled by ChatRepository
        add("local:qwen2:7b" to "Local: Qwen2 7B")
        add("local:llama2" to "Local: Llama 2")
        add("local:llama3" to "Local: Llama 3")
        add("local:mistral" to "Local: Mistral")
        add("local:mixtral" to "Local: Mixtral")
        add("local:phi" to "Local: Phi")
        add("local:gemma" to "Local: Gemma")
        add("local:qwen" to "Local: Qwen")
        add("local:codellama" to "Local: CodeLlama")
    }
    
    // Settings
    // Default to first available model (will be local if remote models are disabled)
    private val _modelName = MutableStateFlow(
        if (Config.ENABLE_REMOTE_MODELS) Config.DEFAULT_YANDEXGPT_MODEL else "local:qwen2:7b"
    )
    val modelName: StateFlow<String> = _modelName.asStateFlow()
    
    private val _temperature = MutableStateFlow(0.7)
    val temperature: StateFlow<Double> = _temperature.asStateFlow()
    
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()
    
    // Support mode: when enabled, uses SupportContextBuilder for context-aware responses
    private val _supportMode = MutableStateFlow(false)
    val supportMode: StateFlow<Boolean> = _supportMode.asStateFlow()
    
    // Support context builder (optional, injected via Koin)
    private val supportContextBuilder: SupportContextBuilder? = injectedSupportContextBuilder
    
    init {
        // Create a new session on initialization if none exists
        Log.d("GIREV", "repository $mcpRepository")
        viewModelScope.launch {
            if (_currentSessionId.value == null) {
                createNewSession()
            }
        }
        
        // Initialize MCP connection if available
        viewModelScope.launch {
            mcpRepository?.initialize()?.onFailure { error ->
                Log.d("GIREV", "Failed to initialize MCP")
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
     * Enable or disable support mode
     * When enabled, messages will include RAG documentation and CRM ticket context
     */
    fun setSupportMode(enabled: Boolean) {
        _supportMode.value = enabled
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
                        
                        // Add support-specific system prompt when support mode is enabled
                        if (_supportMode.value) {
                            if (length > 0) {
                                append("\n\n")
                            }
                            append("""
                                |You are a helpful support assistant for AIChat application.
                                |When answering user questions:
                                |1. Use the provided product documentation to give accurate information
                                |2. ALWAYS mention the ticket number when referencing user's previous tickets (e.g., "Based on your Ticket #ticket1", "As mentioned in Ticket #ticket2")
                                |3. Use the format "Ticket #[NUMBER]" when referring to specific tickets
                                |4. Reference the user's previous support tickets if they relate to the current question
                                |5. Provide personalized responses based on the user's ticket history
                                |6. If the user asks about an issue they've reported before, ALWAYS mention the ticket number
                                |7. Be helpful, clear, and concise
                                |
                                |IMPORTANT: Always include ticket numbers (e.g., Ticket #ticket1, Ticket #ticket2) when referencing the user's previous support tickets.
                                |
                                |The user's question and relevant context will be provided below.
                            """.trimMargin())
                        }
                        
                        // Add custom system prompt if set
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
                    
                    // Add current user message (with support context if enabled)
                    val userMessageContent = if (_supportMode.value && supportContextBuilder != null) {
                        Log.d("GIREV", "=== ChatViewModel: Support mode enabled ===")
                        Log.d("GIREV", "Building support context for: ${messageText.trim()}")
                        
                        // Build support context and enhance user message
                        val supportContext = supportContextBuilder.buildSupportContext(
                            userQuery = messageText.trim(),
                            includeRagContext = true, // Enable RAG documentation search
                            maxTickets = 5
                        )
                        
                        if (supportContext.isNotEmpty()) {
                            Log.d("GIREV", "✓ Support context built: ${supportContext.length} chars")
                            Log.d("GIREV", "Enhanced user message with context")
                            """
                            |${messageText.trim()}
                            |
                            |$supportContext
                            """.trimMargin()
                        } else {
                            Log.d("GIREV", "✗ Support context is empty, using original message")
                            messageText.trim()
                        }
                    } else {
                        if (_supportMode.value) {
                            Log.d("GIREV", "✗ Support mode enabled but supportContextBuilder is null")
                        }
                        messageText.trim()
                    }
                    add(ChatMessage(role = "user", content = userMessageContent))
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

