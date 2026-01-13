package com.example.aichat.console.mcp.repo

import com.example.aichat.console.mcp.repo.git.GitService
import com.example.aichat.console.mcp.repo.fs.FileSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

/**
 * MCP Server for GitHub/Repository context
 * 
 * Exposes tools for:
 * - Git operations (current branch, remote URL)
 * - File system operations (read file, list files)
 * 
 * Runs as a stdio-based MCP server (reads JSON-RPC from stdin, writes to stdout)
 * 
 * Usage:
 * ```bash
 * java -jar repo-mcp-server.jar
 * ```
 * 
 * The server implements the MCP protocol:
 * - Handles initialize handshake
 * - Exposes tools via tools/list
 * - Executes tools via tools/call
 */
class RepoMcpServer(
    private val gitService: GitService = GitService(),
    private val fileSystemService: FileSystemService = FileSystemService()
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
            errorWriter.println("Starting Repo MCP Server...")
            
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
                put("name", "repo-mcp-server")
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
            // Git tools
            add(createToolDefinition(
                name = "git.getCurrentBranch",
                description = "Gets the current Git branch name",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                }
            ))
            
            add(createToolDefinition(
                name = "git.getRemoteUrl",
                description = "Gets the remote repository URL (origin)",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("remote", buildJsonObject {
                            put("type", "string")
                            put("description", "Remote name (default: origin)")
                            put("default", "origin")
                        })
                    })
                }
            ))
            
            // File system tools
            add(createToolDefinition(
                name = "fs.readFile",
                description = "Reads the contents of a file",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the file to read")
                        })
                    })
                    put("required", buildJsonArray {
                        add("path")
                    })
                }
            ))
            
            add(createToolDefinition(
                name = "repo.listFiles",
                description = "Lists files in a directory",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("dir", buildJsonObject {
                            put("type", "string")
                            put("description", "Directory path (default: current directory)")
                            put("default", ".")
                        })
                        put("recursive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Whether to list recursively (default: false)")
                            put("default", false)
                        })
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
                "git.getCurrentBranch" -> {
                    val branch = gitService.getCurrentBranch()
                    createToolResult(branch)
                }
                "git.getRemoteUrl" -> {
                    val remote = arguments["remote"]?.jsonPrimitive?.content ?: "origin"
                    val url = gitService.getRemoteUrl(remote)
                    createToolResult(url)
                }
                "fs.readFile" -> {
                    val path = arguments["path"]?.jsonPrimitive?.content
                        ?: return createErrorResponse(id = id, code = -32602, message = "Missing path parameter")
                    val content = fileSystemService.readFile(path)
                    createToolResult(content)
                }
                "repo.listFiles" -> {
                    val dir = arguments["dir"]?.jsonPrimitive?.content ?: "."
                    val recursive = arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
                    val files = fileSystemService.listFiles(dir, recursive)
                    createToolResult(files.joinToString("\n"))
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

