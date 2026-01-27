package com.example.aichat.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.Config
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.service.SpeechRecognizerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

/**
 * ViewModel for Voice Agent feature
 * 
 * Handles speech recognition and LLM interaction:
 * Speech → Text → LLM → Text Response
 */
class VoiceAgentViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val tag = "VoiceAgentViewModel"
    
    // Dependencies
    private val networkDeps by lazy { NetworkModule.createNetworkDependencies() }
    private val repository: ChatRepository = ChatRepository(
        apiService = networkDeps.apiService,
        apiKey = Config.OPENAI_API_KEY,
        yandexApiService = networkDeps.yandexApiService,
        localLLMApiService = networkDeps.localLLMApiService,
        mcpRepository = null, // Disable MCP for voice agent
        gson = networkDeps.gson
    )
    
    private val speechRecognizerService = SpeechRecognizerService(application)
    
    // State
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()
    
    private val _llmResponse = MutableStateFlow<String?>(null)
    val llmResponse: StateFlow<String?> = _llmResponse.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Conversation history for context
    private val conversationHistory = mutableListOf<ChatMessage>()
    
    // Model to use (default to local LLM)
    private val modelName = "local:qwen2:7b"
    
    /**
     * Check if speech recognition is available
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        return speechRecognizerService.isAvailable()
    }
    
    /**
     * Check if RECORD_AUDIO permission is granted
     */
    fun hasPermission(): Boolean {
        return speechRecognizerService.hasPermission()
    }
    
    /**
     * Start listening for speech input
     */
    fun startListening() {
        if (!hasPermission()) {
            _error.value = "RECORD_AUDIO permission is required"
            return
        }
        
        if (!isSpeechRecognitionAvailable()) {
            _error.value = "Speech recognition is not available on this device"
            return
        }
        
        viewModelScope.launch {
            try {
                _isListening.value = true
                _recognizedText.value = null
                _error.value = null
                
                var finalText: String? = null
                
                speechRecognizerService.startListening()
                    .catch { e ->
                        Log.e(tag, "Speech recognition error", e)
                        _error.value = e.message ?: "Speech recognition failed"
                        _isListening.value = false
                    }
                    .collect { text ->
                        // Update recognized text for display (could be partial or final)
                        _recognizedText.value = text
                        finalText = text
                    }
                
                // After flow completes, process final result with LLM
                _isListening.value = false
                finalText?.let { processWithLLM(it) }
            } catch (e: Exception) {
                Log.e(tag, "Error starting speech recognition", e)
                _error.value = e.message ?: "Failed to start speech recognition"
                _isListening.value = false
            }
        }
    }
    
    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizerService.stopListening()
        _isListening.value = false
    }
    
    /**
     * Process recognized text with LLM
     */
    private fun processWithLLM(text: String) {
        if (text.isBlank()) {
            _error.value = "No text recognized"
            return
        }
        
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null
                
                // Add user message to history
                conversationHistory.add(ChatMessage(role = "user", content = text))
                
                // Send to LLM
                val result = repository.sendChatRequest(
                    messages = conversationHistory,
                    model = modelName,
                    temperature = 0.7,
                    enableTools = false // Disable tools for voice agent
                )
                
                result.fold(
                    onSuccess = { response ->
                        val assistantMessage = response.choices.firstOrNull()?.message?.content
                        if (assistantMessage != null) {
                            _llmResponse.value = assistantMessage
                            // Add assistant response to history
                            conversationHistory.add(
                                ChatMessage(role = "assistant", content = assistantMessage)
                            )
                        } else {
                            _error.value = "Empty response from LLM"
                        }
                    },
                    onFailure = { e ->
                        Log.e(tag, "LLM request failed", e)
                        _error.value = e.message ?: "Failed to get response from LLM"
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Error processing with LLM", e)
                _error.value = e.message ?: "Failed to process request"
            } finally {
                _isProcessing.value = false
            }
        }
    }
    
    /**
     * Clear conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
        _recognizedText.value = null
        _llmResponse.value = null
        _error.value = null
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        speechRecognizerService.destroy()
    }
}
