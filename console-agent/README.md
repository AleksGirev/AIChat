# Mobile Device Control Console Agent

A Kotlin console application that allows you to control Android/iOS devices using natural language commands through an LLM and the Mobile MCP server.

## Features

- **Natural Language Control**: Issue commands in plain language (e.g., "Launch the Settings app and turn on Wi-Fi")
- **MCP Integration**: Connects to `@mobilenext/mobile-mcp` server via stdin/stdout
- **LLM-Powered**: Uses OpenAI-compatible APIs (GPT-4o, Claude, or local models) to understand and execute commands
- **Interactive Console**: Simple command-line interface with `> ` prompt
- **Multi-step Execution**: Supports complex commands requiring multiple tool calls

## Prerequisites

1. **Node.js** (for running the MCP server)
   ```bash
   node --version  # Should be v18 or later
   ```

2. **YandexGPT API Credentials**
   - Set the `YANDEX_IAM_TOKEN` environment variable (IAM token)
   - Set the `YANDEX_FOLDER_ID` environment variable (Folder ID)
   - Get your IAM token from: https://cloud.yandex.ru/docs/iam/operations/iam-token/create
   - Get your folder ID from: https://cloud.yandex.ru/docs/resource-manager/operations/folder/get-id

3. **Connected Device** (Android/iOS device or emulator)
   - For Android: Enable USB debugging or use an emulator
   - For iOS: Use a simulator or connected device

## Building

From the project root:

```bash
./gradlew :console-agent:build
```

## Running

### 1. Set your YandexGPT credentials

```bash
export YANDEX_IAM_TOKEN=your-iam-token-here
export YANDEX_FOLDER_ID=your-folder-id-here
```

**Getting your credentials:**
- **IAM Token**: Visit https://cloud.yandex.ru/docs/iam/operations/iam-token/create
- **Folder ID**: Visit https://cloud.yandex.ru/docs/resource-manager/operations/folder/get-id

### 2. Ensure a device is connected

- For Android: `adb devices` should show your device
- For iOS: Xcode should detect your simulator/device

### 3. Run the application

```bash
./gradlew :console-agent:run
```

Or run the JAR directly:

```bash
./gradlew :console-agent:installDist
./console-agent/build/install/console-agent/bin/console-agent
```

## Usage

Once started, you'll see:

```
=== Mobile Device Control Agent ===

[Agent]: Инициализация...
[MCP]: Starting MCP server: npx -y @mobilenext/mobile-mcp@latest
[MCP]: Connected successfully
[MCP]: Initialized successfully
[Agent]: Получаю инструменты от MCP...
[MCP]: Found X tools
[MCP]: Доступны инструменты: launchApp, tapElement, typeText, ...
[Agent]: Готов к работе!

>
```

### Example Commands

```
> Открой приложение Gmail
> Launch Settings and turn on Wi-Fi
> Take a screenshot
> Tap on the search button
> Type "Hello World" in the text field
> exit
```

### Exiting

Type `exit` or press `Ctrl+C` to gracefully shut down the application.

## Architecture

```
┌─────────────┐
│   User      │
│  Input      │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Agent     │────▶│  LLM Client  │────▶│  OpenAI API │
│Orchestrator │     │  (OpenAiClient)│     │             │
└──────┬──────┘     └──────────────┘     └─────────────┘
       │
       │ Tool Calls
       ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   MCP       │────▶│  Stdio        │────▶│  Mobile MCP │
│   Client    │     │  Transport   │     │  Server     │
└─────────────┘     └──────────────┘     └─────────────┘
```

### Components

- **Main.kt**: Entry point with interactive console loop
- **AgentOrchestrator**: Coordinates LLM and MCP interactions
- **OpenAiClient**: Handles LLM API calls and tool conversion
- **McpClient**: Manages MCP protocol communication
- **StdioMcpTransport**: Handles stdin/stdout communication with MCP server

## Configuration

### Using YandexGPT (Default)

The application is configured to use YandexGPT by default. Set the required environment variables:

```bash
export YANDEX_IAM_TOKEN=your-iam-token
export YANDEX_FOLDER_ID=your-folder-id
```

### Switching to Other OpenAI-Compatible APIs

If you want to use OpenAI or other compatible APIs, edit `Main.kt`:

```kotlin
// For OpenAI
val llmClient = OpenAiClient(
    apiKey = System.getenv("OPENAI_API_KEY") ?: "",
    baseUrl = "https://api.openai.com/v1",
    model = "gpt-4o",
    folderId = null  // Not needed for OpenAI
)

// For local models (Ollama)
val llmClient = OpenAiClient(
    apiKey = "ollama",  // Ollama doesn't require a real key
    baseUrl = "http://localhost:11434/v1",
    model = "llama3",
    folderId = null
)
```

## Troubleshooting

### MCP Server Not Starting

- Ensure Node.js is installed: `node --version`
- Check network connectivity (for downloading the MCP package)
- Try running manually: `npx -y @mobilenext/mobile-mcp@latest`

### Device Not Detected

- **Android**: Run `adb devices` to verify connection
- **iOS**: Check Xcode device manager
- Ensure USB debugging is enabled (Android)

### API Errors

- Verify `YANDEX_IAM_TOKEN` and `YANDEX_FOLDER_ID` are set correctly
- Check Yandex Cloud quota/rate limits
- Ensure your IAM token is valid (they expire after 12 hours)
- For other APIs, verify the API key is correct

### Timeout Errors

- Increase timeout in `StdioMcpTransport.sendRequest()` (currently 30 seconds)
- Check device responsiveness
- Verify MCP server is responding

## Development

### Project Structure

```
console-agent/
├── build.gradle.kts
├── src/main/kotlin/com/example/aichat/console/
│   ├── Main.kt                    # Entry point
│   ├── agent/
│   │   └── AgentOrchestrator.kt   # Orchestration logic
│   ├── llm/
│   │   └── OpenAiClient.kt        # LLM API client
│   ├── mcp/
│   │   ├── McpClient.kt           # MCP protocol client
│   │   └── StdioMcpTransport.kt   # stdio transport
│   ├── model/
│   │   ├── McpModels.kt           # MCP data models
│   │   ├── OpenAiModels.kt        # OpenAI data models
│   │   └── JsonElementSerializer.kt
│   └── util/
│       └── JsonUtils.kt           # JSON utilities
└── README.md
```

### Adding New Features

1. **New MCP Tools**: Automatically discovered from the MCP server
2. **Custom Prompts**: Modify `AgentOrchestrator.createSystemMessage()`
3. **Error Handling**: Enhance error messages in each component

## License

Part of the AIChat project.
