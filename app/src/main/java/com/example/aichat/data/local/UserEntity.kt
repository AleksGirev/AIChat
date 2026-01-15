package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user
 * Stores user authentication and profile information
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val email: String,
    val username: String,
    val createdAt: Long,
    val lastLoginAt: Long
)
