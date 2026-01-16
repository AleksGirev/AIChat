package com.example.aichat.console.mcp.tasks

import kotlinx.serialization.Serializable

/**
 * Data models for Team Tasks system
 */

@Serializable
data class TeamMember(
    val userId: String,
    val name: String,
    val email: String,
    val role: String
)

@Serializable
data class Task(
    val taskId: String,
    val title: String,
    val description: String,
    val status: String, // "todo", "in-progress", "review", "done", "blocked", "cancelled"
    val priority: String, // "low", "medium", "high", "critical"
    val assignee: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val dueDate: Long? = null,
    val blockedBy: List<String> = emptyList(), // Task IDs that block this task
    val blocks: List<String> = emptyList(), // Task IDs that this task blocks
    val estimatedHours: Int = 0,
    val actualHours: Int = 0
)

@Serializable
data class TeamData(
    val tasks: List<Task> = emptyList(),
    val teamMembers: List<TeamMember> = emptyList()
)

@Serializable
data class ProjectStatus(
    val totalTasks: Int,
    val tasksByStatus: Map<String, Int>,
    val tasksByPriority: Map<String, Int>,
    val blockedTasks: Int,
    val tasksByAssignee: Map<String, Int>,
    val overdueTasks: Int,
    val totalEstimatedHours: Int,
    val totalActualHours: Int
)
