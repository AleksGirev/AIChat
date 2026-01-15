# Support Service Implementation Prompt (Short Version)

## Context
You are working on AIChat - an Android chat app (Kotlin, Jetpack Compose, MVVM, Room DB) with existing RAG pipeline and MCP client infrastructure.

## Task
Implement a support service that:
1. **RAG**: Answers using product documentation and FAQ
2. **MCP CRM**: Integration with mock CRM (JSON file with users/tickets)
3. **Context-aware**: Answers consider user's ticket history

**Example**: User asks "Why isn't authorization working?" → Assistant responds using RAG docs + user's ticket history.

## Implementation Steps

### 1. Product Documentation
- Create `docs/product/` with: `product-overview.md`, `faq.md`, `troubleshooting.md`, `api-documentation.md`, `authorization-guide.md`, `user-guide.md`
- Index docs in existing RAG pipeline (`console-agent/src/main/kotlin/com/example/aichat/console/rag/RAGPipeline.kt`)

### 2. Authentication & User Management
- Add `AuthScreen.kt` + `AuthViewModel.kt` (Jetpack Compose)
- Create `UserEntity` in Room DB (version 8)
- Store user ID in `EncryptedSharedPreferences` after login
- Link `ChatSessionEntity` to `userId`

### 3. CRM MCP Server
- Create `console-agent/src/main/kotlin/com/example/aichat/console/mcp/crm/`:
  - `CrmMcpServer.kt` - MCP server (JSON-RPC 2.0 over stdio)
  - `TicketService.kt` - Ticket management
  - Tools: `crm.getUserTickets(userId)`, `crm.getTicket(ticketId)`, `crm.getUserInfo(userId)`, `crm.searchTickets(query, userId)`
- Store data in `console-agent/data/crm_data.json`:
  ```json
  {
    "users": [{"userId": "user1", "email": "...", ...}],
    "tickets": [{"ticketId": "t1", "userId": "user1", "title": "...", "status": "open", "messages": [...]}]
  }
  ```
- Generate 3-5 mock tickets per user with common issues (auth problems, API errors, etc.)

### 4. Support Chat Integration
- Modify `ChatRepository` to:
  1. Get current `userId` from `EncryptedSharedPreferences`
  2. Search RAG for relevant docs
  3. Call MCP CRM tools: `crm.getUserTickets(userId)`
  4. Build context: RAG chunks + user tickets
  5. Send enhanced prompt to LLM with support system prompt
- System prompt: "You are a support assistant. Use documentation to answer. Consider user's ticket history for personalized responses."

### 5. UI Updates
- Show user info in chat header
- Add logout in settings
- Handle auth flow (redirect to login if not authenticated)

## Key Files to Create/Modify

**New:**
- `app/src/main/java/com/example/aichat/ui/auth/AuthScreen.kt`
- `app/src/main/java/com/example/aichat/ui/auth/AuthViewModel.kt`
- `app/src/main/java/com/example/aichat/data/local/UserEntity.kt`
- `app/src/main/java/com/example/aichat/data/local/UserDao.kt`
- `app/src/main/java/com/example/aichat/data/support/SupportContextBuilder.kt`
- `console-agent/src/main/kotlin/com/example/aichat/console/mcp/crm/CrmMcpServer.kt`
- `console-agent/src/main/kotlin/com/example/aichat/console/mcp/crm/TicketService.kt`
- `console-agent/data/crm_data.json`
- `docs/product/*.md` (6 files)

**Modify:**
- `ChatDatabase.kt` (add `UserEntity`, version 8)
- `ChatSessionEntity.kt` (add `userId` field)
- `ChatRepository.kt` (add RAG + CRM context building)
- `ChatViewModel.kt` (pass user context)
- `McpFactory.kt` (add CRM MCP server support)

## Testing Checklist
- [ ] Login works, user ID stored
- [ ] RAG indexes product docs
- [ ] CRM MCP server returns tickets
- [ ] Support chat includes RAG + ticket context
- [ ] Responses reference user's tickets

## Expected Flow
1. User logs in → `userId` stored
2. User asks: "Why isn't authorization working?"
3. System: RAG search → `crm.getUserTickets(userId)` → Build context → LLM response
4. Response: "Based on your ticket #123 and docs, the issue might be..."

---

**Follow existing patterns**: MVVM, Koin DI, Room, existing RAG/MCP infrastructure. See full prompt in `support-service-prompt.md` for details.
