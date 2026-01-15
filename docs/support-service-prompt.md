# Prompt for Implementing Support Service with RAG and MCP Integration

You are an expert Android developer working on the AIChat project - a production-grade Android chat application that integrates with OpenAI-compatible LLM backends. The project uses Kotlin, Jetpack Compose, MVVM architecture, Room database, and has existing RAG (Retrieval-Augmented Generation) and MCP (Model Context Protocol) infrastructure.

## Project Context

**Current Architecture:**
- **App Module**: Android app with Jetpack Compose UI, MVVM pattern, Room database
- **Console-Agent Module**: Standalone Kotlin app with RAG pipeline and MCP client integration
- **Tech Stack**: Kotlin, Jetpack Compose, Coroutines + Flow, Koin DI, Retrofit, Room (SQLite)
- **Existing Features**: 
  - Chat with LLM (OpenAI-compatible API)
  - RAG pipeline for document indexing and retrieval (`console-agent/src/main/kotlin/com/example/aichat/console/rag/`)
  - MCP client for tool calling (`app/src/main/java/com/example/aichat/data/mcp/`)
  - Room database for chat messages and sessions
  - History compression and external memory storage

**Database Schema (Room):**
- `ChatMessageEntity` - stores chat messages
- `ChatSessionEntity` - stores chat sessions
- `ExternalMemoryEntity` - stores long-term context
- Current version: 7

## Task: Implement Support Service with RAG and MCP Integration

Create a mini-service that answers user questions about your product using:
1. **RAG**: Answers using product documentation and FAQ
2. **MCP Integration**: Integration with CRM (or JSON file with current users/tickets)
3. **User Context**: Answers consider user's ticket history

**Example**: "Why isn't authorization working?" - assistant responds considering the context of the user's tickets.

**Result**: An assistant working with support functionality.

## Requirements

### 1. Product Documentation and RAG Setup

**1.1 Create Product Documentation**
- Create comprehensive product documentation in `docs/product/` directory:
  - `product-overview.md` - Product description, main features, architecture
  - `faq.md` - Frequently asked questions with answers
  - `troubleshooting.md` - Common issues and solutions
  - `api-documentation.md` - API endpoints, authentication, usage examples
  - `authorization-guide.md` - Detailed authorization flow, common issues, solutions
  - `user-guide.md` - User manual, step-by-step guides
- Documentation should be detailed enough for RAG to provide meaningful context
- Include real-world scenarios and troubleshooting steps

**1.2 Index Documentation in RAG**
- Use existing RAG pipeline (`console-agent/src/main/kotlin/com/example/aichat/console/rag/RAGPipeline.kt`)
- Index all documentation files from `docs/product/` directory
- Ensure RAG index is accessible from Android app (consider shared database or API endpoint)
- Test RAG search functionality with sample queries

### 2. User Authentication and User Management

**2.1 Simple Authentication System**
- Add authentication screen to Android app (`app/src/main/java/com/example/aichat/ui/auth/`)
  - `AuthScreen.kt` - Login/registration UI with Jetpack Compose
  - `AuthViewModel.kt` - Handles authentication logic
- Implement simple authentication:
  - Email/username + password login
  - Optional: registration flow
  - Store authentication state in `EncryptedSharedPreferences` (for security)
- No backend required - use local authentication (mock users for MVP)

**2.2 User Entity and Storage**
- Create `UserEntity` in Room database:
  ```kotlin
  @Entity(tableName = "users")
  data class UserEntity(
      @PrimaryKey val userId: String,
      val email: String,
      val username: String,
      val createdAt: Long,
      val lastLoginAt: Long
  )
  ```
- Create `UserDao` and `UserRepository` for user management
- Update `ChatDatabase` to include `UserEntity` (version 8)
- Store current user ID in `EncryptedSharedPreferences` after login
- Link chat sessions to user ID (add `userId` field to `ChatSessionEntity`)

### 3. Mock CRM with Tickets (MCP Integration)

