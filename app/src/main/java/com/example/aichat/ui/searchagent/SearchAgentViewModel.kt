package com.example.aichat.ui.searchagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.search.Article
import com.example.aichat.data.search.SearchAgentRepository
import com.example.aichat.data.search.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for SearchAgentScreen
 * 
 * Manages UI state and orchestrates article search operations.
 * Handles cancellation when user leaves the screen via viewModelScope.
 */
class SearchAgentViewModel(
    private val repository: SearchAgentRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SearchAgentUiState>(SearchAgentUiState.Idle)
    val uiState: StateFlow<SearchAgentUiState> = _uiState.asStateFlow()
    
    /**
     * Searches for articles on the given topic
     */
    fun searchArticles(topic: String) {
        if (topic.isBlank()) {
            _uiState.value = SearchAgentUiState.Error("Please enter a topic")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = SearchAgentUiState.Loading
            
            when (val result = repository.searchArticles(topic)) {
                is SearchResult.Success -> {
                    _uiState.value = SearchAgentUiState.Success(result.articles)
                }
                is SearchResult.Empty -> {
                    _uiState.value = SearchAgentUiState.Empty
                }
                is SearchResult.Error -> {
                    _uiState.value = SearchAgentUiState.Error(result.message)
                }
            }
        }
    }
    
    /**
     * Clears the current state and returns to idle
     */
    fun clearState() {
        _uiState.value = SearchAgentUiState.Idle
    }
}

/**
 * UI state for SearchAgentScreen
 */
sealed class SearchAgentUiState {
    object Idle : SearchAgentUiState()
    object Loading : SearchAgentUiState()
    data class Success(val articles: List<Article>) : SearchAgentUiState()
    object Empty : SearchAgentUiState()
    data class Error(val message: String) : SearchAgentUiState()
}

