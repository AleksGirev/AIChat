package com.example.aichat.console.llm

import com.example.aichat.console.model.*
import com.example.aichat.console.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible API client
 * Supports tool calling and streaming responses
 * Also supports YandexGPT API (OpenAI-compatible)
 */
class OpenAiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4o",
    private val folderId: String? = null // Required for YandexGPT
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val mediaType = "application/json".toMediaType()
    
    /**
     * Sends a chat completion request with tool support
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
        temperature: Double = 0.7,
        maxTokens: Int? = null
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = ChatRequest(
                model = model,
                messages = messages,
                tools = tools,
                toolChoice = if (tools != null && tools.isNotEmpty()) "auto" else null,
                temperature = temperature,
                maxTokens = maxTokens
            )
            
            val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
            
            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Content-Type", "application/json")
            
            // Add Authorization header only if apiKey is not "ollama" (Ollama doesn't require auth)
            if (apiKey != "ollama" && apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            
            // Add x-folder-id header for YandexGPT
            folderId?.let {
                requestBuilder.header("x-folder-id", it)
            }
            
            val request = requestBuilder
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    Exception("API error ${response.code}: $errorBody")
                )
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
            
            Result.success(chatResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Converts MCP tools to OpenAI tool format
     */
    fun convertMcpToolsToOpenAiTools(mcpTools: List<com.example.aichat.console.model.McpTool>): List<ToolDefinition> {
        return mcpTools.map { mcpTool ->
            ToolDefinition(
                type = "function",
                function = FunctionDefinition(
                    name = mcpTool.name,
                    description = mcpTool.description ?: "Tool: ${mcpTool.name}",
                    parameters = convertInputSchemaToJsonSchema(mcpTool.inputSchema)
                )
            )
        }
    }
    
    /**
     * Converts MCP input schema to JSON Schema format for OpenAI
     */
    private fun convertInputSchemaToJsonSchema(schema: ToolInputSchema): kotlinx.serialization.json.JsonObject {
        val jsonSchema = mutableMapOf<String, Any>(
            "type" to schema.type
        )
        
        schema.properties?.let { properties ->
            val propsMap = properties.mapValues { (_, prop) ->
                val propMap = mutableMapOf<String, Any>(
                    "type" to prop.type
                )
                prop.description?.let { propMap["description"] = it }
                prop.enum?.let { propMap["enum"] = it }
                propMap
            }
            jsonSchema["properties"] = propsMap
        }
        
        schema.required?.let { required ->
            jsonSchema["required"] = required
        }
        
        return JsonUtils.mapToJsonObject(jsonSchema)
    }
}
