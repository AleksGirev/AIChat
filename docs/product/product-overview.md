# AIChat Product Overview

## Introduction

AIChat is a production-grade Android chat application that integrates with OpenAI-compatible LLM backends. The application provides an intelligent chat interface powered by advanced AI models, with support for RAG (Retrieval-Augmented Generation), MCP (Model Context Protocol) tool integration, and comprehensive chat history management.

## Core Features

### 1. Intelligent Chat Interface
- Real-time chat with OpenAI-compatible LLM backends (YandexGPT, OpenRouter)
- Support for multiple AI models with easy switching
- Conversation history management with session support
- Message compression and summarization for long conversations
- Token usage tracking and optimization

### 2. RAG (Retrieval-Augmented Generation)
- Document indexing and vector search capabilities
- Semantic search across product documentation
- Context-aware responses using indexed knowledge base
- Support for multiple document formats (Markdown, text files)
- Automatic chunking and embedding generation

### 3. MCP (Model Context Protocol) Integration
- Tool calling capabilities for extended functionality
- Integration with external services via MCP servers
- Support for multiple transport types (HTTP, REST, WebSocket)
- Dynamic tool discovery and execution
- Context-aware tool selection

### 4. Chat History Management
- Persistent storage using Room database
- Session-based conversation organization
- Message compression for long conversations
- External memory storage for context preservation
- Search and retrieval of past conversations

### 5. Support Service
- Product documentation integration
- User ticket history tracking
- Personalized support responses
- Context-aware troubleshooting

## Architecture

### Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (SQLite)
- **Dependency Injection**: Koin
- **Networking**: Retrofit + OkHttp
- **Coroutines**: Kotlin Coroutines + Flow

### Module Structure
- **App Module**: Android application with UI and business logic
- **Console-Agent Module**: Standalone Kotlin application with RAG pipeline and MCP client

### Key Components
- **ChatRepository**: Handles LLM API communication and MCP tool integration
- **RAGPipeline**: Manages document indexing, embedding generation, and vector search
- **McpClient**: Communicates with MCP servers for tool execution
- **ChatDatabase**: Room database for persistent storage
- **SupportContextBuilder**: Combines RAG and CRM context for support responses

## Use Cases

### Primary Use Cases
1. **General Chat**: Users can have natural conversations with AI assistants
2. **Product Support**: Users can ask questions about the product and get contextual help
3. **Documentation Search**: Users can search product documentation through natural language queries
4. **Ticket Management**: Support agents can view and manage user tickets
5. **Context-Aware Assistance**: System considers user's history and previous tickets when providing help

### Support Scenarios
- Troubleshooting common issues
- Understanding product features
- API usage and integration help
- Authorization and authentication guidance
- Best practices and recommendations

## Target Audience

- **End Users**: Individuals using the chat application for various tasks
- **Support Agents**: Staff members providing customer support
- **Developers**: Engineers integrating with the product APIs

## Product Goals

1. Provide intelligent, context-aware assistance
2. Enable seamless integration with external services
3. Maintain conversation context across sessions
4. Deliver personalized support based on user history
5. Ensure reliable and secure operation

## Future Enhancements

- Multi-language support
- Voice input/output
- Advanced analytics and insights
- Custom model fine-tuning
- Enhanced security features
- Mobile and web platform expansion
