package com.example.aichat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.auth.AuthManager
import com.example.aichat.data.local.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for authentication screen
 * Handles login and registration logic
 */
class AuthViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val authManager: AuthManager
) : AndroidViewModel(application) {
    
    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Success(val userId: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    /**
     * Login with email/username and password
     * For MVP: Simple string comparison (no hashing)
     */
    fun login(emailOrUsername: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            try {
                // Try to find user by email or username
                val user = userRepository.getUserByEmail(emailOrUsername)
                    ?: userRepository.getUserByUsername(emailOrUsername)
                
                if (user == null) {
                    _authState.value = AuthState.Error("User not found. Please register first.")
                    return@launch
                }
                
                // For MVP: Simple password check (in production, use proper hashing)
                // For now, we'll use a simple mock: password = "password123" for all users
                // In real implementation, store hashed password in UserEntity
                if (password != "password123") {
                    _authState.value = AuthState.Error("Invalid password")
                    return@launch
                }
                
                // Update last login
                userRepository.updateLastLogin(user.userId)
                
                // Store user ID in EncryptedSharedPreferences
                authManager.setUserId(user.userId)
                
                _authState.value = AuthState.Success(user.userId)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Login failed: ${e.message}")
            }
        }
    }
    
    /**
     * Register a new user
     */
    fun register(email: String, username: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            try {
                // Validate input
                if (email.isBlank() || username.isBlank() || password.isBlank()) {
                    _authState.value = AuthState.Error("All fields are required")
                    return@launch
                }
                
                // Check if user already exists
                val existingUser = userRepository.getUserByEmail(email)
                    ?: userRepository.getUserByUsername(username)
                
                if (existingUser != null) {
                    _authState.value = AuthState.Error("User with this email or username already exists")
                    return@launch
                }
                
                // Create new user
                val userId = UUID.randomUUID().toString()
                val user = userRepository.createUser(
                    userId = userId,
                    email = email,
                    username = username
                )
                
                // Store user ID in EncryptedSharedPreferences
                authManager.setUserId(userId)
                
                _authState.value = AuthState.Success(userId)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Registration failed: ${e.message}")
            }
        }
    }
    
    /**
     * Check if user is already authenticated
     */
    fun checkAuthState(): Boolean {
        return authManager.isAuthenticated()
    }
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? {
        return authManager.getUserId()
    }
    
    /**
     * Reset auth state
     */
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
}
