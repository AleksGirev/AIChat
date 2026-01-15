# Setup Guide: Running AIChat with Support Service

This guide will help you set up and run the AIChat Android application with the new support service features.

## Prerequisites

### Required Software

1. **Android Studio** (latest version recommended)
   - Download from: https://developer.android.com/studio
   - Includes Android SDK, Gradle, and emulator

2. **Java Development Kit (JDK) 17+**
   - Android Studio usually includes JDK, but verify: `java -version`

3. **Android Device or Emulator**
   - Physical device: Enable USB debugging
   - Emulator: Create via Android Studio AVD Manager (API 24+)

4. **Git** (for cloning the repository)

### Optional (for CRM MCP Server)

- **Java Runtime** (for running CRM MCP server standalone)
- **Python 3.x** (if you want to create an HTTP bridge server)

## Step-by-Step Setup

### 1. Clone and Open Project

```bash
# Clone the repository (if not already done)
cd /path/to/your/projects
git clone <repository-url> AIChat
cd AIChat

# Open in Android Studio
# File → Open → Select AIChat folder
```

### 2. Configure API Keys

Edit `app/src/main/java/com/example/aichat/data/Config.kt`:

```kotlin
object Config {
    // OpenRouter API key (for amazon/nova-2-lite-v1:free)
    const val OPENAI_API_KEY = "your-openrouter-api-key"
    
    // YandexGPT API configuration
    const val YANDEX_IAM_TOKEN = "your-yandex-iam-token"
    const val YANDEX_FOLDER_ID = "your-yandex-folder-id"
    
    // Add CRM MCP Bridge URL (NEW)
    const val CRM_MCP_BRIDGE_URL = "http://10.0.2.2:8003"  // For emulator
    // For physical device, use your computer's IP:
    // const val CRM_MCP_BRIDGE_URL = "http://192.168.1.100:8003"
}
```

**How to get API keys:**

- **OpenRouter**: Sign up at https://openrouter.ai/keys
- **YandexGPT**: 
  - Get IAM token: https://cloud.yandex.ru/docs/iam/operations/iam-token/create
  - Get folder ID: https://cloud.yandex.ru/docs/resource-manager/operations/folder/get-id

### 3. Update Config.kt with CRM URL

Add the CRM MCP bridge URL to `Config.kt`:

```kotlin
// Add this after BRIGHTDATA_API_KEY
const val CRM_MCP_BRIDGE_URL = "http://10.0.2.2:8003"
```

Then update `CrmMcpClient` initialization in `AppModule.kt`:

```kotlin
single {
    CrmMcpClient(
        gson = get(),
        httpClient = get(),
        useRestBridge = true,
        bridgeUrl = Config.CRM_MCP_BRIDGE_URL  // Use Config value
    )
}
```

### 4. Set Up CRM MCP Server

The CRM MCP server needs to run as a separate process. You have two options:

#### Option A: Run CRM MCP Server Standalone (Recommended for Testing)

1. **Build the console-agent module:**
   ```bash
   ./gradlew :console-agent:build
   ```

2. **Run the CRM MCP server:**
   ```bash
   # From project root
   cd console-agent
   java -cp build/libs/console-agent-1.0.0.jar com.example.aichat.console.mcp.crm.CrmMcpServerMainKt
   ```

   Or add a Gradle task (see Step 5).

#### Option B: Set Up HTTP Bridge Server (For Android)

Since Android can't run stdio processes directly, create an HTTP bridge:

1. **Create a simple Python HTTP bridge** (`crm_mcp_bridge.py`):

```python
#!/usr/bin/env python3
import subprocess
import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import threading

class MCPBridgeHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok"}).encode())
        elif self.path == '/tools':
            # Return CRM tools list
            tools = [
                {"name": "crm.getUserTickets", "description": "Get user tickets"},
                {"name": "crm.getTicket", "description": "Get ticket by ID"},
                {"name": "crm.getUserInfo", "description": "Get user info"},
                {"name": "crm.searchTickets", "description": "Search tickets"}
            ]
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"tools": tools}).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_POST(self):
        # Parse tool name from path: /tools/{toolName}
        path_parts = self.path.strip('/').split('/')
        if len(path_parts) == 2 and path_parts[0] == 'tools':
            tool_name = path_parts[1]
            # Read request body
            content_length = int(self.headers['Content-Length'])
            body = self.rfile.read(content_length)
            data = json.loads(body.decode())
            arguments = data.get('arguments', {})
            
            # Call CRM MCP server via stdio
            # This is a simplified version - you'd need to implement JSON-RPC
            result = self.call_mcp_tool(tool_name, arguments)
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(result).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def call_mcp_tool(self, tool_name, arguments):
        # Simplified - implement actual MCP JSON-RPC call
        return {"content": [{"type": "text", "text": "[]"}]}

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8003
    server = HTTPServer(('0.0.0.0', port), MCPBridgeHandler)
    print(f"CRM MCP Bridge Server running on port {port}")
    server.serve_forever()
```

2. **Run the bridge server:**
   ```bash
   python3 crm_mcp_bridge.py 8003
   ```

### 5. Add Gradle Task for CRM MCP Server

Add to `console-agent/build.gradle.kts`:

```kotlin
// Task for running the CRM MCP Server
tasks.register<JavaExec>("runCrmMcpServer") {
    group = "application"
    description = "Run the CRM MCP Server (for support tickets)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.aichat.console.mcp.crm.CrmMcpServerMainKt")
    standardInput = System.`in`
    standardOutput = System.out
}
```

