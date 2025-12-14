package com.example.aichat.data.util

import android.content.Context
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.local.ChatSessionRepository
import com.example.aichat.data.local.ExternalMemoryRepository
import com.example.aichat.data.local.ChatSessionEntity
import com.example.aichat.data.local.ExternalMemoryEntity
import com.example.aichat.ui.model.UiMessage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileWriter

/**
 * Data class for JSON export
 */
data class MemoryExportData(
    val sessions: List<ChatSessionEntity>,
    val messages: List<UiMessage>,
    val externalMemory: List<ExternalMemoryEntity>,
    val exportTimestamp: Long,
    val version: String = "1.0"
)

/**
 * Service for exporting and importing chat data to/from JSON
 */
class JsonMemoryExporter(
    private val context: Context,
    private val sessionRepository: ChatSessionRepository,
    private val historyRepository: ChatHistoryRepository,
    private val externalMemoryRepository: ExternalMemoryRepository
) {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    /**
     * Exports all data to JSON file
     */
    suspend fun exportAllToJson(): File {
        // Get all sessions
        val sessions = sessionRepository.getAllSessions().first()
        
        // Get all messages from all sessions
        val allMessages = mutableListOf<UiMessage>()
        sessions.forEach { session ->
            val messages = historyRepository.getMessagesBySessionId(session.id)
            allMessages.addAll(messages)
        }
        
        // Get all external memory
        val externalMemory = externalMemoryRepository.getAllMemory().first()
        
        // Create export data
        val exportData = MemoryExportData(
            sessions = sessions,
            messages = allMessages,
            externalMemory = externalMemory,
            exportTimestamp = System.currentTimeMillis()
        )
        
        // Convert to JSON
        val json = gson.toJson(exportData)
        
        // Write to file
        val fileName = "memory_export_${System.currentTimeMillis()}.json"
        val file = File(context.filesDir, fileName)
        FileWriter(file).use { writer ->
            writer.write(json)
        }
        
        return file
    }
    
    /**
     * Exports data for a specific session to JSON
     */
    suspend fun exportSessionToJson(sessionId: String): File {
        // Get session
        val session = sessionRepository.getSessionById(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        // Get messages for this session
        val messages = historyRepository.getMessagesBySessionId(sessionId)
        
        // Get external memory for this session
        val externalMemory = externalMemoryRepository.getBySessionId(sessionId)
        
        // Create export data
        val exportData = MemoryExportData(
            sessions = listOf(session),
            messages = messages,
            externalMemory = externalMemory,
            exportTimestamp = System.currentTimeMillis()
        )
        
        // Convert to JSON
        val json = gson.toJson(exportData)
        
        // Write to file
        val fileName = "session_export_${sessionId}_${System.currentTimeMillis()}.json"
        val file = File(context.filesDir, fileName)
        FileWriter(file).use { writer ->
            writer.write(json)
        }
        
        return file
    }
    
    /**
     * Imports data from JSON file
     */
    suspend fun importFromJson(json: String): Result<Unit> {
        return try {
            // Parse JSON
            val exportData = gson.fromJson(json, MemoryExportData::class.java)
            
            // Import sessions
            exportData.sessions.forEach { session ->
                sessionRepository.createSession(
                    id = session.id,
                    title = session.title,
                    summary = session.summary
                )
                // Update other fields
                val existing = sessionRepository.getSessionById(session.id)
                existing?.let {
                    sessionRepository.updateSession(
                        it.copy(
                            createdAt = session.createdAt,
                            updatedAt = session.updatedAt,
                            messageCount = session.messageCount,
                            lastMessagePreview = session.lastMessagePreview
                        )
                    )
                }
            }
            
            // Import messages
            historyRepository.saveMessages(exportData.messages)
            
            // Import external memory
            exportData.externalMemory.forEach { memory ->
                externalMemoryRepository.saveMemory(
                    key = memory.key,
                    content = memory.content,
                    category = memory.category,
                    sessionId = memory.sessionId,
                    relatedMessageIds = memory.relatedMessageIds
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Imports data from JSON file
     */
    suspend fun importFromJsonFile(file: File): Result<Unit> {
        return try {
            val json = file.readText()
            importFromJson(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

