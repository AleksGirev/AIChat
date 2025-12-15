# MCP (Model Context Protocol) Integration

Эта реализация интегрирует MCP-сервер в Android-приложение для расширения возможностей LLM через инструменты (tools).

## Архитектура

### Компоненты

1. **McpTransport** - Интерфейс для транспортных слоев
   - `StdioMcpTransport` - для локальных серверов через stdin/stdout
   - `HttpMcpTransport` - для удаленных серверов через HTTP
   - `WebSocketMcpTransport` - для удаленных серверов через WebSocket

2. **McpClient** - Клиент для общения с MCP-сервером
   - Инициализация соединения
   - Получение списка инструментов (`listTools`)
   - Вызов инструментов (`callTool`)

3. **McpRepository** - Репозиторий для работы с MCP
   - Высокоуровневый интерфейс
   - Конвертация MCP tools в OpenAI-совместимый формат

4. **McpConfig** - Конфигурация и безопасное хранение настроек
   - Использует `EncryptedSharedPreferences` для хранения API ключей
   - Поддержка разных типов транспорта

5. **ChatRepository** - Интеграция с LLM
   - Автоматическое получение tools из MCP
   - Передача tools в LLM запросы
   - Обработка `tool_calls` от LLM
   - Выполнение инструментов через MCP
   - Возврат результатов обратно в LLM

## Использование

### 1. Настройка MCP сервера

```kotlin
val config = McpConfig(context)
config.setServerConfig(
    McpConfig.ServerConfig(
        type = "http", // или "websocket", "stdio"
        url = "https://mcp.example.com/mcp",
        apiKey = "your-api-key" // опционально
    )
)
config.setEnabled(true)
```

### 2. Создание MCP репозитория

```kotlin
val mcpRepository = McpFactory.createMcpRepository(
    context = context,
    httpClient = okHttpClient,
    gson = gson
)
```

### 3. Интеграция с ChatRepository

```kotlin
val chatRepository = ChatRepository(
    apiService = apiService,
    apiKey = apiKey,
    yandexApiService = yandexApiService,
    mcpRepository = mcpRepository, // Передаем MCP репозиторий
    gson = gson
)
```

### 4. Использование в ChatViewModel

MCP автоматически интегрирован в `ChatViewModel`. При отправке сообщения:

1. `ChatRepository` получает список tools из MCP
2. Передает tools в LLM запрос
3. Если LLM хочет вызвать инструмент, получает `tool_calls` в ответе
4. Выполняет инструменты через MCP
5. Отправляет результаты обратно в LLM
6. Получает финальный ответ от LLM

## Примеры

### Локальный MCP сервер (stdio)

```kotlin
val config = McpConfig.ServerConfig(
    type = "stdio",
    stdioCommand = listOf("node", "/path/to/mcp-server.js")
)
```

### Удаленный MCP сервер (HTTP)

```kotlin
val config = McpConfig.ServerConfig(
    type = "http",
    url = "https://mcp.example.com/mcp",
    apiKey = "your-api-key"
)
```

### Удаленный MCP сервер (WebSocket)

```kotlin
val config = McpConfig.ServerConfig(
    type = "websocket",
    url = "wss://mcp.example.com/mcp",
    apiKey = "your-api-key"
)
```

## Безопасность

- API ключи хранятся в `EncryptedSharedPreferences`
- Используется Android Keystore для шифрования
- Ключи не попадают в код приложения

## Обработка ошибок

Все операции возвращают `Result<T>` для безопасной обработки ошибок:

```kotlin
mcpRepository.listTools().onSuccess { tools ->
    // Обработка успешного результата
}.onFailure { error ->
    // Обработка ошибки
    Log.e("MCP", "Failed to list tools", error)
}
```

## Тестирование

### Mock MCP сервер

Для тестирования можно создать mock реализацию `McpTransport`:

```kotlin
class MockMcpTransport : McpTransport {
    override suspend fun sendRequest(request: McpRequest): McpResponse {
        // Возвращаем mock ответ
    }
}
```

### Интеграционные тесты

Используйте реальный MCP сервер в тестах или mock сервер для изоляции.

## Ограничения

1. **Streaming**: Текущая реализация не поддерживает streaming для tool results в SSE. Это можно добавить в будущем.

2. **Stdio Transport**: Требует запуска процесса на устройстве, что может быть ограничено на некоторых Android устройствах.

3. **WebSocket**: Требует постоянного соединения, что может быть проблематично при работе в фоне.

## Будущие улучшения

- [ ] Поддержка streaming для tool results в SSE
- [ ] Автоматическое переподключение при разрыве соединения
- [ ] Кэширование списка tools
- [ ] Поддержка параллельных tool calls
- [ ] Метрики и логирование