Then run:
```bash
./gradlew :console-agent:runCrmMcpServer
```

### 6. Initialize CRM Client in Application

Update `app/src/main/java/com/example/aichat/AIChatApplication.kt`:

```kotlin
import com.example.aichat.data.crm.CrmMcpClient

private fun initializeCrmMcp() {
    applicationScope.launch {
        try {
            val crmClient: CrmMcpClient = GlobalContext.get().get()
            val initResult = crmClient.initialize()
            if (initResult.isSuccess) {
                Log.d(TAG, "CRM MCP client initialized successfully")
            } else {
                Log.w(TAG, "CRM MCP client initialization failed: ${initResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "CRM MCP client not available: ${e.message}")
        }
    }
}

override fun onCreate() {
    super.onCreate()
    initializeKoin()
    initializeBrightDataMcp()
    initializeCrmMcp()  // Add this
}
```

### 7. Build and Run Android App

1. **Sync Gradle:**
   - Android Studio: Click "Sync Now" when prompted
   - Or: `./gradlew build`

2. **Run the app:**
   - Click the green "Run" button in Android Studio
   - Or: `./gradlew installDebug` (then launch app manually)

3. **First Launch:**
   - You'll see the authentication screen
   - Register a new user (or use existing mock users)
   - Default password for MVP: `password123`

### 8. Test Support Service

1. **Login** with a registered user
2. **Enable Support Mode** (tap the 💬/🛟 icon in chat header)
3. **Ask a question** like: "Why isn't authorization working?"
4. **Check the response** - it should reference your previous tickets

## Mock Users and Tickets

The CRM system comes with mock data in `console-agent/data/crm_data.json`:

**Users:**
- `user1@example.com` / `user1` (password: `password123`)
- `user2@example.com` / `user2` (password: `password123`)
- `user3@example.com` / `user3` (password: `password123`)

**Tickets:**
- User1 has 3 tickets (authorization, API rate limit, dark mode feature)
- User2 has 2 tickets (documentation, bug report)
- User3 has 1 ticket (RAG search issue)

## Troubleshooting

### Database Migration Issues

If you see database errors:
- The app uses `fallbackToDestructiveMigration()` for development
- This will recreate the database on schema changes
- **Warning**: This deletes all existing data!

### CRM MCP Server Not Connecting

1. **Check if server is running:**
   ```bash
   # Test health endpoint
   curl http://localhost:8003/health
   ```

2. **Check Android emulator network:**
   - Emulator uses `10.0.2.2` to access host `localhost`
   - Physical device needs your computer's IP address

3. **Check logs:**
   ```bash
   adb logcat | grep CrmMcpClient
   ```

### Authentication Not Working

1. **Check EncryptedSharedPreferences:**
   - Clear app data: Settings → Apps → AIChat → Clear Data
   - Re-register users

2. **Verify user exists:**
   - Check Room database via Android Studio Database Inspector
   - Or use ADB: `adb shell run-as com.example.aichat`

### Support Mode Not Showing Context

1. **Verify CRM client is initialized:**
   - Check logs: `adb logcat | grep SupportContextBuilder`
   
2. **Check user ID:**
   - Ensure user is logged in (check `AuthManager`)

3. **Verify tickets exist:**
   - Check `console-agent/data/crm_data.json` exists
   - Verify user ID matches between app and CRM data

## Development Tips

### Viewing Database

Use Android Studio's **Database Inspector**:
1. View → Tool Windows → App Inspection
2. Select running app
3. Navigate to `chat_database` → `users` table

### Testing CRM MCP Server Standalone

```bash
# Start server
./gradlew :console-agent:runCrmMcpServer

# In another terminal, test with JSON-RPC:
echo '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05"}}' | \
  java -cp console-agent/build/libs/console-agent-1.0.0.jar com.example.aichat.console.mcp.crm.CrmMcpServerMainKt
```

### Network Configuration

**For Android Emulator:**
- Use `http://10.0.2.2:PORT` to access host machine

**For Physical Device:**
- Find your computer's IP:
  - macOS: `ipconfig getifaddr en0`
  - Linux: `hostname -I | awk '{print $1}'`
  - Windows: `ipconfig | findstr IPv4`
- Use `http://YOUR_IP:PORT` in Config.kt
- Ensure firewall allows connections

## Next Steps

1. **RAG Integration** (Optional):
   - Set up RAG pipeline to index product documentation
   - Connect RAG API to `SupportContextBuilder`

2. **Production Setup**:
   - Move API keys to `local.properties` or BuildConfig
   - Set up proper CRM backend (replace JSON file)
   - Implement proper password hashing

3. **Testing**:
   - Write unit tests for repositories
   - Write integration tests for support flow
   - Test with multiple users and tickets

## Quick Start Checklist

- [ ] Android Studio installed
- [ ] Project opened and Gradle synced
- [ ] API keys configured in `Config.kt`
- [ ] CRM MCP bridge URL added to `Config.kt`
- [ ] CRM client initialization added to `AIChatApplication.kt`
- [ ] CRM MCP server running (standalone or bridge)
- [ ] Android emulator/device connected
- [ ] App builds and runs
- [ ] User registered/logged in
- [ ] Support mode tested

## Support

If you encounter issues:
1. Check logs: `adb logcat | grep -E "AIChat|CrmMcp|Support"`
2. Verify all configuration steps completed
3. Check network connectivity
4. Review error messages in Android Studio Logcat

Good luck! 🚀
