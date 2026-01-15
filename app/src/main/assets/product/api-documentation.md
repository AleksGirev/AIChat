# API Documentation

## Overview

AIChat integrates with multiple APIs for LLM services, MCP servers, and external services. This document describes the API endpoints, authentication, and usage patterns.

## LLM APIs

### YandexGPT API

**Base URL**: `https://llm.api.cloud.yandex.net/v1/`

**Authentication:**
- Method: Bearer token
- Header: `Authorization: Bearer {IAM_TOKEN}`
- Folder ID: Required in request body

**Endpoints:**

#### Chat Completion
```
POST /chat/completions
```

**Request:**
```json
{
  "model": "gpt://{folder_id}/yandexgpt/latest",
  "messages": [
    {
      "role": "user",
      "content": "Hello, how are you?"
    }
  ],
  "max_tokens": 2000,
  "temperature": 0.6
}
```

**Response:**
```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "Hello! I'm doing well, thank you."
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 9,
    "completion_tokens": 12,
    "total_tokens": 21
  }
}
```

**Configuration:**
- IAM Token: Get from https://cloud.yandex.ru/docs/iam/operations/iam-token/create
- Folder ID: Get from https://cloud.yandex.ru/docs/resource-manager/operations/folder/get-id
- Set in `Config.kt`: `YANDEX_IAM_TOKEN` and `YANDEX_FOLDER_ID`

### OpenRouter API

**Base URL**: `https://openrouter.ai/api/v1/`

**Authentication:**
- Method: Bearer token
- Header: `Authorization: Bearer {API_KEY}`

**Endpoints:**

#### Chat Completion
```
POST /chat/completions
```

**Request:**
```json
{
  "model": "amazon/nova-2-lite-v1:free",
  "messages": [
    {
      "role": "user",
      "content": "Hello"
    }
  ],
  "max_tokens": 2000
}
```

**Response:**
Same format as YandexGPT (OpenAI-compatible)

**Configuration:**
- API Key: Get from https://openrouter.ai/keys
- Set in `Config.kt`: `OPENAI_API_KEY`

## MCP (Model Context Protocol) APIs

### MCP Protocol Overview

MCP uses JSON-RPC 2.0 for communication. The protocol supports multiple transport types:
- **HTTP**: JSON-RPC over HTTP POST
- **REST**: RESTful API wrapper
- **WebSocket**: Real-time bidirectional communication
- **stdio**: Standard input/output (for local servers)

### MCP Server Endpoints

#### Initialize
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "AIChat-Android",
      "version": "1.0.0"
    }
  }
}
```

#### List Tools
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/list"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "result": {
    "tools": [
      {
        "name": "tool_name",
        "description": "Tool description",
        "inputSchema": {
          "type": "object",
          "properties": {
            "param": {
              "type": "string",
              "description": "Parameter description"
            }
          }
        }
      }
    ]
  }
}
```

#### Call Tool
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "tool_name",
    "arguments": {
      "param": "value"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Tool result"
      }
    ],
    "isError": false
  }
}
```

### CRM MCP Server

**Transport**: stdio (local server)

**Tools:**

#### crm.getUserTickets
Retrieves all tickets for a user.

**Parameters:**
```json
{
  "userId": "user1"
}
```

**Response:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "[{\"ticketId\":\"ticket1\",\"userId\":\"user1\",\"title\":\"Authorization issue\",...}]"
    }
  ]
}
```

#### crm.getTicket
Retrieves a specific ticket.

**Parameters:**
```json
{
  "ticketId": "ticket1"
}
```

#### crm.getUserInfo
Retrieves user information.

**Parameters:**
```json
{
  "userId": "user1"
}
```

#### crm.searchTickets
Searches tickets by query.

**Parameters:**
```json
{
  "query": "authorization",
  "userId": "user1"
}
```

### REST MCP Bridge

For Android, MCP servers can be wrapped in a REST API bridge.

**Base URL**: `http://10.0.2.2:8000` (emulator) or `http://YOUR_IP:8000` (device)

**Endpoints:**

#### Health Check
```
GET /health
```

#### List Tools
```
GET /tools
```

#### Call Tool
```
POST /tools/{toolName}
Content-Type: application/json

{
  "arguments": {
    "param": "value"
  }
}
```

## Internal APIs

### ChatRepository API

**Methods:**

#### sendChatRequest
Sends a chat request to LLM with optional MCP tools.

```kotlin
suspend fun sendChatRequest(
    messages: List<ChatMessage>,
    model: String = Config.DEFAULT_YANDEXGPT_MODEL,
    maxTokens: Int? = 120000,
    temperature: Double? = null,
    enableTools: Boolean = true
): Result<ChatResponse>
```

#### sendMessage
Convenience method for single message.

```kotlin
suspend fun sendMessage(
    userMessage: String,
    conversationHistory: List<ChatMessage> = emptyList(),
    model: String = Config.DEFAULT_YANDEXGPT_MODEL
): Result<String>
```

### RAG Pipeline API

**Methods:**

#### processDocuments
Indexes documents for RAG.

```kotlin
suspend fun processDocuments(
    paths: List<String>,
    onProgress: ((PipelineProgress) -> Unit)? = null,
    replaceExisting: Boolean = true
): Result<Int>
```

#### search
Searches for relevant document chunks.

```kotlin
suspend fun search(
    queryText: String,
    limit: Int = 10,
    minSimilarity: Float = 0.0f
): Result<List<SearchResult>>
```

## Error Handling

### API Error Responses

**HTTP Errors:**
- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Invalid or missing authentication
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server error

**MCP Errors:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

**Error Codes:**
- `-32700`: Parse error
- `-32600`: Invalid request
- `-32601`: Method not found
- `-32602`: Invalid params
- `-32603`: Internal error
- `-32000`: Server error

## Rate Limits

### YandexGPT
- Check Yandex Cloud documentation for current limits
- IAM tokens may have expiration (typically 12 hours)

### OpenRouter
- Free tier: Limited requests per day
- Paid tier: Higher rate limits
- Check https://openrouter.ai/docs for current limits

### MCP Servers
- Local servers: No rate limits
- Remote servers: Depends on server configuration

## Best Practices

1. **Authentication**: Store API keys securely, use EncryptedSharedPreferences
2. **Error Handling**: Always handle API errors gracefully
3. **Retry Logic**: Implement retry for transient failures
4. **Token Management**: Monitor token usage and optimize requests
5. **Caching**: Cache tool results when appropriate
6. **Logging**: Log API calls for debugging (without sensitive data)

## Configuration

All API configurations are in `app/src/main/java/com/example/aichat/data/Config.kt`:

```kotlin
object Config {
    const val OPENAI_API_KEY = "..."
    const val YANDEX_IAM_TOKEN = "..."
    const val YANDEX_FOLDER_ID = "..."
    const val MCP_SERVER_URL = "http://10.0.2.2:8000"
    // ...
}
```

For production, consider:
- Using BuildConfig for API keys
- Environment-specific configuration
- Secure key management systems
