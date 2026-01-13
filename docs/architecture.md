# Architecture Overview

## Project Structure

AIChat is a production-grade Android chat application that integrates with OpenAI-compatible LLM backends. The project follows clean architecture principles with clear separation of concerns.

### Modules

1. **app/** - Main Android application module
   - UI layer (Jetpack Compose)
   - ViewModels (MVVM pattern)
   - Android-specific components

2. **console-agent/** - Standalone Kotlin console application
   - RAG (Retrieval-Augmented Generation) pipeline
   - MCP (Model Context Protocol) client integration
   - CLI interface for AI assistance

### Architecture Pattern

**MVVM (Model-View-ViewModel)** with the following layers:

#### 1. UI Layer (`app/src/main/java/com/example/aichat/ui/`)
- **Compose Screens**: Declarative UI using Jetpack Compose
- **Navigation**: Screen-based navigation using sealed classes
- **State Management**: StateFlow for reactive state updates

Key components:
- `MainActivity.kt` - Entry point, initializes navigation
- `ui/navigation/Navigation.kt` - Navigation orchestrator
- `ui/chat/` - Chat interface screens
- `ui/viewmodel/` - ViewModels for each screen

#### 2. ViewModel Layer (`app/src/main/java/com/example/aichat/ui/viewmodel/`)
- **ChatViewModel**: Manages chat state, message history, session management
- **ModelComparisonViewModel**: Handles model comparison features
- **TokenComparisonViewModel**: Manages token usage analysis

Responsibilities:
- Business logic coordination
- State management (StateFlow)
- Repository interaction
- Session and history management

#### 3. Repository Layer (`app/src/main/java/com/example/aichat/data/repository/`)
- **ChatRepository**: Main data repository for chat operations
  - LLM API communication (OpenAI-compatible)
  - MCP tool integration
  - Model comparison
  - Token counting and cost calculation

#### 4. Data Layer (`app/src/main/java/com/example/aichat/data/`)
- **Local Storage**: Room database for persistent storage
  - Chat messages
  - Chat sessions
  - External memory (long-term context)
  - Weather data
  
- **Network**: Retrofit-based API clients
  - OpenAI-compatible API service
  - YandexGPT API service
  
- **MCP Integration**: Model Context Protocol support
  - MCP client for tool calling
  - Transport layers (stdio, HTTP, WebSocket)
  - Tool discovery and execution

#### 5. Dependency Injection (`app/src/main/java/com/example/aichat/di/`)
- **Koin**: Lightweight DI framework
- **AppModule**: Central module configuration
- All dependencies injected as singletons or scoped instances

### Key Architectural Decisions

1. **MVVM over MVI**: Chosen for simplicity and team familiarity
2. **Koin over Hilt**: Lighter weight, easier setup
3. **Room Database**: Native Android solution for persistence
4. **StateFlow**: Reactive state management with lifecycle awareness
5. **Coroutines + Flow**: Modern async/await patterns
6. **Modular Structure**: Clear separation between app and console-agent

### Data Flow

```
User Input (UI)
    ↓
ViewModel (State Management)
    ↓
Repository (Business Logic)
    ↓
API Service / Database (Data Source)
    ↓
Response flows back through layers
    ↓
UI updates via StateFlow
```

### Session Management

- Each chat conversation is a **Session** with unique ID
- Messages stored in Room database with session reference
- **History Compression**: Every 10 messages automatically summarized to reduce token usage
- **External Memory**: Long-term context stored separately for efficient retrieval

### MCP Integration

- **Model Context Protocol** enables LLM to call external tools
- Tools discovered dynamically from MCP servers
- Supports multiple transport types (stdio, HTTP, WebSocket)
- Tool results fed back to LLM for context-aware responses

### Console Agent Architecture

The `console-agent` module is a standalone Kotlin application:

- **RAG Pipeline**: Document indexing, embedding generation, vector search
- **MCP Client**: Connects to MCP servers for tool access
- **CLI Interface**: Interactive console for AI assistance

### Technology Stack

- **Language**: Kotlin 100%
- **UI**: Jetpack Compose
- **Architecture**: MVVM
- **DI**: Koin
- **Networking**: Retrofit + OkHttp
- **Database**: Room (SQLite)
- **Async**: Coroutines + Flow
- **Serialization**: Gson
- **Security**: EncryptedSharedPreferences for API keys

