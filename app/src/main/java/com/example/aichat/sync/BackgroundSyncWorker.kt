package com.example.aichat.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aichat.data.Config
import com.example.aichat.data.local.LlmSyncResponse
import com.example.aichat.data.local.ParsedUpdate
import com.example.aichat.data.local.SyncUpdateRepository
import com.example.aichat.data.local.WeatherRepository
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.mcp.model.McpTool
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.sync.BackgroundSyncMcpConfig
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Background Worker for MCP data synchronization with LLM processing.
 * 
 * Execution Flow:
 * 1. Connect to MCP server (or reuse existing connection)
 * 2. Fetch list of available tools via `listTools`
 * 3. Execute relevant tools to gather data
 * 4. Construct LLM request with tool results
 * 5. Parse LLM response into structured updates
 * 6. Persist updates to Room database
 * 7. Show immediate notification for high-priority updates
 * 
 * Error Handling:
 * - Graceful degradation if MCP server unavailable
 * - Retry policy handled by WorkManager constraints
 * - All errors logged and stored in sync status
 * 
 * Android Considerations:
 * - Doze mode: WorkManager handles rescheduling automatically
 * - App Standby: NETWORK_CONNECTED constraint ensures execution
 * - Worker is lifecycle-independent, no Activity required
 */
class BackgroundSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val mcpRepository: McpRepository?,
    private val chatRepository: ChatRepository,
    private val syncUpdateRepository: SyncUpdateRepository,
    private val weatherRepository: WeatherRepository,
    private val notificationManager: SyncNotificationManager,
    private val gson: Gson
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "BackgroundSyncWorker"
        
        // Work request unique name
        const val WORK_NAME = "background_sync_work"
        
        // Input data keys
        const val KEY_IS_MANUAL_SYNC = "is_manual_sync"
        
        // LLM model to use for processing
        private val SYNC_MODEL = Config.DEFAULT_YANDEXGPT_MODEL
        
        // Maximum tool calls per sync
        private const val MAX_TOOL_CALLS = 5
        
        // System prompt for LLM to process MCP data (including weather data)
        private val SYNC_SYSTEM_PROMPT = """
            You are a data processing assistant. Your task is to analyze data from various tools 
            and extract important updates, events, metrics, and alerts.
            
            For WEATHER data specifically:
            - Extract current temperature, conditions, humidity, wind speed
            - Identify weather alerts or warnings (storms, extreme temperatures)
            - Note significant weather changes
            - Priority 4-5 for severe weather alerts (storms, extreme heat/cold)
            - Priority 3 for notable conditions (rain, snow)
            - Priority 2 for normal weather updates
            
            Output your response in JSON format with the following structure:
            {
                "updates": [
                    {
                        "type": "event|metric|update|alert|weather",
                        "priority": 1-5,
                        "title": "Short title (max 50 chars)",
                        "content": "Detailed description",
                        "sourceTool": "tool_name"
                    }
                ],
                "summary": "Brief overall summary"
            }
            
            Priority levels:
            1 - Low: Informational, no action needed
            2 - Normal: Standard update
            3 - Medium: Worth noting
            4 - High: Important, should notify user (severe weather, etc.)
            5 - Critical: Requires immediate attention (dangerous conditions)
            
            Always respond with valid JSON only, no additional text.
        """.trimIndent()
        
        // Default location for weather queries (can be configured)
        const val DEFAULT_WEATHER_LOCATION = "Moscow,RU"
        const val DEFAULT_WEATHER_LAT = 55.7558
        const val DEFAULT_WEATHER_LON = 37.6173
        
        // Belarusian cities to track weather
        val BELARUSIAN_CITIES = listOf(
            "Gomel,BY",
            "Minsk,BY",
            "Mogilev,BY",
            "Grodno,BY",
            "Brest,BY",
            "Vitebsk,BY"
        )
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val isManualSync = inputData.getBoolean(KEY_IS_MANUAL_SYNC, false)
        
        Log.d(TAG, "Starting background sync (manual: $isManualSync)")
        
        try {
            // Step 1: Check if sync is enabled
            if (!syncUpdateRepository.isSyncEnabled() && !isManualSync) {
                Log.d(TAG, "Sync disabled, skipping")
                return@withContext Result.success()
            }
            
            // Step 2: Check MCP availability
            if (mcpRepository == null) {
                val error = "MCP repository not available"
                Log.w(TAG, error)
                syncUpdateRepository.updateSyncStatus(false, error)
                
                if (isManualSync) {
                    notificationManager.showManualSyncCompleteNotification(0, error)
                }
                
                return@withContext Result.retry() // Retry later
            }
            
            // Step 3: Initialize MCP connection if needed
            val initResult = mcpRepository.initialize()
            if (initResult.isFailure) {
                val error = "Failed to initialize MCP: ${initResult.exceptionOrNull()?.message}"
                Log.e(TAG, error)
                syncUpdateRepository.updateSyncStatus(false, error)
                
                if (isManualSync) {
                    notificationManager.showManualSyncCompleteNotification(0, error)
                }
                
                return@withContext Result.retry()
            }
            
            // Step 4: List available tools
            val toolsResult = mcpRepository.listTools()
            val tools = toolsResult.getOrElse { error ->
                val errorMsg = "Failed to list tools: ${error.message}"
                Log.e(TAG, errorMsg)
                syncUpdateRepository.updateSyncStatus(false, errorMsg)
                
                if (isManualSync) {
                    notificationManager.showManualSyncCompleteNotification(0, errorMsg)
                }
                
                return@withContext Result.retry()
            }
            
            if (tools.isEmpty()) {
                Log.d(TAG, "No tools available from MCP server")
                syncUpdateRepository.updateSyncStatus(true)
                
                if (isManualSync) {
                    notificationManager.showManualSyncCompleteNotification(0)
                }
                
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Found ${tools.size} MCP tools")
            Log.d(TAG, "Tool names: ${tools.map { it.name }}")
            
            // Step 5: Fetch weather for Belarusian cities and save to SQLite
            Log.d(TAG, "=== Starting weather fetch for Belarusian cities ===")
            try {
                fetchAndSaveBelarusianWeather(tools, mcpRepository)
                Log.d(TAG, "=== Weather fetch completed ===")
            } catch (e: Exception) {
                Log.e(TAG, "=== Error during weather fetch ===", e)
                e.printStackTrace()
            }
            
            // Step 6: Execute tools and gather data
            val toolResults = executeTools(tools, mcpRepository)
            
            if (toolResults.isEmpty()) {
                Log.d(TAG, "No data gathered from tools")
                syncUpdateRepository.updateSyncStatus(true)
                
                if (isManualSync) {
                    notificationManager.showManualSyncCompleteNotification(0)
                }
                
                return@withContext Result.success()
            }
            
            // Step 7: Process data with LLM
            val llmResponse = processWithLlm(toolResults)
            
            if (llmResponse == null || llmResponse.updates.isEmpty()) {
                Log.d(TAG, "No updates extracted from LLM processing")
                syncUpdateRepository.updateSyncStatus(true)
                
                if (isManualSync) {
                    notificationManager.showManualSyncCompleteNotification(0)
                }
                
                return@withContext Result.success()
            }
            
            // Step 8: Save updates to Room
            val rawDataJson = gson.toJson(toolResults)
            syncUpdateRepository.saveUpdates(llmResponse.updates, rawDataJson)
            
            Log.d(TAG, "Saved ${llmResponse.updates.size} updates to database")
            
            // Step 9: Show immediate notifications for high-priority updates
            val highPriorityUpdates = llmResponse.updates.filter { it.priority >= 4 }
            highPriorityUpdates.take(3).forEach { update ->
                // Convert to entity for notification display
                val entity = com.example.aichat.data.local.SyncUpdateEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    type = update.type,
                    priority = update.priority,
                    title = update.title,
                    content = update.content,
                    sourceTool = update.sourceTool,
                    syncTimestamp = System.currentTimeMillis()
                )
                notificationManager.showHighPriorityNotification(entity)
            }
            
            // Step 10: Update sync status
            syncUpdateRepository.updateSyncStatus(true)
            
            // Step 11: Cleanup old updates periodically
            syncUpdateRepository.cleanupOldUpdates()
            
            // Step 12: Cleanup old weather data periodically
            weatherRepository.cleanupOldData()
            
            // Step 13: Show manual sync completion notification
            if (isManualSync) {
                notificationManager.showManualSyncCompleteNotification(llmResponse.updates.size)
            }
            
            Log.d(TAG, "Background sync completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed", e)
            val errorMessage = e.message ?: "Unknown error"
            syncUpdateRepository.updateSyncStatus(false, errorMessage)
            
            if (inputData.getBoolean(KEY_IS_MANUAL_SYNC, false)) {
                notificationManager.showManualSyncCompleteNotification(0, errorMessage)
            }
            
            // Retry for transient failures
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    /**
     * Fetches weather data for Belarusian cities and saves to SQLite.
     * Called during sync to collect temperature data for summary notifications.
     */
    private suspend fun fetchAndSaveBelarusianWeather(
        tools: List<McpTool>,
        repository: McpRepository
    ) {
        Log.d(TAG, "Fetching weather for Belarusian cities")
        Log.d(TAG, "Available tools: ${tools.map { it.name }}")
        
        // Find weather tool (typically "getCurrentWeather" or similar)
        val weatherTool = tools.firstOrNull { tool ->
            val name = tool.name.lowercase()
            val desc = tool.description?.lowercase() ?: ""
            name.contains("weather") || 
            name.contains("getcurrentweather") ||
            desc.contains("weather") ||
            desc.contains("current weather")
        }
        
        if (weatherTool == null) {
            Log.w(TAG, "No weather tool found, skipping Belarusian weather fetch")
            Log.w(TAG, "Tool names: ${tools.map { it.name }}")
            return
        }
        
        Log.d(TAG, "Found weather tool: ${weatherTool.name}")
        
        // Fetch weather for each Belarusian city
        val weatherData = mutableMapOf<String, Double>()
        
        for (cityLocation in BELARUSIAN_CITIES) {
            try {
                val cityName = cityLocation.split(",").first()
                val arguments = buildBelarusianWeatherArguments(weatherTool, cityLocation)
                
                Log.d(TAG, "Fetching weather for $cityName with arguments: $arguments")
                val result = repository.callTool(weatherTool.name, arguments)
                
                result.getOrNull()?.let { toolResult ->
                    val content = toolResult.content.joinToString("\n") { it.text ?: it.data ?: "" }
                    Log.d(TAG, "Received response for $cityName: ${content.take(200)}")
                    if (content.isNotBlank()) {
                        val temperature = parseTemperatureFromWeatherResponse(content)
                        if (temperature != null) {
                            weatherData[cityName] = temperature
                            Log.d(TAG, "Got temperature for $cityName: $temperature°C")
                        } else {
                            Log.w(TAG, "Could not parse temperature from response for $cityName. Content: ${content.take(500)}")
                        }
                    } else {
                        Log.w(TAG, "Empty content for $cityName")
                    }
                } ?: run {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Failed to get weather for $cityName: ${error?.message}", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather for $cityLocation", e)
            }
        }
        
        // Save all weather data to SQLite
        Log.d(TAG, "Collected weather data for ${weatherData.size} cities: ${weatherData.keys}")
        if (weatherData.isNotEmpty()) {
            try {
                weatherRepository.saveWeatherBatch(weatherData)
                Log.d(TAG, "Saved weather data for ${weatherData.size} cities to SQLite")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving weather data to SQLite", e)
                e.printStackTrace()
            }
        } else {
            Log.w(TAG, "No weather data collected for Belarusian cities")
        }
    }
    
    /**
     * Builds arguments for weather tool with Belarusian city location
     */
    private fun buildBelarusianWeatherArguments(tool: McpTool, cityLocation: String): Map<String, Any> {
        val args = mutableMapOf<String, Any>()
        val schema = tool.inputSchema
        val properties = schema.properties ?: return args
        
        // Check what parameters the tool expects
        if (properties.containsKey("q") || properties.containsKey("city")) {
            val key = if (properties.containsKey("q")) "q" else "city"
            args[key] = cityLocation
        }
        
        if (properties.containsKey("units")) {
            args["units"] = "metric" // Celsius
        }
        
        if (properties.containsKey("lang")) {
            args["lang"] = "en"
        }
        
        // Add appid if required
        if (properties.containsKey("appid") && (schema.required?.contains("appid") == true)) {
            args["appid"] = BackgroundSyncMcpConfig.OpenWeatherStdio.OPENWEATHER_API_KEY
        }
        
        return args
    }
    
    /**
     * Parses temperature from weather API response.
     * Handles various JSON formats from OpenWeather API.
     */
    private fun parseTemperatureFromWeatherResponse(response: String): Double? {
        Log.d(TAG, "Parsing temperature from response (first 500 chars): ${response.take(500)}")
        return try {
            // Try to parse as JSON
            val jsonContent = extractJsonFromResponse(response)
            Log.d(TAG, "Extracted JSON content (first 500 chars): ${jsonContent.take(500)}")
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(jsonContent, mapType)
            Log.d(TAG, "Parsed JSON map keys: ${map.keys}")
            
            // Try different possible JSON structures
            val temperature = when {
                // OpenWeather format: {"main": {"temp": 15.5}}
                map.containsKey("main") -> {
                    @Suppress("UNCHECKED_CAST")
                    val main = map["main"] as? Map<String, Any>
                    Log.d(TAG, "Found 'main' object: $main")
                    val temp = main?.get("temp") as? Number
                    temp?.toDouble()
                }
                // Direct temp field
                map.containsKey("temp") -> {
                    val temp = map["temp"] as? Number
                    Log.d(TAG, "Found 'temp' field: $temp")
                    temp?.toDouble()
                }
                // temperature field
                map.containsKey("temperature") -> {
                    val temp = map["temperature"] as? Number
                    Log.d(TAG, "Found 'temperature' field: $temp")
                    temp?.toDouble()
                }
                else -> {
                    // Try to find any numeric field that might be temperature
                    Log.d(TAG, "No standard temperature field found, searching for numeric values")
                    (map.values.firstOrNull { it is Number } as? Number)?.toDouble()
                }
            }
            Log.d(TAG, "Parsed temperature: $temperature")
            temperature
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse temperature from response: ${response.take(1000)}", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Executes MCP tools and gathers results.
     * Selects tools based on their description and executes them.
     * 
     * For OpenWeather MCP server, specifically handles:
     * - getCurrentWeather: Get current weather for a location
     * - getForecast: Get weather forecast
     * - Other weather-related tools
     */
    private suspend fun executeTools(
        tools: List<McpTool>,
        repository: McpRepository
    ): Map<String, String> = coroutineScope {
        val results = mutableMapOf<String, String>()
        
        Log.d(TAG, "Available tools: ${tools.map { it.name }}")
        
        // Check for weather-specific tools (OpenWeather MCP)
        val weatherTools = tools.filter { tool ->
            val name = tool.name.lowercase()
            val desc = tool.description?.lowercase() ?: ""
            name.contains("weather") || 
            name.contains("forecast") ||
            name.contains("temperature") ||
            desc.contains("weather") ||
            desc.contains("forecast")
        }
        
        if (weatherTools.isNotEmpty()) {
            Log.d(TAG, "Found ${weatherTools.size} weather tools: ${weatherTools.map { it.name }}")
            
            // Execute weather tools with location arguments
            for (tool in weatherTools.take(MAX_TOOL_CALLS)) {
                try {
                    val arguments = buildWeatherToolArguments(tool)
                    Log.d(TAG, "Calling weather tool: ${tool.name} with args: $arguments")
                    
                    val result = repository.callTool(tool.name, arguments)
                    result.getOrNull()?.let { toolResult ->
                        val content = toolResult.content.joinToString("\n") { it.text ?: it.data ?: "" }
                        if (content.isNotBlank()) {
                            results[tool.name] = content
                            Log.d(TAG, "Tool ${tool.name} returned ${content.length} chars")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling weather tool ${tool.name}", e)
                }
            }
            
            if (results.isNotEmpty()) {
                return@coroutineScope results
            }
        }
        
        // Fallback: Filter tools that are suitable for background sync
        val syncTools = tools
            .filter { tool ->
                val desc = tool.description?.lowercase() ?: ""
                val name = tool.name.lowercase()
                // Look for tools that provide data, not ones that modify state
                name.contains("get") || 
                name.contains("list") || 
                name.contains("fetch") ||
                desc.contains("get") || 
                desc.contains("list") || 
                desc.contains("fetch") ||
                desc.contains("read") ||
                desc.contains("status") ||
                desc.contains("data") ||
                desc.contains("info")
            }
            .take(MAX_TOOL_CALLS)
        
        if (syncTools.isEmpty()) {
            Log.d(TAG, "No suitable tools for sync, trying first available tool")
            val firstTool = tools.firstOrNull()
            if (firstTool != null) {
                try {
                    Log.d(TAG, "Calling first available tool: ${firstTool.name}")
                    val result = repository.callTool(firstTool.name, buildDefaultArguments(firstTool))
                    result.getOrNull()?.let { toolResult ->
                        val content = toolResult.content.joinToString("\n") { it.text ?: it.data ?: "" }
                        if (content.isNotBlank()) {
                            results[firstTool.name] = content
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling tool ${firstTool.name}", e)
                }
            }
            return@coroutineScope results
        }
        
        // Execute tools in parallel
        val deferredResults = syncTools.map { tool ->
            async {
                try {
                    Log.d(TAG, "Calling tool: ${tool.name}")
                    
                    val arguments = buildDefaultArguments(tool)
                    val result = repository.callTool(tool.name, arguments)
                    result.getOrNull()?.let { toolResult ->
                        val content = toolResult.content.joinToString("\n") { it.text ?: it.data ?: "" }
                        if (content.isNotBlank()) {
                            tool.name to content
                        } else null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling tool ${tool.name}", e)
                    null
                }
            }
        }
        
        deferredResults.awaitAll().filterNotNull().forEach { (name, content) ->
            results[name] = content
        }
        
        Log.d(TAG, "Gathered data from ${results.size} tools")
        results
    }
    
    /**
     * Builds arguments for weather-specific tools
     */
    private fun buildWeatherToolArguments(tool: McpTool): Map<String, Any> {
        val args = mutableMapOf<String, Any>()
        val schema = tool.inputSchema
        val properties = schema.properties ?: return args
        val required = schema.required ?: emptyList()
        
        // Check what parameters the tool expects
        if (properties.containsKey("q") || properties.containsKey("city")) {
            // City name parameter
            val key = if (properties.containsKey("q")) "q" else "city"
            args[key] = DEFAULT_WEATHER_LOCATION
        }
        
        if (properties.containsKey("lat") && properties.containsKey("lon")) {
            // Coordinates
            args["lat"] = DEFAULT_WEATHER_LAT
            args["lon"] = DEFAULT_WEATHER_LON
        }
        
        if (properties.containsKey("units")) {
            args["units"] = "metric" // Celsius
        }
        
        if (properties.containsKey("lang")) {
            args["lang"] = "en"
        }
        
        // Add appid if required (should be injected by MCP server via env, but just in case)
        if (properties.containsKey("appid") && "appid" in required) {
            args["appid"] = BackgroundSyncMcpConfig.OpenWeatherStdio.OPENWEATHER_API_KEY
        }
        
        Log.d(TAG, "Built weather tool arguments: $args")
        return args
    }
    
    /**
     * Builds default arguments for generic tools
     */
    private fun buildDefaultArguments(tool: McpTool): Map<String, Any>? {
        val schema = tool.inputSchema
        val properties = schema.properties
        
        // If no properties required, return null
        if (properties.isNullOrEmpty()) {
            return null
        }
        
        // Try to build arguments based on schema
        val args = mutableMapOf<String, Any>()
        val required = schema.required ?: emptyList()
        
        // Only fill required parameters with defaults if we can guess them
        for (propName in required) {
            val prop = properties[propName] ?: continue
            when {
                propName.contains("location", ignoreCase = true) ||
                propName.contains("city", ignoreCase = true) ||
                propName == "q" -> {
                    args[propName] = DEFAULT_WEATHER_LOCATION
                }
                propName.contains("lat", ignoreCase = true) -> {
                    args[propName] = DEFAULT_WEATHER_LAT
                }
                propName.contains("lon", ignoreCase = true) -> {
                    args[propName] = DEFAULT_WEATHER_LON
                }
            }
        }
        
        return if (args.isEmpty()) null else args
    }
    
    /**
     * Processes tool results with LLM to extract structured updates.
     * Uses the configured model (OpenRouter) with a specific system prompt.
     */
    private suspend fun processWithLlm(toolResults: Map<String, String>): LlmSyncResponse? {
        if (toolResults.isEmpty()) return null
        
        try {
            // Build user message with tool results
            val userMessage = buildString {
                append("Please analyze the following data from various tools and extract important updates:\n\n")
                
                toolResults.forEach { (toolName, content) ->
                    append("=== Tool: $toolName ===\n")
                    append(content.take(2000)) // Limit content length
                    append("\n\n")
                }
                
                append("Extract any events, metrics, updates, or alerts from this data.")
            }
            
            val messages = listOf(
                ChatMessage(role = "system", content = SYNC_SYSTEM_PROMPT),
                ChatMessage(role = "user", content = userMessage)
            )
            
            Log.d(TAG, "Sending data to LLM for processing")
            
            val result = chatRepository.sendChatRequest(
                messages = messages,
                model = SYNC_MODEL,
                temperature = 0.3, // Low temperature for consistent structured output
                enableTools = false // Don't use MCP tools for processing
            )
            
            val response = result.getOrElse { error ->
                Log.e(TAG, "LLM request failed: ${error.message}")
                return null
            }
            
            val content = response.choices.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                Log.w(TAG, "Empty LLM response")
                return null
            }
            
            Log.d(TAG, "LLM response received, parsing JSON")
            
            // Parse JSON response
            return parseLlmResponse(content)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing with LLM", e)
            return null
        }
    }
    
    /**
     * Parses LLM response JSON into structured LlmSyncResponse.
     * Handles various JSON formats and edge cases.
     */
    private fun parseLlmResponse(content: String): LlmSyncResponse? {
        try {
            // Extract JSON from response (may contain markdown code blocks)
            val jsonContent = extractJsonFromResponse(content)
            
            // Try to parse as LlmSyncResponse
            return try {
                gson.fromJson(jsonContent, LlmSyncResponse::class.java)
            } catch (e: JsonSyntaxException) {
                // Try parsing updates array directly
                try {
                    val mapType = object : TypeToken<Map<String, Any>>() {}.type
                    val map: Map<String, Any> = gson.fromJson(jsonContent, mapType)
                    
                    @Suppress("UNCHECKED_CAST")
                    val updatesRaw = map["updates"] as? List<Map<String, Any>> ?: return null
                    
                    val updates = updatesRaw.mapNotNull { updateMap ->
                        try {
                            ParsedUpdate(
                                type = updateMap["type"] as? String ?: "update",
                                priority = (updateMap["priority"] as? Number)?.toInt() ?: 2,
                                title = (updateMap["title"] as? String)?.take(50) ?: "Update",
                                content = updateMap["content"] as? String ?: "",
                                sourceTool = updateMap["sourceTool"] as? String
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse update: $updateMap")
                            null
                        }
                    }
                    
                    LlmSyncResponse(
                        updates = updates,
                        summary = map["summary"] as? String
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse LLM response: $jsonContent", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing LLM response", e)
            return null
        }
    }
    
    /**
     * Extracts JSON from LLM response that may contain markdown code blocks.
     */
    private fun extractJsonFromResponse(content: String): String {
        // Check for markdown JSON code block
        val jsonBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        val match = jsonBlockRegex.find(content)
        
        if (match != null) {
            return match.groupValues[1].trim()
        }
        
        // Check if content starts with { or [
        val trimmed = content.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        
        // Try to find JSON object in the content
        val jsonStart = content.indexOf('{')
        val jsonEnd = content.lastIndexOf('}')
        
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd + 1)
        }
        
        return content.trim()
    }
}

