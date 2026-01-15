# Frequently Asked Questions (FAQ)

## General Questions

### What is AIChat?
AIChat is an Android chat application that provides intelligent conversations with AI assistants powered by OpenAI-compatible LLM backends. It includes advanced features like RAG, MCP tool integration, and comprehensive chat history management.

### Which AI models are supported?
AIChat supports multiple AI models including:
- YandexGPT (via Yandex Cloud API)
- OpenRouter models (including amazon/nova-2-lite-v1:free)
- Any OpenAI-compatible API endpoint

### How do I switch between different AI models?
You can switch models in the chat settings. The application supports dynamic model selection, and you can configure different models for different use cases.

### Is my conversation history stored?
Yes, all conversations are stored locally in the device's Room database. Sessions are organized and can be retrieved later. The app also supports message compression for long conversations.

## Authentication & User Management

### How do I log in?
Use the authentication screen to log in with your email/username and password. For MVP, mock authentication is supported with local user storage.

### Can I register a new account?
Yes, the registration flow is available in the authentication screen. New users are stored locally in the database.

### Where is my user data stored?
User authentication data is stored securely using EncryptedSharedPreferences. User profile information is stored in the Room database.

### How do I log out?
You can log out from the settings screen. This will clear your session and return you to the login screen.

## RAG & Documentation

### What is RAG?
RAG (Retrieval-Augmented Generation) allows the AI to search through indexed documentation and use relevant information to provide more accurate and contextual responses.

### How does document indexing work?
Documents are loaded, split into chunks, embedded using vector models, and stored in a vector database. When you ask a question, the system searches for relevant chunks and includes them in the context.

### Which documents are indexed?
Product documentation in the `docs/product/` directory is indexed, including:
- Product overview
- FAQ
- Troubleshooting guides
- API documentation
- Authorization guides
- User manuals

### How do I know if RAG context is being used?
The chat interface indicates when RAG context is being used. You may see references to documentation sources in responses.

## MCP Tools & Integration

### What are MCP tools?
MCP (Model Context Protocol) tools extend the AI's capabilities by allowing it to call external functions. For example, the AI can retrieve user tickets, search documentation, or interact with external APIs.

### Which MCP servers are available?
- **CRM MCP Server**: Manages user tickets and support information
- **Repository MCP Server**: Provides Git and file system operations
- **Context7 MCP Server**: Provides code repository context
- **BrightData MCP Server**: Reads articles from URLs

### How do MCP tools work?
When you ask a question, the AI can decide to call MCP tools to gather additional information. The tool results are then included in the AI's response.

### Can I add custom MCP tools?
Yes, you can create custom MCP servers following the MCP protocol specification. The application supports HTTP, REST, and WebSocket transport types.

## Support & Troubleshooting

### How do I get support?
You can use the support chat feature, which provides contextual help based on:
- Product documentation (via RAG)
- Your previous tickets
- Common troubleshooting guides

### What if I have an authorization issue?
Check the authorization guide in the documentation. The support assistant can also help troubleshoot authorization problems based on your ticket history.

### How does the support system know about my previous tickets?
The support system integrates with the CRM MCP server, which maintains your ticket history. When you ask a question, the system retrieves relevant tickets to provide personalized assistance.

### Can I create a new support ticket?
For MVP, ticket creation is read-only. Future versions will support creating tickets directly from the chat interface.

## Technical Questions

### How is data stored?
- **Chat messages**: Room database (SQLite)
- **User data**: Room database + EncryptedSharedPreferences
- **RAG index**: Vector database (SQLite with embeddings)
- **Tickets**: JSON file (via CRM MCP server)

### What is message compression?
For long conversations, older messages are compressed into summaries to save tokens and maintain context. The original messages are preserved, but only summaries are sent to the LLM.

### How are tokens tracked?
The application tracks request tokens, response tokens, and total tokens for each conversation. This helps optimize API usage and costs.

### Can I export my chat history?
Export functionality is planned for future versions. Currently, data is stored locally in the Room database.

## Privacy & Security

### Is my data secure?
Yes, the application uses:
- EncryptedSharedPreferences for sensitive data
- Local database storage (no cloud sync by default)
- Secure API communication (HTTPS)

### Is my conversation data shared?
No, all conversations are stored locally on your device. API calls to LLM services are made directly, and conversation history is not shared with third parties.

### How are API keys stored?
API keys are stored in the application's configuration. For production, consider using BuildConfig or secure key management systems.

## Performance

### Why are some responses slow?
Response time depends on:
- LLM API latency
- RAG search time (if enabled)
- MCP tool execution time
- Network conditions

### How can I improve performance?
- Use faster models for simple queries
- Disable RAG for general conversations
- Reduce message history length
- Use message compression for long sessions

## Error Handling

### What if the LLM API fails?
The application handles API failures gracefully and shows error messages. You can retry the request or switch to a different model.

### What if MCP tools fail?
If MCP tools fail, the chat continues without tool context. The application logs errors for debugging.

### What if RAG search fails?
If RAG search fails, the chat falls back to general knowledge without documentation context.
