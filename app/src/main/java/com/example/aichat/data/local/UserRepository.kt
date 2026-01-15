package com.example.aichat.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing users
 */
class UserRepository(
    private val userDao: UserDao
) {
    /**
     * Get all users as a Flow
     */
    fun getAllUsers(): Flow<List<UserEntity>> {
        return userDao.getAllUsers()
    }
    
    /**
     * Get a user by ID
     */
    suspend fun getUserById(userId: String): UserEntity? {
        return userDao.getUserById(userId)
    }
    
    /**
     * Get a user by email
     */
    suspend fun getUserByEmail(email: String): UserEntity? {
        return userDao.getUserByEmail(email)
    }
    
    /**
     * Get a user by username
     */
    suspend fun getUserByUsername(username: String): UserEntity? {
        return userDao.getUserByUsername(username)
    }
    
    /**
     * Create a new user
     */
    suspend fun createUser(
        userId: String,
        email: String,
        username: String
    ): UserEntity {
        val now = System.currentTimeMillis()
        val user = UserEntity(
            userId = userId,
            email = email,
            username = username,
            createdAt = now,
            lastLoginAt = now
        )
        userDao.insertUser(user)
        return user
    }
    
    /**
     * Update user
     */
    suspend fun updateUser(user: UserEntity) {
        userDao.updateUser(user)
    }
    
    /**
     * Update user's last login timestamp
     */
    suspend fun updateLastLogin(userId: String) {
        userDao.updateLastLogin(userId, System.currentTimeMillis())
    }
    
    /**
     * Delete a user
     */
    suspend fun deleteUser(userId: String) {
        userDao.deleteUser(userId)
    }
    
    /**
     * Delete all users
     */
    suspend fun deleteAllUsers() {
        userDao.deleteAllUsers()
    }
}