**3.1 Create MCP Server for CRM**
- Create new MCP server: `console-agent/src/main/kotlin/com/example/aichat/console/mcp/crm/`
  - `CrmMcpServer.kt` - MCP server implementation (JSON-RPC 2.0 over stdio)
  - `CrmMcpServerMain.kt` - Main entry point for standalone server
  - `TicketService.kt` - Service for managing tickets
  - `UserService.kt` - Service for managing users
- MCP server should expose tools:
  - `crm.getUserTickets(userId: String)` - Returns all tickets for a user
  - `crm.getTicket(ticketId: String)` - Returns specific ticket details
  - `crm.getUserInfo(userId: String)` - Returns user information
  - `crm.searchTickets(query: String, userId: String?)` - Search tickets by query
- Use JSON file for data storage: `console-agent/data/crm_data.json`
- Follow existing MCP server pattern (see `console-agent/src/main/kotlin/com/example/aichat/console/mcp/repo/RepoMcpServer.kt`)

**3.2 Mock Ticket Data Structure**
- Create JSON structure for tickets:
  ```json
  {
    "users": [
      {
        "userId": "user1",
        "email": "user1@example.com",
        "username": "user1",
        "createdAt": 1234567890
      }
    ],
    "tickets": [
      {
        "ticketId": "ticket1",
        "userId": "user1",
        "title": "Authorization not working",
        "description": "I cannot log in to the application",
        "status": "open",
        "priority": "high",
        "createdAt": 1234567890,
        "updatedAt": 1234567890,
        "messages": [
          {
            "messageId": "msg1",
            "sender": "user",
            "content": "Why isn't authorization working?",
            "timestamp": 1234567890
          },
          {
            "messageId": "msg2",
            "sender": "support",
            "content": "We're investigating the issue...",
            "timestamp": 1234567891
          }
        ]
      }
    ]
  }
  ```

**3.3 Generate Mock Tickets**
- Create script or function to generate mock tickets for each user:
  - At least 3-5 tickets per user with different statuses (open, closed, in-progress)
  - Include common issues: authorization problems, API errors, feature questions
  - Add realistic timestamps and conversation history
  - Store in `console-agent/data/crm_data.json`
- Ensure tickets are linked to user IDs

**3.4 Integrate MCP CRM Server with Android App**
- Update `app/src/main/java/com/example/aichat/data/mcp/McpFactory.kt` to support CRM MCP server
- Configure CRM MCP server connection (stdio transport)
- Ensure MCP client can call CRM tools from Android app
- Test MCP tool calling from `ChatRepository`

### 4. Support Chat Integration

**4.1 Enhanced Chat with RAG and CRM Context**
- Modify `ChatRepository` to:
  - Retrieve current user ID from `EncryptedSharedPreferences`
  - When user asks a question:
    1. Search RAG index for relevant documentation/FAQ
    2. Call MCP CRM tools to get user's ticket history
    3. Combine RAG context + ticket context in system prompt
    4. Send enhanced prompt to LLM
    5. Return contextualized response
- Update `ChatViewModel` to pass user context to repository

**4.2 System Prompt Enhancement**
- Create support-specific system prompt that:
  - Instructs LLM to use RAG documentation for product knowledge
  - Instructs LLM to consider user's ticket history for personalized answers
  - Example: "You are a support assistant. Use the provided documentation to answer questions. Consider the user's previous tickets to provide personalized responses. If the user asks about an issue they've reported before, reference that ticket."

**4.3 Context Building**
- Build context string that includes:
  - Relevant RAG chunks from documentation search
  - User's recent tickets (last 5-10 tickets)
  - Relevant ticket messages if query matches ticket content
- Format context clearly for LLM consumption

### 5. UI Enhancements

**5.1 Support Chat Screen**
- Enhance `ChatScreen.kt` to show:
  - User info (username/email) in header
  - Indication when RAG context is being used
  - Optional: Show referenced tickets in response
- Add logout button in settings

