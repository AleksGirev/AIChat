package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for users
 */
@Dao
interface UserDao {
    
    /**
     * Get all users ordered by creation date (newest first)
     */
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<UserEntity>>
    
    /**
     * Get a user by ID
     */
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?
    
    /**
     * Get a user by email
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?
    
    /**
     * Get a user by username
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?
    
    /**
     * Insert a new user
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    /**
     * Update a user
     */
    @Update
    suspend fun updateUser(user: UserEntity)
    
    /**
     * Update user's last login timestamp
     */
    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE userId = :userId")
    suspend fun updateLastLogin(userId: String, timestamp: Long)
    
    /**
     * Delete a user
     */
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)
    
    /**
     * Delete all users
     */
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
