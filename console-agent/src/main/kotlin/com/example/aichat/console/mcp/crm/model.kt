package com.example.aichat.console.mcp.crm

import kotlinx.serialization.Serializable

/**
 * Data models for CRM system
 */

@Serializable
data class CrmUser(
    val userId: String,
    val email: String,
    val username: String,
    val createdAt: Long
)

@Serializable
data class TicketMessage(
    val messageId: String,
    val sender: String, // "user" or "support"
    val content: String,
    val timestamp: Long
)

@Serializable
data class Ticket(
    val ticketId: String,
    val userId: String,
    val title: String,
    val description: String,
    val status: String, // "open", "closed", "in-progress"
    val priority: String, // "low", "medium", "high"
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<TicketMessage> = emptyList()
)

@Serializable
data class CrmData(
    val users: List<CrmUser> = emptyList(),
    val tickets: List<Ticket> = emptyList()
)