**5.2 Authentication Flow**
- Add navigation to auth screen on app start if not authenticated
- Store auth state and redirect authenticated users to chat
- Handle session expiration

## Implementation Guidelines

### Code Structure

**New Files to Create:**
```
app/src/main/java/com/example/aichat/
├── ui/auth/
│   ├── AuthScreen.kt
│   └── AuthViewModel.kt
├── data/local/
│   ├── UserEntity.kt
│   ├── UserDao.kt
│   └── UserRepository.kt
└── data/support/
    └── SupportContextBuilder.kt (builds RAG + CRM context)

console-agent/src/main/kotlin/com/example/aichat/console/
├── mcp/crm/
│   ├── CrmMcpServer.kt
│   ├── CrmMcpServerMain.kt
│   ├── TicketService.kt
│   └── UserService.kt
└── data/
    └── crm_data.json

docs/product/
├── product-overview.md
├── faq.md
├── troubleshooting.md
├── api-documentation.md
├── authorization-guide.md
└── user-guide.md
```

### Database Migration

- Update `ChatDatabase` version to 8
- Add `UserEntity` to database entities
- Add `userId` field to `ChatSessionEntity` (nullable for backward compatibility)
- Create migration script if needed

### Testing

1. **Test Authentication:**
   - Login with mock user
   - Verify user ID stored correctly
   - Verify chat sessions linked to user

2. **Test RAG:**
   - Index product documentation
   - Query RAG with sample questions
   - Verify relevant chunks returned

3. **Test MCP CRM:**
   - Start CRM MCP server standalone
   - Test MCP tools via JSON-RPC
   - Verify ticket retrieval works

4. **Test Support Chat:**
   - Ask question about product
   - Verify RAG context included
   - Verify user tickets considered
   - Verify personalized response

### Security Considerations

- Use `EncryptedSharedPreferences` for storing auth tokens and user IDs
- Validate user input in authentication
- Sanitize data before storing in database
- Ensure MCP server validates user IDs before returning tickets

### Error Handling

- Handle RAG search failures gracefully (fallback to general knowledge)
- Handle MCP connection failures (continue without CRM context)
- Handle authentication failures with clear error messages
- Handle missing user tickets (empty context)

## Expected Behavior

**Example Flow:**
1. User logs in → User ID stored
2. User asks: "Why isn't authorization working?"
3. System:
   - Searches RAG for "authorization" → finds relevant docs
   - Calls `crm.getUserTickets(userId)` → finds user's auth-related tickets
   - Builds context: "User has open ticket #123 about login issues. Documentation says..."
   - Sends to LLM with context
4. LLM responds: "Based on your ticket #123 and our documentation, the issue might be..."

## Deliverables

1. ✅ Product documentation in `docs/product/`
2. ✅ RAG index populated with documentation
3. ✅ Authentication system (login screen + user storage)
4. ✅ User entity in Room database
5. ✅ CRM MCP server with ticket management tools
6. ✅ Mock ticket data for multiple users
7. ✅ Enhanced chat with RAG + CRM context integration
8. ✅ Support-specific system prompts
9. ✅ UI updates for support chat
10. ✅ Testing and validation

## Notes

- Follow existing code patterns and architecture (MVVM, Koin DI, Room)
- Use existing RAG pipeline - don't recreate it
- Use existing MCP client infrastructure - extend it
- Keep code modular and testable
- Add proper error handling and logging
- Update project documentation (`docs/architecture.md`) with new components
- Follow Kotlin coding conventions and project style guide

## Questions to Consider

- Should tickets be editable through MCP tools? (For MVP: read-only is fine)
- Should we support ticket creation from chat? (Future enhancement)
- How to handle multiple users in mock CRM? (Use JSON file with array of users)
- Should RAG index be shared between console-agent and Android app? (Consider API or shared database)

---

**Start by:**
1. Creating product documentation
2. Setting up authentication
3. Creating CRM MCP server with mock data
4. Integrating everything in chat flow

Good luck! 🚀
