# Quick Start: Support Service

## ✅ What's Already Set Up

1. ✅ **RAG Service** - Searches product documentation
2. ✅ **CRM MCP Client** - Retrieves user tickets  
3. ✅ **SupportContextBuilder** - Combines RAG + CRM context
4. ✅ **ChatViewModel Integration** - Uses context when support mode enabled
5. ✅ **Documentation Files** - Already in `app/src/main/assets/product/`
6. ✅ **CRM Mock Data** - Already in `console-agent/data/crm_data.json`

## 🚀 Quick Setup (3 Steps)

### Step 1: Start CRM MCP Server

```bash
# Terminal 1: Start CRM server
./gradlew :console-agent:runCrmMcpServer
```

**Note**: Server runs continuously - this is normal! It's waiting for requests.

### Step 2: Verify Configuration

Check `app/src/main/java/com/example/aichat/data/Config.kt`:

```kotlin
const val CRM_MCP_BRIDGE_URL = "http://10.0.2.2:8003"  // ✅ Already set
```

**For Physical Device**: Change to your computer's IP:
```kotlin
const val CRM_MCP_BRIDGE_URL = "http://192.168.1.100:8003"
```

### Step 3: Run Android App

1. Build and run app in Android Studio
2. Register/Login (use `user1@example.com` / `password123` for testing)
3. Tap 💬 icon to enable Support Mode
4. Ask: "Why isn't authorization working?"

## 🧪 Testing

### Test 1: Basic Support Mode

1. **Login** as user1
2. **Enable Support Mode** (💬 → 🛟)
3. **Ask**: "Why isn't authorization working?"
4. **Expected**: Response mentions:
   - Documentation about authorization
   - Your ticket #ticket1

### Test 2: Documentation Search

1. **Enable Support Mode**
2. **Ask**: "How do I configure the API?"
3. **Expected**: Response includes info from `api-documentation.md`

### Test 3: Ticket History

1. **Login** as user1 (has 3 tickets)
2. **Enable Support Mode**
3. **Ask**: "What issues have I reported?"
4. **Expected**: Response references your tickets

## 📊 How to Verify It's Working

### Check Logs

```bash
# View support service logs
adb logcat | grep -E "SupportContextBuilder|RagService|CrmMcp"

# Should see:
# - "RAG context found: XXX chars"
# - "CRM MCP server connected successfully"
# - "Support context built with X tickets"
```

### Check in App

1. **Enable Support Mode** → Icon changes 💬 → 🛟
2. **Ask question** → Response should reference docs/tickets
3. **Check response quality** → Should be more contextual

## 🔧 Troubleshooting

### No RAG Context

**Symptoms**: Responses don't mention documentation

**Check**:
```bash
adb logcat | grep RagService
```

**Fix**:
- Verify files in `app/src/main/assets/product/`
- Check logs for "Documentation initialized"

### No Ticket Context

**Symptoms**: Responses don't mention previous tickets

**Check**:
```bash
adb logcat | grep CrmMcpClient
```

**Fix**:
- Verify CRM server is running
- Check `CRM_MCP_BRIDGE_URL` is correct
- Verify user is logged in (has userId)
- Check user has tickets in `crm_data.json`

### CRM Server Not Connecting

**Symptoms**: Logs show "Failed to connect to CRM MCP server"

**Fix**:
1. Verify server is running: `./gradlew :console-agent:runCrmMcpServer`
2. Check URL: `http://10.0.2.2:8003` (emulator) or your IP (device)
3. Test connection: `curl http://localhost:8003/health` (on host machine)

## 📝 Example Flow

**User**: "Why isn't authorization working?"

**System**:
1. Detects support mode is enabled
2. Calls `SupportContextBuilder.buildSupportContext()`
3. Searches RAG: Finds `authorization-guide.md` sections
4. Searches CRM: Finds ticket #ticket1 about authorization
5. Builds context:
   ```
   [Support Context]
   === Product Documentation ===
   Authorization issues: Check API tokens, IAM tokens expire...
   
   === User's Previous Support Tickets ===
   Ticket #ticket1: Authorization not working
   Status: open | Priority: high
   ...
   ```
6. Enhances prompt with context
7. LLM responds: "Based on your ticket #ticket1 and our documentation..."

## 🎯 Success Criteria

✅ Support mode toggle works
✅ RAG finds relevant documentation
✅ CRM retrieves user tickets
✅ Responses reference docs and tickets
✅ Personalized answers based on history

## 📚 Files Reference

- **RAG Service**: `app/src/main/java/com/example/aichat/data/rag/RagService.kt`
- **CRM Client**: `app/src/main/java/com/example/aichat/data/crm/CrmMcpClient.kt`
- **Context Builder**: `app/src/main/java/com/example/aichat/data/support/SupportContextBuilder.kt`
- **Chat ViewModel**: `app/src/main/java/com/example/aichat/ui/viewmodel/ChatViewModel.kt`
- **Documentation**: `app/src/main/assets/product/*.md`
- **CRM Data**: `console-agent/data/crm_data.json`

Everything is ready! Just start the CRM server and test! 🚀
