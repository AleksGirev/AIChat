package com.example.aichat.console.mcp.crm

import kotlinx.serialization.json.*
import java.io.File

/**
 * Service for managing users in CRM
 * Reads users from JSON file
 */
class UserService(
    private val dataFile: File = resolveDataFile()
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        /**
         * Resolves the data file path, trying multiple locations
         */
        private fun resolveDataFile(): File {
            // Try multiple possible locations
            val possiblePaths = listOf(
                // Relative to current working directory (when run from project root)
                File("console-agent/data/crm_data.json"),
                // Relative to console-agent directory (when run from console-agent)
                File("data/crm_data.json"),
                // Absolute path from classpath (when run as JAR)
                File(System.getProperty("user.dir") + "/console-agent/data/crm_data.json"),
                // Try parent directory
                File("../console-agent/data/crm_data.json"),
                // Try from project root
                File(System.getProperty("user.dir") + "/data/crm_data.json")
            )
            
            for (path in possiblePaths) {
                if (path.exists() && path.isFile) {
                    System.err.println("Found CRM data file at: ${path.absolutePath}")
                    return path
                }
            }
            
            // Default fallback
            val defaultPath = File("console-agent/data/crm_data.json")
            System.err.println("WARNING: CRM data file not found. Using default path: ${defaultPath.absolutePath}")
            System.err.println("Current working directory: ${System.getProperty("user.dir")}")
            return defaultPath
        }
    }
    
    /**
     * Load CRM data from JSON file
     */
    private fun loadData(): CrmData {
        return try {
            if (!dataFile.exists()) {
                System.err.println("ERROR: CRM data file does not exist at: ${dataFile.absolutePath}")
                System.err.println("Current working directory: ${System.getProperty("user.dir")}")
                return CrmData()
            }
            val content = dataFile.readText()
            System.err.println("Loaded CRM data from: ${dataFile.absolutePath} (${content.length} chars)")
            json.decodeFromString<CrmData>(content)
        } catch (e: Exception) {
            System.err.println("Error loading CRM data from ${dataFile.absolutePath}: ${e.message}")
            e.printStackTrace()
            CrmData()
        }
    }
    
    /**
     * Get user by ID
     */
    fun getUser(userId: String): CrmUser? {
        val data = loadData()
        return data.users.find { it.userId == userId }
    }
    
    /**
     * Get user by email
     */
    fun getUserByEmail(email: String): CrmUser? {
        val data = loadData()
        return data.users.find { it.email == email }
    }
    
    /**
     * Get user as JSON string
     */
    fun getUserJson(userId: String): String {
        val user = getUser(userId)
        return if (user != null) {
            json.encodeToString(CrmUser.serializer(), user)
        } else {
            "null"
        }
    }
}
