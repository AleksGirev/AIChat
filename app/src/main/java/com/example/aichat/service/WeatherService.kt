package com.example.aichat.service

import android.util.Log
import com.example.aichat.data.Config
import com.example.aichat.data.local.WeatherRepository
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service for managing periodic weather fetching and summary generation.
 * 
 * Responsibilities:
 * 1. Fetch weather for Gomel every 10 seconds using MCP server and OpenAI API
 * 2. Save weather data to DAO
 * 3. Generate summaries every minute or on demand
 * 4. Provide summary text via StateFlow for UI consumption
 */
class WeatherService(
    private val weatherRepository: WeatherRepository,
    private val mcpRepository: McpRepository?,
    private val chatRepository: ChatRepository
) {
    
    companion object {
        private const val TAG = "WeatherService"
        private const val CITY = "Gomel"
        private const val WEATHER_FETCH_INTERVAL_MS = 10_000L // 10 seconds
        private const val SUMMARY_INTERVAL_MS = 60_000L // 1 minute
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var weatherFetchJob: Job? = null
    private var summaryJob: Job? = null
    
    // StateFlow for summary text (UI can observe this)
    private val _summaryText = MutableStateFlow<String?>(null)
    val summaryText: StateFlow<String?> = _summaryText.asStateFlow()
    
    // StateFlow for summary request (to trigger snackbar)
    private val _shouldShowSummary = MutableStateFlow(false)
    val shouldShowSummary: StateFlow<Boolean> = _shouldShowSummary.asStateFlow()
    
    /**
     * Starts the weather service:
     * - Fetches weather every 10 seconds
     * - Generates summaries every minute
     */
    fun start() {
        Log.d(TAG, "Starting WeatherService")
        stop() // Stop any existing jobs
        
        // Start periodic weather fetching
        weatherFetchJob = serviceScope.launch {
            while (isActive) {
                try {
                    fetchWeatherForGomel()
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching weather", e)
                }
                delay(WEATHER_FETCH_INTERVAL_MS)
            }
        }
        
        // Start periodic summary generation
        summaryJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(SUMMARY_INTERVAL_MS)
                    generateSummary()
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating summary", e)
                }
            }
        }
        
        Log.d(TAG, "WeatherService started")
    }
    
    /**
     * Stops the weather service
     */
    fun stop() {
        Log.d(TAG, "Stopping WeatherService")
        weatherFetchJob?.cancel()
        summaryJob?.cancel()
        weatherFetchJob = null
        summaryJob = null
    }
    
    /**
     * Manually triggers summary generation (called from settings button)
     */
    suspend fun triggerSummary() {
        Log.d(TAG, "Manually triggering summary")
        generateSummary()
    }
    
    /**
     * Fetches weather for Gomel using MCP server and OpenAI API
     */
    private suspend fun fetchWeatherForGomel() {
        Log.d(TAG, "Fetching weather for $CITY")
        
        try {
            // Step 1: Get weather from MCP server
            val weatherData = fetchWeatherFromMcp()
            
            if (weatherData != null) {
                // Step 2: Save to DAO
                weatherRepository.saveWeather(
                    city = CITY,
                    temperature = weatherData.temperature,
                    additionalData = weatherData.additionalData
                )
                Log.d(TAG, "Saved weather for $CITY: ${weatherData.temperature}°C")
            } else {
                Log.w(TAG, "Failed to fetch weather from MCP server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchWeatherForGomel", e)
        }
    }
    
    /**
     * Fetches weather data from MCP server
     */
    private suspend fun fetchWeatherFromMcp(): WeatherData? {
        if (mcpRepository == null || !mcpRepository.isConnected()) {
            Log.w(TAG, "MCP repository not available or not connected")
            return null
        }
        
        try {
            // List available tools
            val toolsResult = mcpRepository.listTools()
            val tools = toolsResult.getOrNull() ?: return null
            
            // Find weather tool
            val weatherTool = tools.firstOrNull { tool ->
                val name = tool.name.lowercase()
                val desc = tool.description?.lowercase() ?: ""
                name.contains("weather") || 
                name.contains("getcurrentweather") ||
                desc.contains("weather") ||
                desc.contains("current weather")
            }
            
            if (weatherTool == null) {
                Log.w(TAG, "No weather tool found")
                return null
            }
            
            // Build arguments for weather tool
            val arguments = buildWeatherArguments(weatherTool)
            
            // Call the tool
            val result = mcpRepository.callTool(weatherTool.name, arguments)
            
            return result.getOrNull()?.let { toolResult ->
                parseWeatherFromMcpResult(toolResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather from MCP", e)
            return null
        }
    }
    
    /**
     * Builds arguments for weather tool based on its schema
     */
    private fun buildWeatherArguments(tool: com.example.aichat.data.mcp.model.McpTool): Map<String, Any> {
        val args = mutableMapOf<String, Any>()
        val schema = tool.inputSchema
        val properties = schema.properties ?: return args
        
        // City name parameter
        if (properties.containsKey("q")) {
            args["q"] = "$CITY,by" // Gomel, Belarus
        } else if (properties.containsKey("city")) {
            args["city"] = CITY
        }
        
        // Coordinates (Gomel, Belarus approximate coordinates)
        if (properties.containsKey("lat") && properties.containsKey("lon")) {
            args["lat"] = 52.4242
            args["lon"] = 30.9753
        }
        
        // Units
        if (properties.containsKey("units")) {
            args["units"] = "metric"
        }
        
        // Language
        if (properties.containsKey("lang")) {
            args["lang"] = "en"
        }
        
        return args
    }
    
    /**
     * Parses weather data from MCP tool result
     */
    private fun parseWeatherFromMcpResult(toolResult: com.example.aichat.data.mcp.model.McpToolResult): WeatherData? {
        try {
            val content = toolResult.content
            if (content.isEmpty()) {
                return null
            }
            
            // Try to extract temperature from JSON response
            // MCP tool result content is typically JSON
            val jsonString = content.firstOrNull()?.text ?: return null
            
            // Simple JSON parsing - look for "temp" or "temperature" field
            val tempPattern = """"temp(?:erature)?":\s*([0-9.-]+)""".toRegex()
            val match = tempPattern.find(jsonString)
            val temperature = match?.groupValues?.get(1)?.toDoubleOrNull()
            
            if (temperature != null) {
                return WeatherData(
                    temperature = temperature,
                    additionalData = jsonString
                )
            }
            
            // Fallback: try to extract from text
            val textPattern = """([0-9.-]+)\s*°?[Cc]""".toRegex()
            val textMatch = textPattern.find(jsonString)
            val textTemp = textMatch?.groupValues?.get(1)?.toDoubleOrNull()
            
            if (textTemp != null) {
                return WeatherData(
                    temperature = textTemp,
                    additionalData = jsonString
                )
            }
            
            Log.w(TAG, "Could not parse temperature from: ${jsonString.take(200)}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing weather data", e)
            return null
        }
    }
    
    /**
     * Generates a summary of all weather data for Gomel using LLM
     */
    private suspend fun generateSummary() {
        Log.d(TAG, "Generating weather summary for $CITY")
        
        try {
            // Step 1: Get all weather data for Gomel from DAO
            val allWeatherData = weatherRepository.getAllByCity(CITY)
            
            if (allWeatherData.isEmpty()) {
                Log.d(TAG, "No weather data available for summary")
                _summaryText.value = "No weather data available yet."
                _shouldShowSummary.value = true
                return
            }
            
            // Step 2: Build prompt with all weather data
            val prompt = buildSummaryPrompt(allWeatherData)
            
            // Step 3: Send to LLM via YandexGPT API
            val messages = listOf(
                ChatMessage(role = "user", content = prompt)
            )
            
            val result = chatRepository.sendChatRequest(
                messages = messages,
                model = Config.DEFAULT_YANDEXGPT_MODEL, // Using YandexGPT by default
                maxTokens = 500,
                temperature = 0.7,
                enableTools = false // Don't use tools for summary
            )
            
            result.onSuccess { response ->
                val summary = response.choices.firstOrNull()?.message?.content
                if (summary != null) {
                    Log.d(TAG, "Summary generated: $summary")
                    _summaryText.value = summary
                    _shouldShowSummary.value = true
                } else {
                    Log.w(TAG, "No summary content in response")
                    _summaryText.value = "Failed to generate summary."
                    _shouldShowSummary.value = true
                }
            }.onFailure { error ->
                Log.e(TAG, "Error generating summary", error)
                _summaryText.value = "Error: ${error.message}"
                _shouldShowSummary.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateSummary", e)
            _summaryText.value = "Error generating summary: ${e.message}"
            _shouldShowSummary.value = true
        }
    }
    
    /**
     * Builds the prompt for LLM to summarize weather data
     */
    private fun buildSummaryPrompt(weatherData: List<com.example.aichat.data.local.WeatherEntity>): String {
        val sb = StringBuilder()
        sb.append("Please summarize the weather data for Gomel, Belarus. ")
        sb.append("Here is all the collected weather data:\n\n")
        
        weatherData.forEach { data ->
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(data.timestamp))
            sb.append("Date: $date, Temperature: ${data.temperature}°C")
            if (data.additionalData != null) {
                sb.append(", Additional: ${data.additionalData.take(100)}")
            }
            sb.append("\n")
        }
        
        sb.append("\nPlease provide a concise summary of the weather patterns, ")
        sb.append("temperature trends, and any notable observations. ")
        sb.append("Keep it brief and informative (2-3 sentences).")
        
        return sb.toString()
    }
    
    /**
     * Clears the summary flag (call after showing snackbar)
     */
    fun clearSummaryFlag() {
        _shouldShowSummary.value = false
    }
    
    /**
     * Data class for weather information
     */
    private data class WeatherData(
        val temperature: Double,
        val additionalData: String?
    )
}

