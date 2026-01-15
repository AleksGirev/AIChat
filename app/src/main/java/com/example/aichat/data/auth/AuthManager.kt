package com.example.aichat.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages authentication state using EncryptedSharedPreferences
 * Stores user ID securely after login
 */
class AuthManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val authPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_preferences",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
    }
    
    /**
     * Store user ID after successful login
     */
    fun setUserId(userId: String) {
        authPreferences.edit()
            .putString(KEY_USER_ID, userId)
            .putBoolean(KEY_IS_AUTHENTICATED, true)
            .apply()
    }
    
    /**
     * Get current user ID
     */
    fun getUserId(): String? {
        return authPreferences.getString(KEY_USER_ID, null)
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return authPreferences.getBoolean(KEY_IS_AUTHENTICATED, false) && getUserId() != null
    }
    
    /**
     * Clear authentication state (logout)
     */
    fun clearAuth() {
        authPreferences.edit()
            .remove(KEY_USER_ID)
            .putBoolean(KEY_IS_AUTHENTICATED, false)
            .apply()
    }
}
