package com.example.aichat.data.support

import android.util.Log
import com.example.aichat.data.auth.AuthManager
import com.example.aichat.data.crm.CrmMcpClient
import com.example.aichat.data.rag.RagService
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds support context by combining:
 * 1. RAG documentation context (from product docs)
 * 2. CRM ticket context (user's previous tickets)
 * 
 * This context is then used to enhance LLM responses with personalized,
 * context-aware support information.
 */
class SupportContextBuilder(
    private val authManager: AuthManager,
    private val crmMcpClient: CrmMcpClient,
    private val ragService: RagService? = null,
    private val gson: Gson
) {
    private val tag = "SupportContextBuilder"
    
    /**
     * Builds support context for a user query
     * 
     * @param userQuery The user's question/query
     * @param includeRagContext Whether to include RAG documentation (for MVP, can be false)
     * @param maxTickets Maximum number of tickets to include (default: 5)
     * @return Formatted context string ready to be included in LLM prompt
     */
    suspend fun buildSupportContext(
        userQuery: String,
        includeRagContext: Boolean = false,
        maxTickets: Int = 5
    ): String = withContext(Dispatchers.IO) {
        Log.d("GIREV", "=== SupportContextBuilder: Building support context ===")
        Log.d("GIREV", "Query: $userQuery")
        Log.d("GIREV", "Include RAG: $includeRagContext")
        
        val contextBuilder = StringBuilder()
        
        // Get current user ID
        val userId = authManager.getUserId()
        if (userId == null) {
            Log.w("GIREV", "No user ID found, skipping CRM context")
            Log.w(tag, "No user ID found, skipping CRM context")
            return@withContext buildContextWithoutUser(contextBuilder, userQuery, includeRagContext)
        }
        
        Log.d("GIREV", "User ID: $userId")
        
        var ragUsed = false
        var ticketsUsed = false
        
        // Add RAG context if enabled
        if (includeRagContext) {
            Log.d("GIREV", "Searching RAG documentation...")
            val ragContext = getRagContext(userQuery)
            if (ragContext.isNotEmpty()) {
                Log.d("GIREV", "✓ RAG context found: ${ragContext.length} characters")
                ragUsed = true
                contextBuilder.appendLine("=== Product Documentation ===")
                contextBuilder.appendLine(ragContext)
                contextBuilder.appendLine()
            } else {
                Log.d("GIREV", "✗ No RAG context found")
            }
        }
        
        // Get user's tickets
        Log.d("GIREV", "Retrieving user tickets from CRM...")
        val ticketsContext = getUserTicketsContext(userId, userQuery, maxTickets)
        if (ticketsContext.isNotEmpty()) {
            Log.d("GIREV", "✓ Tickets context found: ${ticketsContext.length} characters")
            ticketsUsed = true
            contextBuilder.appendLine("=== User's Previous Support Tickets ===")
            contextBuilder.appendLine(ticketsContext)
            contextBuilder.appendLine()
        } else {
            Log.d("GIREV", "✗ No tickets context found")
        }
        
        // Build final context
        val finalContext = contextBuilder.toString().trim()
        
        Log.d("GIREV", "Final context length: ${finalContext.length} characters")
        Log.d("GIREV", "RAG used: $ragUsed, Tickets used: $ticketsUsed")
        
        if (finalContext.isEmpty()) {
            Log.d("GIREV", "No context built, returning empty")
            return@withContext ""
        }
        
        // Format as context block with emphasis on ticket numbers
        """
        |[Support Context]
        |$finalContext
        |
        |IMPORTANT: When answering the user's question:
        |1. ALWAYS mention the ticket number (e.g., "Ticket #ticket1", "Ticket #ticket2") when referencing their previous tickets
        |2. Use the format "Based on your Ticket #[NUMBER]" or "As mentioned in Ticket #[NUMBER]"
        |3. Reference specific ticket numbers when the user asks about issues they've reported before
        |4. Use the documentation to provide accurate information about the product
        |5. Combine information from both tickets and documentation for comprehensive answers
        """.trimMargin()
    }
    
    /**
     * Gets RAG context from product documentation
     * Uses RagService to search documentation files
     */
    private suspend fun getRagContext(query: String): String {
        return try {
            if (ragService != null) {
                Log.d("GIREV", "Calling RagService.searchDocumentation for: $query")
                val ragContext = ragService.searchDocumentation(query, limit = 5)
                if (ragContext.isNotEmpty()) {
                    Log.d("GIREV", "✓ RAG context retrieved: ${ragContext.length} chars")
                    Log.d(tag, "RAG context found: ${ragContext.length} chars")
                } else {
                    Log.d("GIREV", "✗ RAG context empty for query: $query")
                    Log.d(tag, "No RAG context found for query: $query")
                }
                ragContext
            } else {
                Log.d("GIREV", "✗ RagService is null, skipping RAG context")
                Log.d(tag, "RagService not available, skipping RAG context")
                ""
            }
        } catch (e: Exception) {
            Log.e("GIREV", "✗ Error getting RAG context: ${e.message}", e)
            Log.e(tag, "Error getting RAG context", e)
            ""
        }
    }
    
    /**
     * Maps app user ID to CRM user ID for testing
     * In production, this would be handled by the CRM system
     */
    private fun mapToCrmUserId(appUserId: String): String {
        // For testing: Map any user to "user1" to match mock CRM data
        // In production, this would query the CRM system for the actual mapping
        Log.d("GIREV", "Mapping app userId: $appUserId to CRM userId: user1 (for testing)")
        return "user1"
    }
    
    /**
     * Gets user's tickets context
     */
    private suspend fun getUserTicketsContext(
        userId: String,
        userQuery: String,
        maxTickets: Int
    ): String {
        return try {
            Log.d("GIREV", "Getting tickets for user: $userId, query: $userQuery")
            
            if (!crmMcpClient.isConnected()) {
                Log.w("GIREV", "✗ CRM MCP client not connected, skipping ticket context")
                Log.w(tag, "CRM MCP client not connected, skipping ticket context")
                return ""
            }
            
            Log.d("GIREV", "✓ CRM MCP client is connected")
            
            // Map app user ID to CRM user ID
            val crmUserId = mapToCrmUserId(userId)
            Log.d("GIREV", "Using CRM userId: $crmUserId for app userId: $userId")
            
            val tickets: List<JsonObject>
            
            // First, try to search for relevant tickets
            Log.d("GIREV", "Searching tickets with query: $userQuery, userId: $crmUserId")
            val searchResult = crmMcpClient.searchTickets(userQuery, crmUserId)
            
            tickets = if (searchResult.isSuccess) {
                val ticketsJson = searchResult.getOrThrow()
                Log.d("GIREV", "✓ Ticket search successful, parsing JSON...")
                Log.d("GIREV", "Raw response: $ticketsJson")
                val parsed = parseTicketsJson(ticketsJson)
                Log.d("GIREV", "✓ Parsed ${parsed.size} tickets from search")
                
                // If search returns empty, fall back to getAllTickets
                if (parsed.isEmpty()) {
                    Log.d("GIREV", "Search returned empty, falling back to getAllTickets")
                    val allTicketsResult = crmMcpClient.getUserTickets(crmUserId)
                    if (allTicketsResult.isSuccess) {
                        val allTicketsJson = allTicketsResult.getOrThrow()
                        Log.d("GIREV", "✓ getAllTickets successful, parsing JSON...")
                        Log.d("GIREV", "Raw response: $allTicketsJson")
                        val allParsed = parseTicketsJson(allTicketsJson)
                        Log.d("GIREV", "✓ Parsed ${allParsed.size} tickets from getAllTickets")
                        allParsed
                    } else {
                        Log.e("GIREV", "✗ getAllTickets failed: ${allTicketsResult.exceptionOrNull()?.message}")
                        emptyList()
                    }
                } else {
                    parsed
                }
            } else {
                Log.d("GIREV", "✗ Ticket search failed, falling back to getAllTickets")
                // Fallback: get all user tickets (use CRM user ID)
                val allTicketsResult = crmMcpClient.getUserTickets(crmUserId)
                if (allTicketsResult.isSuccess) {
                    val ticketsJson = allTicketsResult.getOrThrow()
                    Log.d("GIREV", "✓ getAllTickets successful, parsing JSON...")
                    Log.d("GIREV", "Raw response: $ticketsJson")
                    val parsed = parseTicketsJson(ticketsJson)
                    Log.d("GIREV", "✓ Parsed ${parsed.size} tickets from getAllTickets")
                    parsed
                } else {
                    Log.e("GIREV", "✗ getAllTickets also failed: ${allTicketsResult.exceptionOrNull()?.message}")
                    emptyList()
                }
            }
            
            if (tickets.isEmpty()) {
                Log.d("GIREV", "No tickets found for user")
                return ""
            }
            
            Log.d("GIREV", "Formatting ${tickets.size} tickets (max: $maxTickets)")
            
            // Format tickets (limit to maxTickets)
            val relevantTickets = tickets.take(maxTickets)
            val ticketsText = relevantTickets.joinToString("\n\n") { ticket ->
                formatTicket(ticket)
            }
            
            Log.d("GIREV", "✓ Formatted tickets context: ${ticketsText.length} chars")
            Log.d("GIREV", "Ticket IDs used: ${relevantTickets.map { it.get("ticketId")?.asString ?: "unknown" }.joinToString(", ")}")
            
            ticketsText
        } catch (e: Exception) {
            Log.e("GIREV", "✗ Error getting user tickets context: ${e.message}", e)
            Log.e(tag, "Error getting user tickets context", e)
            ""
        }
    }
    
    /**
     * Builds context without user (for unauthenticated users)
     */
    private suspend fun buildContextWithoutUser(
        contextBuilder: StringBuilder,
        userQuery: String,
        includeRagContext: Boolean
    ): String {
        if (includeRagContext) {
            val ragContext = getRagContext(userQuery)
            if (ragContext.isNotEmpty()) {
                contextBuilder.appendLine("=== Product Documentation ===")
                contextBuilder.appendLine(ragContext)
            }
        }
        
        return contextBuilder.toString().trim()
    }
    
    /**
     * Parse tickets JSON string into list of ticket maps
     */
    private fun parseTicketsJson(jsonString: String): List<JsonObject> {
        return try {
            val jsonElement = gson.fromJson(jsonString, com.google.gson.JsonElement::class.java)
            when {
                jsonElement.isJsonArray -> {
                    jsonElement.asJsonArray.map { it.asJsonObject }
                }
                jsonElement.isJsonObject -> {
                    listOf(jsonElement.asJsonObject)
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing tickets JSON", e)
            emptyList()
        }
    }
    
    /**
     * Format a ticket for context
     * Emphasizes ticket number for LLM to reference it in responses
     */
    private fun formatTicket(ticket: JsonObject): String {
        val ticketId = ticket.get("ticketId")?.asString ?: "unknown"
        val title = ticket.get("title")?.asString ?: "No title"
        val status = ticket.get("status")?.asString ?: "unknown"
        val priority = ticket.get("priority")?.asString ?: "unknown"
        val description = ticket.get("description")?.asString ?: ""
        
        val messages = ticket.get("messages")?.asJsonArray
        val messagesText = if (messages != null && messages.size() > 0) {
            messages.take(3).joinToString("\n") { msg ->
                val sender = msg.asJsonObject.get("sender")?.asString ?: "unknown"
                val content = msg.asJsonObject.get("content")?.asString ?: ""
                "$sender: $content"
            }
        } else {
            ""
        }
        
        // Format with emphasis on ticket number
        return buildString {
            appendLine("TICKET NUMBER: #$ticketId")
            appendLine("Title: $title")
            appendLine("Status: $status | Priority: $priority")
            if (description.isNotEmpty()) {
                appendLine("Description: $description")
            }
            if (messagesText.isNotEmpty()) {
                appendLine("Recent conversation:")
                appendLine(messagesText)
            }
            appendLine("--- End of Ticket #$ticketId ---")
        }
    }
}
