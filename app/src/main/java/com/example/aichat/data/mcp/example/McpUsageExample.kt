package com.example.aichat.data.mcp.example

import android.content.Context
import android.util.Log
import com.example.aichat.data.mcp.McpClient
import com.example.aichat.data.mcp.McpConfig
import com.example.aichat.data.mcp.McpFactory
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Пример использования MCP клиента
 */
object McpUsageExample {
    
    private const val TAG = "McpUsageExample"
    
    /**
     * Пример 1: Настройка и инициализация MCP с HTTP транспортом
     */
    fun example1_HttpTransport(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            // 1. Настройка конфигурации
            val config = McpConfig(context)
            config.setServerConfig(
                McpConfig.ServerConfig(
                    type = "http",
                    url = "https://mcp.context7.com/mcp",
                    apiKey = "your-api-key" // опционально
                )
            )
            config.setEnabled(true)
            
            // 2. Создание репозитория
            val networkDeps = NetworkModule.createNetworkDependencies()
            val mcpRepository = McpFactory.createMcpRepository(
                context = context,
                httpClient = networkDeps.okHttpClient,
                gson = networkDeps.gson
            )
            
            if (mcpRepository == null) {
                Log.e(TAG, "MCP repository not created (disabled?)")
                return@launch
            }
            
            // 3. Инициализация соединения
            mcpRepository.initialize().onSuccess {
                Log.d(TAG, "MCP initialized successfully")
                
                // 4. Получение списка инструментов
                mcpRepository.listTools().onSuccess { tools ->
                    Log.d(TAG, "Found ${tools.size} tools:")
                    tools.forEach { tool ->
                        Log.d(TAG, "  - ${tool.name}: ${tool.description}")
                    }
                    
                    // 5. Вызов инструмента
                    if (tools.isNotEmpty()) {
                        val firstTool = tools.first()
                        mcpRepository.callTool(
                            toolName = firstTool.name,
                            arguments = mapOf("query" to "example")
                        ).onSuccess { result ->
                            Log.d(TAG, "Tool result: ${result.content.joinToString { it.text ?: "" }}")
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to call tool", error)
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to list tools", error)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to initialize MCP", error)
            }
        }
    }
    
    /**
     * Пример 2: Использование stdio транспорта для локального сервера
     */
    fun example2_StdioTransport(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            val config = McpConfig(context)
            config.setServerConfig(
                McpConfig.ServerConfig(
                    type = "stdio",
                    stdioCommand = listOf(
                        "node",
                        "/data/local/tmp/mcp-server.js"
                    )
                )
            )
            config.setEnabled(true)
            
            val networkDeps = NetworkModule.createNetworkDependencies()
            val mcpRepository = McpFactory.createMcpRepository(
                context = context,
                httpClient = networkDeps.okHttpClient,
                gson = networkDeps.gson
            )
            
            mcpRepository?.initialize()?.onSuccess {
                Log.d(TAG, "Stdio MCP initialized")
            }
        }
    }
    
    /**
     * Пример 3: Использование WebSocket транспорта
     */
    fun example3_WebSocketTransport(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            val config = McpConfig(context)
            config.setServerConfig(
                McpConfig.ServerConfig(
                    type = "websocket",
                    url = "wss://mcp.example.com/mcp",
                    apiKey = "your-api-key"
                )
            )
            config.setEnabled(true)
            
            val networkDeps = NetworkModule.createNetworkDependencies()
            val mcpRepository = McpFactory.createMcpRepository(
                context = context,
                httpClient = networkDeps.okHttpClient,
                gson = networkDeps.gson
            )
            
            mcpRepository?.initialize()?.onSuccess {
                Log.d(TAG, "WebSocket MCP initialized")
            }
        }
    }
    
    /**
     * Пример 4: Ручное создание транспорта и клиента
     */
    fun example4_ManualSetup(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            val networkDeps = NetworkModule.createNetworkDependencies()
            val config = McpConfig.ServerConfig(
                type = "http",
                url = "https://mcp.example.com/mcp",
                apiKey = "your-api-key"
            )
            
            // Создание транспорта
            val transport = McpFactory.createTransport(
                config = config,
                httpClient = networkDeps.okHttpClient,
                gson = networkDeps.gson
            )
            
            // Создание клиента
            val client = McpFactory.createClient(transport)
            
            // Подключение
            transport.connect()
            
            // Инициализация
            client.initialize().onSuccess {
                // Использование клиента
                client.listTools().onSuccess { tools ->
                    Log.d(TAG, "Tools: ${tools.size}")
                }
            }
        }
    }
    
    /**
     * Пример 5: Обработка ошибок и переподключение
     */
    fun example5_ErrorHandling(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            val networkDeps = NetworkModule.createNetworkDependencies()
            val mcpRepository = McpFactory.createMcpRepository(
                context = context,
                httpClient = networkDeps.okHttpClient,
                gson = networkDeps.gson
            ) ?: return@launch
            
            // Попытка инициализации с retry
            var retries = 3
            var initialized = false
            
            while (retries > 0 && !initialized) {
                mcpRepository.initialize().onSuccess {
                    initialized = true
                    Log.d(TAG, "MCP initialized")
                }.onFailure { error ->
                    retries--
                    Log.w(TAG, "Initialization failed, retries left: $retries", error)
                    if (retries > 0) {
                        kotlinx.coroutines.delay(1000) // Подождать перед повтором
                    }
                }
            }
            
            if (!initialized) {
                Log.e(TAG, "Failed to initialize MCP after retries")
                return@launch
            }
            
            // Использование репозитория
            mcpRepository.listTools().onSuccess { tools ->
                Log.d(TAG, "Successfully retrieved ${tools.size} tools")
            }.onFailure { error ->
                Log.e(TAG, "Failed to list tools", error)
            }
        }
    }
}

