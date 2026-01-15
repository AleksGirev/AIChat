package com.example.aichat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for storing chat messages, sessions, external memory, weather data, and users.
 * 
 * Version History:
 * - v4: Added ExternalMemoryEntity
 * - v6: Added WeatherEntity for Belarusian cities temperature data
 * - v7: Removed SyncUpdateEntity (background sync functionality removed)
 * - v8: Added UserEntity and userId field to ChatSessionEntity
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        ExternalMemoryEntity::class,
        WeatherEntity::class,
        UserEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class ChatDatabase : RoomDatabase() {
    
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun externalMemoryDao(): ExternalMemoryDao
    abstract fun weatherDao(): WeatherDao
    abstract fun userDao(): UserDao
    
    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null
        
        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                    .fallbackToDestructiveMigration() // For simplicity, recreate on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
