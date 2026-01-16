package com.example.aichat.console.mcp.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * MCP Server for Team Tasks Management
 * 
 * Exposes tools for:
 * - Creating tasks
 * - Getting tasks with filters
 * - Getting specific task details
 * - Updating tasks
 * - Getting tasks by priority
 * - Getting project status summary
 * 
 * Runs as a stdio-based MCP server (reads JSON-RPC from stdin, writes to stdout)
 * 
 * Usage:
 * ```bash
 * java -jar tasks-mcp-server.jar
 * ```
 * 
 * The server implements the MCP protocol:
 * - Handles initialize handshake
 * - Exposes tools via tools/list
 * - Executes tools via tools/call
 */
class TasksMcpServer(
    private val taskService: TaskService = TaskService()
) {
    private var initialized = false
    private val reader = BufferedReader(InputStreamReader(System.`in`))
    private val writer = PrintWriter(System.out, true)
    private val errorWriter = PrintWriter(System.err, true)
    
    /**
     * Runs the MCP server main loop
     * Reads JSON-RPC requests from stdin and writes responses to stdout
     */
    suspend fun run() = withContext(Dispatchers.IO) {
        try {
            errorWriter.println("Starting Tasks MCP Server...")
            errorWriter.println("Server is ready. Waiting for JSON-RPC requests on stdin...")
            errorWriter.println("Press Ctrl+C to stop the server.")
            
            while (true) {
                val line = reader.readLine() ?: break
                
                if (line.isBlank()) continue
                
                try {
                    val request = Json.parseToJsonElement(line).jsonObject
                    val response = handleRequest(request)
                    
                    if (response != null) {
                        writer.println(Json.encodeToString(JsonObject.serializer(), response))
                    }
                } catch (e: Exception) {
                    val errorResponse = createErrorResponse(
                        id = null,
                        code = -32700,
                        message = "Parse error: ${e.message}"
                    )
                    writer.println(Json.encodeToString(JsonObject.serializer(), errorResponse))
                }
            }
        } catch (e: Exception) {
            errorWriter.println("Server error: ${e.message}")
        }
    }
    
    /**
     * Handles a JSON-RPC request
     */
    private suspend fun handleRequest(request: JsonObject): JsonObject? {
        val method = request["method"]?.jsonPrimitive?.content
        val id = request["id"]?.jsonPrimitive?.content
        
        return when (method) {
            "initialize" -> handleInitialize(request, id)
            "notifications/initialized" -> null // Notification, no response
            "tools/list" -> handleToolsList(id)
            "tools/call" -> handleToolCall(request, id)
            else -> createErrorResponse(
                id = id,
                code = -32601,
                message = "Method not found: $method"
            )
        }
    }
    
    /**
     * Handles initialize request
     */
    private fun handleInitialize(request: JsonObject, id: String?): JsonObject {
        initialized = true
        
        val result = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject { })
            })
            put("serverInfo", buildJsonObject {
                put("name", "tasks-mcp-server")
                put("version", "1.0.0")
            })
        }
        
        return createSuccessResponse(id, result)
    }
    
    /**
     * Handles tools/list request
     */
    private fun handleToolsList(id: String?): JsonObject {
        val tools = buildJsonArray {
            // Create task
            add(createToolDefinition(
                name = "tasks.createTask",
                description = "Creates a new task with title, description, priority, assignee, and other optional fields",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("title", buildJsonObject {
                            put("type", "string")
                            put("description", "Task title")
                        })
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "Task description")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("low"); add("medium"); add("high"); add("critical") })
                            put("description", "Task priority (low, medium, high, critical)")
                        })
                        put("assignee", buildJsonObject {
                            put("type", "string")
                            put("description", "User ID of the assignee (optional)")
                        })
                        put("tags", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                            put("description", "List of tags for the task (optional)")
                        })
                        put("dueDate", buildJsonObject {
                            put("type", "number")
                            put("description", "Due date as Unix timestamp in milliseconds (optional)")
                        })
                        put("estimatedHours", buildJsonObject {
                            put("type", "integer")
                            put("description", "Estimated hours to complete (optional)")
                        })
                        put("blockedBy", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                            put("description", "List of task IDs that block this task (optional)")
                        })
                    })
                    put("required", buildJsonArray {
                        add("title")
                        add("description")
                    })
                }
            ))
            
            // Get tasks
            add(createToolDefinition(
                name = "tasks.getTasks",
                description = "Gets list of tasks with optional filters (status, priority, assignee, tags)",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by status (todo, in-progress, review, done, blocked, cancelled)")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("low"); add("medium"); add("high"); add("critical") })
                            put("description", "Filter by priority")
                        })
                        put("assignee", buildJsonObject {
                            put("type", "string")
                            put("description", "Filter by assignee user ID")
                        })
                        put("tags", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                            put("description", "Filter by tags (tasks must have at least one of these tags)")
                        })
                    })
                }
            ))
            
            // Get task
            add(createToolDefinition(
                name = "tasks.getTask",
                description = "Gets a specific task by task ID",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("taskId", buildJsonObject {
                            put("type", "string")
                            put("description", "Task ID to retrieve")
                        })
                    })
                    put("required", buildJsonArray {
                        add("taskId")
                    })
                }
            ))
            
            // Update task
            add(createToolDefinition(
                name = "tasks.updateTask",
                description = "Updates a task's fields (status, priority, assignee, etc.)",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("taskId", buildJsonObject {
                            put("type", "string")
                            put("description", "Task ID to update")
                        })
                        put("title", buildJsonObject {
                            put("type", "string")
                            put("description", "New title (optional)")
                        })
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "New description (optional)")
                        })
                        put("status", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("todo"); add("in-progress"); add("review"); add("done"); add("blocked"); add("cancelled") })
                            put("description", "New status (optional)")
                        })
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("low"); add("medium"); add("high"); add("critical") })
                            put("description", "New priority (optional)")
                        })
                        put("assignee", buildJsonObject {
                            put("type", "string")
                            put("description", "New assignee user ID (optional)")
                        })
                        put("tags", buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                            put("description", "New tags list (optional)")
                        })
                        put("dueDate", buildJsonObject {
                            put("type", "number")
                            put("description", "New due date as Unix timestamp (optional)")
                        })
                        put("estimatedHours", buildJsonObject {
                            put("type", "integer")
                            put("description", "New estimated hours (optional)")
                        })
                        put("actualHours", buildJsonObject {
                            put("type", "integer")
                            put("description", "New actual hours (optional)")
                        })
                    })
                    put("required", buildJsonArray {
                        add("taskId")
                    })
                }
            ))
            
            // Get tasks by priority
            add(createToolDefinition(
                name = "tasks.getTasksByPriority",
                description = "Gets all tasks filtered by priority level (low, medium, high, critical)",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("priority", buildJsonObject {
                            put("type", "string")
                            put("enum", buildJsonArray { add("low"); add("medium"); add("high"); add("critical") })
                            put("description", "Priority level to filter by")
                        })
                    })
                    put("required", buildJsonArray {
                        add("priority")
                    })
                }
            ))
            
            // Get project status
            add(createToolDefinition(
                name = "tasks.getProjectStatus",
                description = "Gets overall project status summary with statistics (total tasks, by status, by priority, blocked tasks, etc.)",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                }
            ))
        }
        
        val result = buildJsonObject {
            put("tools", tools)
        }
        
        return createSuccessResponse(id, result)
    }
    
    /**
     * Handles tools/call request
     */
    private suspend fun handleToolCall(request: JsonObject, id: String?): JsonObject {
        val params = request["params"]?.jsonObject ?: return createErrorResponse(
            id = id,
            code = -32602,
            message = "Invalid params"
        )
        
        val toolName = params["name"]?.jsonPrimitive?.content
            ?: return createErrorResponse(id = id, code = -32602, message = "Missing tool name")
        
        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject { }
        
        val result = try {
            when (toolName) {
                "tasks.createTask" -> {
                    val title = arguments["title"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing title parameter")
                    val description = arguments["description"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing description parameter")
                    val priority = arguments["priority"]?.jsonPrimitive?.content ?: "medium"
                    val assignee = arguments["assignee"]?.jsonPrimitive?.content
                    val tags = arguments["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    val dueDate = arguments["dueDate"]?.jsonPrimitive?.longOrNull
                    val estimatedHours = arguments["estimatedHours"]?.jsonPrimitive?.intOrNull ?: 0
                    val blockedBy = arguments["blockedBy"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                    
                    val task = taskService.createTask(
                        title = title,
                        description = description,
                        priority = priority,
                        assignee = assignee,
                        tags = tags,
                        dueDate = dueDate,
                        estimatedHours = estimatedHours,
                        blockedBy = blockedBy
                    )
                    val taskJson = Json.encodeToString(Task.serializer(), task)
                    createToolResult(taskJson)
                }
                "tasks.getTasks" -> {
                    val status = arguments["status"]?.jsonPrimitive?.content
                    val priority = arguments["priority"]?.jsonPrimitive?.content
                    val assignee = arguments["assignee"]?.jsonPrimitive?.content
                    val tags = arguments["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
                    
                    val tasksJson = taskService.getTasksJson(status, priority, assignee, tags)
                    createToolResult(tasksJson)
                }
                "tasks.getTask" -> {
                    val taskId = arguments["taskId"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing taskId parameter")
                    val taskJson = taskService.getTaskJson(taskId)
                    createToolResult(taskJson)
                }
                "tasks.updateTask" -> {
                    val taskId = arguments["taskId"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing taskId parameter")
                    val title = arguments["title"]?.jsonPrimitive?.content
                    val description = arguments["description"]?.jsonPrimitive?.content
                    val status = arguments["status"]?.jsonPrimitive?.content
                    val priority = arguments["priority"]?.jsonPrimitive?.content
                    val assignee = arguments["assignee"]?.jsonPrimitive?.content
                    val tags = arguments["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
                    val dueDate = arguments["dueDate"]?.jsonPrimitive?.longOrNull
                    val estimatedHours = arguments["estimatedHours"]?.jsonPrimitive?.intOrNull
                    val actualHours = arguments["actualHours"]?.jsonPrimitive?.intOrNull
                    
                    val updatedTask = taskService.updateTask(
                        taskId = taskId,
                        title = title,
                        description = description,
                        status = status,
                        priority = priority,
                        assignee = assignee,
                        tags = tags,
                        dueDate = dueDate,
                        estimatedHours = estimatedHours,
                        actualHours = actualHours
                    )
                    
                    if (updatedTask == null) {
                        return createErrorResponse(id = id, code = -32000, message = "Task not found: $taskId")
                    }
                    
                    val taskJson = Json.encodeToString(Task.serializer(), updatedTask)
                    createToolResult(taskJson)
                }
                "tasks.getTasksByPriority" -> {
                    val priority = arguments["priority"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing priority parameter")
                    val tasksJson = taskService.getTasksByPriorityJson(priority)
                    createToolResult(tasksJson)
                }
                "tasks.getProjectStatus" -> {
                    val statusJson = taskService.getProjectStatusJson()
                    createToolResult(statusJson)
                }
                else -> {
                    return createErrorResponse(
                        id = id,
                        code = -32601,
                        message = "Unknown tool: $toolName"
                    )
                }
            }
        } catch (e: Exception) {
            return createErrorResponse(
                id = id,
                code = -32000,
                message = "Tool execution error: ${e.message}"
            )
        }
        
        return createSuccessResponse(id, result)
    }
    
    /**
     * Creates a tool definition JSON object
     */
    private fun createToolDefinition(
        name: String,
        description: String,
        parameters: JsonObject
    ): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("inputSchema", parameters)
        }
    }
    
    /**
     * Creates a tool result JSON object
     */
    private fun createToolResult(content: String): JsonObject {
        return buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", content)
                })
            })
            put("isError", false)
        }
    }
    
    /**
     * Creates a success response
     */
    private fun createSuccessResponse(id: String?, result: JsonObject): JsonObject {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) {
                put("id", id)
            }
            put("result", result)
        }
    }
    
    /**
     * Creates an error response
     */
    private fun createErrorResponse(id: String?, code: Int, message: String): JsonObject {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) {
                put("id", id)
            }
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }
    }
}
