# Support Service Integration - Complete Setup

## Overview

The support service is now fully integrated! When users enable support mode and ask questions, the system will:

1. **Search RAG documentation** - Finds relevant product documentation
2. **Retrieve user tickets** - Gets user's previous support tickets via CRM MCP
3. **Combine context** - Merges both sources into enhanced prompt
4. **Generate personalized response** - LLM responds with context-aware answers

## How It Works

### Flow Diagram

```
User Question
    ↓
Support Mode Enabled?
    ↓ YES
SupportContextBuilder.buildSupportContext()
    ├─→ RagService.searchDocumentation() → Product docs
    └─→ CrmMcpClient.searchTickets() → User tickets
    ↓
Enhanced Prompt (Question + RAG + Tickets)
    ↓
LLM Response (Context-aware answer)
```

### Components

1. **RagService** (`app/src/main/java/com/example/aichat/data/rag/RagService.kt`)
   - Searches product documentation files
   - Uses simple text matching (MVP)
   - Can be upgraded to semantic search later

2. **CrmMcpClient** (`app/src/main/java/com/example/aichat/data/crm/CrmMcpClient.kt`)
   - Connects to CRM MCP server
   - Retrieves user tickets
   - Searches tickets by query

3. **SupportContextBuilder** (`app/src/main/java/com/example/aichat/data/support/SupportContextBuilder.kt`)
   - Combines RAG + CRM context
   - Formats context for LLM
   - Handles errors gracefully

4. **ChatViewModel** (`app/src/main/java/com/example/aichat/ui/viewmodel/ChatViewModel.kt`)
   - Toggles support mode
   - Uses SupportContextBuilder when enabled
   - Enhances user messages with context

## Setup Steps

### 1. Copy Documentation Files

Copy product documentation to Android assets:

```bash
# From project root
mkdir -p app/src/main/assets/product
cp docs/product/*.md app/src/main/assets/product/
```

**Files to copy:**
- `product-overview.md`
- `faq.md`
- `troubleshooting.md`
- `api-documentation.md`
- `authorization-guide.md`
- `user-guide.md`

### 2. Start CRM MCP Server

**Option A: Run via Gradle**
```bash
./gradlew :console-agent:runCrmMcpServer
```

**Option B: Set up HTTP Bridge** (for production)
- See `docs/TESTING_CRM_MCP_SERVER.md`
- Configure `CRM_MCP_BRIDGE_URL` in `Config.kt`

### 3. Configure CRM Bridge URL

In `app/src/main/java/com/example/aichat/data/Config.kt`:

```kotlin
const val CRM_MCP_BRIDGE_URL = "http://10.0.2.2:8003"  // Emulator
// OR
const val CRM_MCP_BRIDGE_URL = "http://YOUR_IP:8003"  // Physical device
```

### 4. Build and Run App

```bash
./gradlew build
# Run in Android Studio or via ADB
```

## Testing the Integration

### Test Scenario 1: Basic Support Mode

1. **Launch app** → Login/Register
2. **Enable Support Mode** → Tap 💬 icon in chat header
3. **Ask question**: "Why isn't authorization working?"
4. **Expected**: Response references:
   - Relevant documentation (from RAG)
   - Your previous tickets (from CRM)
   - Personalized answer

### Test Scenario 2: Ticket Context

1. **Login as user1** (has authorization ticket)
2. **Enable Support Mode**
3. **Ask**: "I'm having login issues"
4. **Expected**: Response mentions ticket #ticket1 about authorization

### Test Scenario 3: Documentation Search

1. **Enable Support Mode**
2. **Ask**: "How do I configure the API?"
3. **Expected**: Response includes information from `api-documentation.md`

## Verification Checklist

- [ ] Documentation files copied to `app/src/main/assets/product/`
- [ ] CRM MCP server running (or HTTP bridge configured)
- [ ] `CRM_MCP_BRIDGE_URL` configured in `Config.kt`
- [ ] App builds successfully
- [ ] User logged in (has userId)
- [ ] Support mode toggle works (💬 icon)
- [ ] RAG context appears in responses (check logs)
- [ ] Ticket context appears in responses (check logs)

## Debugging

### Check Logs

```bash
# View all support-related logs
adb logcat | grep -E "SupportContextBuilder|RagService|CrmMcpClient"

# Check CRM connection
adb logcat | grep "CRM MCP Server Initialization"

# Check RAG initialization
adb logcat | grep "RAG Documentation Initialization"
```

### Common Issues

**1. No RAG context in responses**
- Check: `adb logcat | grep RagService`
- Verify: Documentation files in `app/src/main/assets/product/`
- Solution: Copy files manually if needed

**2. No ticket context in responses**
- Check: `adb logcat | grep CrmMcpClient`
- Verify: CRM server is running
- Verify: User is logged in (has userId)
- Verify: User has tickets in `crm_data.json`

**3. CRM connection fails**
- Check: `CRM_MCP_BRIDGE_URL` is correct
- Check: Server is accessible from emulator/device
- Check: Firewall allows connections
- Solution: Use HTTP bridge or check network settings

**4. Support mode not working**
- Check: Support mode is enabled (🛟 icon visible)
- Check: `supportContextBuilder` is not null in ChatViewModel
- Check: User is authenticated

## Example Response

**User Question**: "Why isn't authorization working?"

**Enhanced Prompt** (what LLM receives):
```
Why isn't authorization working?

[Support Context]
=== Product Documentation ===
--- Authorization Guide ---
Common authorization issues include:
- Invalid API tokens
- Expired IAM tokens (YandexGPT)
- Missing authentication headers
...

=== User's Previous Support Tickets ===
Ticket #ticket1: Authorization not working
Status: open | Priority: high
Description: I cannot log in to the application. Getting 401 errors.
Recent messages:
user: Why isn't authorization working? I keep getting 401 errors when trying to log in.
support: We're investigating the issue. Can you check if your API token is still valid?

Please use the provided context to answer the user's question...
```

**LLM Response**:
"Based on your ticket #ticket1 and our documentation, the authorization issue you're experiencing is likely due to an expired or invalid API token. Here's what to check:
1. Verify your API token is still valid
2. Check if your YandexGPT IAM token has expired (they expire after 12 hours)
3. Ensure you're including the Authorization header correctly
..."

## Architecture

```
┌─────────────────┐
│   ChatScreen    │
│  (UI Layer)     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  ChatViewModel  │
│  (Support Mode) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────────┐
│SupportContext   │─────▶│   RagService     │
│    Builder       │      │  (Documentation)│
└────────┬────────┘      └──────────────────┘
         │
         │               ┌──────────────────┐
         └───────────────▶│  CrmMcpClient    │
                          │  (User Tickets)  │
                          └──────────────────┘
```

## Next Steps (Optional Enhancements)

1. **Upgrade RAG**: Replace text matching with semantic search
2. **RAG API**: Create API endpoint for RAG pipeline
3. **Ticket Creation**: Allow creating tickets from chat
4. **Analytics**: Track which docs/tickets are most helpful
5. **Caching**: Cache RAG and ticket results

## Summary

✅ **RAG Integration**: Documentation search working
✅ **CRM Integration**: Ticket retrieval working  
✅ **Context Building**: SupportContextBuilder combines both
✅ **UI Integration**: Support mode toggle in chat
✅ **Error Handling**: Graceful fallbacks if services unavailable

The support service is **fully functional**! 🎉
