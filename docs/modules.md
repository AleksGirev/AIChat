# Module Responsibilities

## app Module

### Package Structure

#### `com.example.aichat.ui/`
**Responsibility**: UI layer - all Compose screens and UI components

- **`chat/`**: Chat interface screens
  - `ChatScreen.kt` - Main chat interface
  - `ChatListScreen.kt` - List of chat sessions
  
- **`navigation/`**: Navigation logic
  - `Navigation.kt` - Screen routing and navigation state
  
- **`viewmodel/`**: ViewModels for each screen
  - `ChatViewModel.kt` - Chat state and business logic
  - `ModelComparisonViewModel.kt` - Model comparison logic
  - `TokenComparisonViewModel.kt` - Token analysis
  
- **`settings/`**: Settings screen
- **`comparison/`**: Model comparison screens
- **`searchagent/`**: Search agent feature
- **`summary/`**: Weather summary screen
- **`theme/`**: Material Design theme configuration

#### `com.example.aichat.data/`
**Responsibility**: Data layer - repositories, API clients, database, MCP

- **`repository/`**: Business logic repositories
  - `ChatRepository.kt` - Main chat repository, LLM communication, MCP tool integration
  
- **`api/`**: API service interfaces
  - `OpenAiApiService.kt` - OpenAI-compatible API
  - `YandexApiService.kt` - YandexGPT API
  
- **`local/`**: Local storage (Room database)
  - `ChatDatabase.kt` - Room database definition
  - `ChatHistoryRepository.kt` - Message history operations
  - `ChatSessionRepository.kt` - Session management
  - `ExternalMemoryRepository.kt` - Long-term memory storage
  - DAOs for each entity type
  
- **`mcp/`**: Model Context Protocol integration
  - `McpClient.kt` - MCP protocol client
  - `McpRepository.kt` - High-level MCP operations
  - `McpFactory.kt` - MCP client factory
  - `McpConfig.kt` - Configuration management
  - `transport/` - Transport implementations (stdio, HTTP, WebSocket)
  
- **`network/`**: Network configuration
  - `NetworkModule.kt` - Retrofit, OkHttp, Gson setup
  
- **`model/`**: Data models
  - `ChatMessage.kt` - Message data class
  - `ChatRequest.kt` - API request model
  - `ChatResponse.kt` - API response model
  
- **`util/`**: Utility classes
  - `TokenCounter.kt` - Token estimation
  - `ModelCostCalculator.kt` - Cost calculation
  - `HistoryCompressionService.kt` - Message summarization
  - `JsonMemoryExporter.kt` - Data export/import

#### `com.example.aichat.di/`
**Responsibility**: Dependency injection configuration

- `AppModule.kt` - Koin module definitions
  - Network dependencies (OkHttp, Retrofit, Gson)
  - Database and DAOs
  - Repositories
  - ViewModels
  - MCP configuration

#### `com.example.aichat.service/`
**Responsibility**: Background services

- `WeatherService.kt` - Weather data synchronization

#### `com.example.aichat/`
**Responsibility**: Application entry point

- `AIChatApplication.kt` - Application class, Koin initialization
- `MainActivity.kt` - Main activity, navigation setup

## console-agent Module

### Package Structure

#### `com.example.aichat.console/`
**Responsibility**: Standalone console application

- **`Main.kt`**: Entry point, CLI loop
- **`ConsoleConfig.kt`**: Configuration management

#### `com.example.aichat.console.agent/`
**Responsibility**: Agent orchestration

- `AgentOrchestrator.kt` - Base orchestrator for LLM + MCP tools
- `RAGAgentOrchestrator.kt` - RAG-enhanced orchestrator

#### `com.example.aichat.console.rag/`
**Responsibility**: RAG (Retrieval-Augmented Generation) pipeline

- `RAGPipeline.kt` - Main pipeline orchestrator
- `DocumentLoader.kt` - Document loading from files
- `TextSplitter.kt` - Text chunking with overlap
- `EmbeddingGenerator.kt` - Embedding generation (Ollama)
- `VectorStore.kt` - Vector storage and search (SQLite)
- `RelevanceReranker.kt` - Second-stage relevance filtering
- `model/` - RAG data models (Document, TextChunk, SearchResult)

#### `com.example.aichat.console.mcp/`
**Responsibility**: MCP client for console agent

- `McpClient.kt` - MCP protocol client
- `McpClientFactory.kt` - Client factory
- `McpClientWrapper.kt` - Wrapper for tool access
- `StdioMcpTransport.kt` - stdio transport implementation

#### `com.example.aichat.console.llm/`
**Responsibility**: LLM API client

- `OpenAiClient.kt` - OpenAI-compatible API client

#### `com.example.aichat.console.model/`
**Responsibility**: Data models

- `ChatMessage.kt` - Chat message model
- `McpModels.kt` - MCP protocol models

#### `com.example.aichat.console.util/`
**Responsibility**: Utility functions

- JSON utilities, helpers

## Module Dependencies

```
app/
├── Depends on: Android SDK, Jetpack libraries
├── Uses: Room, Retrofit, Koin, Compose
└── Exports: Android application

console-agent/
├── Depends on: Kotlin stdlib, Coroutines
├── Uses: SQLite JDBC, Apache Tika, OkHttp
└── Exports: Standalone JAR executable
```

## Module Communication

- **app** and **console-agent** are independent modules
- Both share similar patterns (MCP, LLM clients) but are separate codebases
- No direct dependencies between them
- Both can be built and run independently

