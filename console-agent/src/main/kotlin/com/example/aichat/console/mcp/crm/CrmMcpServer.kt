package com.example.aichat.console.mcp.crm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * MCP Server for CRM (Customer Relationship Management)
 * 
 * Exposes tools for:
 * - Getting user tickets
 * - Getting ticket details
 * - Getting user information
 * - Searching tickets
 * 
 * Runs as a stdio-based MCP server (reads JSON-RPC from stdin, writes to stdout)
 * 
 * Usage:
 * ```bash
 * java -jar crm-mcp-server.jar
 * ```
 * 
 * The server implements the MCP protocol:
 * - Handles initialize handshake
 * - Exposes tools via tools/list
 * - Executes tools via tools/call
 */
class CrmMcpServer(
    private val ticketService: TicketService = TicketService(),
    private val userService: UserService = UserService()
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
            errorWriter.println("Starting CRM MCP Server...")
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
                put("name", "crm-mcp-server")
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
            // CRM tools
            add(createToolDefinition(
                name = "crm.getUserTickets",
                description = "Gets all tickets for a specific user",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("userId", buildJsonObject {
                            put("type", "string")
                            put("description", "User ID to get tickets for")
                        })
                    })
                    put("required", buildJsonArray {
                        add("userId")
                    })
                }
            ))
            
            add(createToolDefinition(
                name = "crm.getTicket",
                description = "Gets a specific ticket by ID",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("ticketId", buildJsonObject {
                            put("type", "string")
                            put("description", "Ticket ID to retrieve")
                        })
                    })
                    put("required", buildJsonArray {
                        add("ticketId")
                    })
                }
            ))
            
            add(createToolDefinition(
                name = "crm.getUserInfo",
                description = "Gets user information by user ID",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("userId", buildJsonObject {
                            put("type", "string")
                            put("description", "User ID to get information for")
                        })
                    })
                    put("required", buildJsonArray {
                        add("userId")
                    })
                }
            ))
            
            add(createToolDefinition(
                name = "crm.searchTickets",
                description = "Searches tickets by query string, optionally filtered by user ID",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Search query string")
                        })
                        put("userId", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional user ID to filter results")
                        })
                    })
                    put("required", buildJsonArray {
                        add("query")
                    })
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
                "crm.getUserTickets" -> {
                    val userId = arguments["userId"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing userId parameter")
                    val ticketsJson = ticketService.getUserTicketsJson(userId)
                    createToolResult(ticketsJson)
                }
                "crm.getTicket" -> {
                    val ticketId = arguments["ticketId"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing ticketId parameter")
                    val ticketJson = ticketService.getTicketJson(ticketId)
                    createToolResult(ticketJson)
                }
                "crm.getUserInfo" -> {
                    val userId = arguments["userId"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing userId parameter")
                    val userJson = userService.getUserJson(userId)
                    createToolResult(userJson)
                }
                "crm.searchTickets" -> {
                    val query = arguments["query"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing query parameter")
                    val userId = arguments["userId"]?.jsonPrimitive?.content
                    val ticketsJson = ticketService.searchTicketsJson(query, userId)
                    createToolResult(ticketsJson)
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
