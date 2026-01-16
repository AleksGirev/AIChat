package com.example.aichat.console.mcp.tasks

import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/**
 * Service for managing team tasks
 * Reads and writes tasks from/to JSON file
 */
class TaskService(
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
                File("console-agent/data/team_tasks.json"),
                // Relative to console-agent directory (when run from console-agent)
                File("data/team_tasks.json"),
                // Absolute path from classpath (when run as JAR)
                File(System.getProperty("user.dir") + "/console-agent/data/team_tasks.json"),
                // Try parent directory
                File("../console-agent/data/team_tasks.json"),
                // Try from project root
                File(System.getProperty("user.dir") + "/data/team_tasks.json")
            )
            
            for (path in possiblePaths) {
                if (path.exists() && path.isFile) {
                    System.err.println("Found team tasks data file at: ${path.absolutePath}")
                    return path
                }
            }
            
            // Default fallback
            val defaultPath = File("console-agent/data/team_tasks.json")
            System.err.println("WARNING: Team tasks data file not found. Using default path: ${defaultPath.absolutePath}")
            System.err.println("Current working directory: ${System.getProperty("user.dir")}")
            return defaultPath
        }
    }
    
    /**
     * Load team data from JSON file
     */
    fun loadData(): TeamData {
        return try {
            if (!dataFile.exists()) {
                System.err.println("ERROR: Team tasks data file does not exist at: ${dataFile.absolutePath}")
                System.err.println("Current working directory: ${System.getProperty("user.dir")}")
                // Return empty data if file doesn't exist
                return TeamData()
            }
            val content = dataFile.readText()
            System.err.println("Loaded team tasks data from: ${dataFile.absolutePath} (${content.length} chars)")
            json.decodeFromString<TeamData>(content)
        } catch (e: Exception) {
            System.err.println("Error loading team tasks data from ${dataFile.absolutePath}: ${e.message}")
            e.printStackTrace()
            TeamData()
        }
    }
    
    /**
     * Save team data to JSON file
     */
    fun saveData(data: TeamData) {
        try {
            dataFile.parentFile?.mkdirs()
            val content = json.encodeToString(TeamData.serializer(), data)
            dataFile.writeText(content)
        } catch (e: Exception) {
            println("Error saving team tasks data: ${e.message}")
            throw e
        }
    }
    
    /**
     * Get all tasks with optional filters
     */
    fun getTasks(
        status: String? = null,
        priority: String? = null,
        assignee: String? = null,
        tags: List<String>? = null
    ): List<Task> {
        val data = loadData()
        var tasks = data.tasks
        
        // Apply filters
        if (status != null) {
            tasks = tasks.filter { it.status == status }
        }
        if (priority != null) {
            tasks = tasks.filter { it.priority == priority }
        }
        if (assignee != null) {
            tasks = tasks.filter { it.assignee == assignee }
        }
        if (tags != null && tags.isNotEmpty()) {
            tasks = tasks.filter { task -> tags.any { tag -> task.tags.contains(tag) } }
        }
        
        return tasks
    }
    
    /**
     * Get a specific task by ID
     */
    fun getTask(taskId: String): Task? {
        val data = loadData()
        return data.tasks.find { it.taskId == taskId }
    }
    
    /**
     * Get tasks by priority
     */
    fun getTasksByPriority(priority: String): List<Task> {
        val data = loadData()
        return data.tasks.filter { it.priority == priority }
    }
    
    /**
     * Create a new task
     */
    fun createTask(
        title: String,
        description: String,
        priority: String = "medium",
        assignee: String? = null,
        tags: List<String> = emptyList(),
        dueDate: Long? = null,
        estimatedHours: Int = 0,
        blockedBy: List<String> = emptyList()
    ): Task {
        val data = loadData()
        val now = System.currentTimeMillis()
        val taskId = "task-${UUID.randomUUID().toString().substring(0, 8)}"
        
        val newTask = Task(
            taskId = taskId,
            title = title,
            description = description,
            status = "todo",
            priority = priority,
            assignee = assignee,
            tags = tags,
            createdAt = now,
            updatedAt = now,
            dueDate = dueDate,
            blockedBy = blockedBy,
            blocks = emptyList(),
            estimatedHours = estimatedHours,
            actualHours = 0
        )
        
        val updatedTasks = data.tasks + newTask
        saveData(data.copy(tasks = updatedTasks))
        
        return newTask
    }
    
    /**
     * Update a task
     */
    fun updateTask(
        taskId: String,
        title: String? = null,
        description: String? = null,
        status: String? = null,
        priority: String? = null,
        assignee: String? = null,
        tags: List<String>? = null,
        dueDate: Long? = null,
        estimatedHours: Int? = null,
        actualHours: Int? = null
    ): Task? {
        val data = loadData()
        val taskIndex = data.tasks.indexOfFirst { it.taskId == taskId }
        
        if (taskIndex == -1) {
            return null
        }
        
        val existingTask = data.tasks[taskIndex]
        val updatedTask = existingTask.copy(
            title = title ?: existingTask.title,
            description = description ?: existingTask.description,
            status = status ?: existingTask.status,
            priority = priority ?: existingTask.priority,
            assignee = assignee ?: existingTask.assignee,
            tags = tags ?: existingTask.tags,
            dueDate = dueDate ?: existingTask.dueDate,
            estimatedHours = estimatedHours ?: existingTask.estimatedHours,
            actualHours = actualHours ?: existingTask.actualHours,
            updatedAt = System.currentTimeMillis()
        )
        
        val updatedTasks = data.tasks.toMutableList()
        updatedTasks[taskIndex] = updatedTask
        saveData(data.copy(tasks = updatedTasks))
        
        return updatedTask
    }
    
    /**
     * Get project status summary
     */
    fun getProjectStatus(): ProjectStatus {
        val data = loadData()
        val tasks = data.tasks
        val now = System.currentTimeMillis()
        
        val tasksByStatus = tasks.groupingBy { it.status }.eachCount()
        val tasksByPriority = tasks.groupingBy { it.priority }.eachCount()
        val blockedTasks = tasks.count { it.blockedBy.isNotEmpty() }
        val tasksByAssignee = tasks.filter { it.assignee != null }
            .groupingBy { it.assignee!! }
            .eachCount()
        val overdueTasks = tasks.count { 
            it.dueDate != null && it.dueDate < now && it.status !in listOf("done", "cancelled")
        }
        val totalEstimatedHours = tasks.sumOf { it.estimatedHours }
        val totalActualHours = tasks.sumOf { it.actualHours }
        
        return ProjectStatus(
            totalTasks = tasks.size,
            tasksByStatus = tasksByStatus,
            tasksByPriority = tasksByPriority,
            blockedTasks = blockedTasks,
            tasksByAssignee = tasksByAssignee,
            overdueTasks = overdueTasks,
            totalEstimatedHours = totalEstimatedHours,
            totalActualHours = totalActualHours
        )
    }
    
    /**
     * Get tasks as JSON string
     */
    fun getTasksJson(
        status: String? = null,
        priority: String? = null,
        assignee: String? = null,
        tags: List<String>? = null
    ): String {
        val tasks = getTasks(status, priority, assignee, tags)
        return buildString {
            append("[")
            tasks.forEachIndexed { index, task ->
                if (index > 0) append(",")
                append(json.encodeToString(Task.serializer(), task))
            }
            append("]")
        }
    }
    
    /**
     * Get task as JSON string
     */
    fun getTaskJson(taskId: String): String {
        val task = getTask(taskId)
        return if (task != null) {
            json.encodeToString(Task.serializer(), task)
        } else {
            "null"
        }
    }
    
    /**
     * Get tasks by priority as JSON string
     */
    fun getTasksByPriorityJson(priority: String): String {
        val tasks = getTasksByPriority(priority)
        return buildString {
            append("[")
            tasks.forEachIndexed { index, task ->
                if (index > 0) append(",")
                append(json.encodeToString(Task.serializer(), task))
            }
            append("]")
        }
    }
    
    /**
     * Get project status as JSON string
     */
    fun getProjectStatusJson(): String {
        val status = getProjectStatus()
        return json.encodeToString(ProjectStatus.serializer(), status)
    }
}
