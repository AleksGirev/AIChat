package com.example.aichat.console.mcp.crm

import kotlinx.serialization.json.*
import java.io.File

/**
 * Service for managing tickets
 * Reads and writes tickets from/to JSON file
 */
class TicketService(
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
    fun loadData(): CrmData {
        return try {
            if (!dataFile.exists()) {
                System.err.println("ERROR: CRM data file does not exist at: ${dataFile.absolutePath}")
                System.err.println("Current working directory: ${System.getProperty("user.dir")}")
                // Return empty data if file doesn't exist
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
     * Save CRM data to JSON file
     */
    fun saveData(data: CrmData) {
        try {
            dataFile.parentFile?.mkdirs()
            val content = json.encodeToString(CrmData.serializer(), data)
            dataFile.writeText(content)
        } catch (e: Exception) {
            println("Error saving CRM data: ${e.message}")
            throw e
        }
    }
    
    /**
     * Get all tickets for a user
     */
    fun getUserTickets(userId: String): List<Ticket> {
        val data = loadData()
        return data.tickets.filter { it.userId == userId }
    }
    
    /**
     * Get a specific ticket by ID
     */
    fun getTicket(ticketId: String): Ticket? {
        val data = loadData()
        return data.tickets.find { it.ticketId == ticketId }
    }
    
    /**
     * Search tickets by query
     */
    fun searchTickets(query: String, userId: String? = null): List<Ticket> {
        val data = loadData()
        val lowerQuery = query.lowercase()
        
        var tickets = data.tickets
        
        // Filter by userId if provided
        if (userId != null) {
            tickets = tickets.filter { it.userId == userId }
        }
        
        // Search in title, description, and messages
        return tickets.filter { ticket ->
            ticket.title.lowercase().contains(lowerQuery) ||
            ticket.description.lowercase().contains(lowerQuery) ||
            ticket.messages.any { it.content.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * Get tickets as JSON string
     */
    fun getUserTicketsJson(userId: String): String {
        val tickets = getUserTickets(userId)
        // Build JSON array manually
        return buildString {
            append("[")
            tickets.forEachIndexed { index, ticket ->
                if (index > 0) append(",")
                append(json.encodeToString(Ticket.serializer(), ticket))
            }
            append("]")
        }
    }
    
    /**
     * Get ticket as JSON string
     */
    fun getTicketJson(ticketId: String): String {
        val ticket = getTicket(ticketId)
        return if (ticket != null) {
            json.encodeToString(Ticket.serializer(), ticket)
        } else {
            "null"
        }
    }
    
    /**
     * Get search results as JSON string
     */
    fun searchTicketsJson(query: String, userId: String? = null): String {
        val tickets = searchTickets(query, userId)
        // Build JSON array manually
        return buildString {
            append("[")
            tickets.forEachIndexed { index, ticket ->
                if (index > 0) append(",")
                append(json.encodeToString(Ticket.serializer(), ticket))
            }
            append("]")
        }
    }
}
