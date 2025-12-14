package com.example.aichat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for storing chat messages, sessions, and external memory
 */
@Database(
    entities = [ChatMessageEntity::class, ChatSessionEntity::class, ExternalMemoryEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class ChatDatabase : RoomDatabase() {
    
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun externalMemoryDao(): ExternalMemoryDao
    
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
