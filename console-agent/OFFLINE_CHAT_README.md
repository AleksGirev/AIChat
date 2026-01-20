# Offline AI Chat CLI

A simple command-line interface for chatting with a local LLM (Large Language Model) running via Ollama, entirely offline.

## Features

- ✅ **100% Offline**: No internet required after model download
- ✅ **Simple REPL**: Clean, interactive chat interface
- ✅ **Lightweight**: Minimal dependencies (Ktor + Jackson)
- ✅ **Fast**: Direct communication with local Ollama instance

## Prerequisites

1. **Ollama installed and running**
   ```bash
   # Install Ollama (if not already installed)
   # macOS: brew install ollama
   # Linux: curl -fsSL https://ollama.com/install.sh | sh
   
   # Start Ollama server
   ollama serve
   ```

2. **Model downloaded**
   ```bash
   # Download the qwen2:7b-instruct model (or any other model)
   ollama pull qwen2:7b-instruct
   ```

3. **System Requirements**
   - ≥16 GB RAM recommended
   - Java 11+ (for running the Kotlin application)

## Usage

### Running with Gradle

```bash
# From the project root
./gradlew :console-agent:runOfflineChat
```

### Building and Running JAR

```bash
# Build the project
./gradlew :console-agent:build

# Run the JAR (if you create a fat JAR task)
java -jar console-agent/build/libs/console-agent-1.0.0.jar
```

### Interactive Session

Once started, you'll see:

```
🧠 Offline AI Assistant (Qwen2 7B)
==================================================
Connecting to Ollama at http://localhost:11434...
Model: qwen2:7b-instruct
Type 'exit' to quit
==================================================

✓ Connected to Ollama

You: What is Kotlin?
AI: Kotlin is a statically typed programming language...

You: exit
Goodbye! 👋
```

## Configuration

### Default Settings

- **Ollama URL**: `http://localhost:11434`
- **Model**: `qwen2:7b-instruct`
- **Streaming**: Disabled (uses `stream: false`)

### Customization

To use a different model or Ollama instance, modify `LlmClient.kt`:

```kotlin
val llmClient = LlmClient(
    baseUrl = "http://localhost:11434",  // Change if Ollama runs on different port
    model = "llama2:13b"                  // Change to any model you have
)
```

## Architecture

```
OfflineChatMain.kt
    └──> LlmClientOkHttp.kt
            └──> Ollama API (http://localhost:11434/api/generate)
```

### Components

- **OfflineChatMain.kt**: Entry point with REPL loop
- **LlmClientOkHttp.kt**: Encapsulates Ollama API communication using OkHttp and Jackson

## Troubleshooting

### "Could not connect to Ollama"

1. Ensure Ollama is running:
   ```bash
   ollama serve
   ```

2. Check if Ollama is accessible:
   ```bash
   curl http://localhost:11434/api/tags
   ```

3. Verify the model is downloaded:
   ```bash
   ollama list
   ```

### "Model not found"

Download the model first:
```bash
ollama pull qwen2:7b-instruct
```

### Slow responses

- The model runs locally on your CPU/GPU
- First response may be slower (model loading)
- Consider using a smaller model if RAM is limited

## Dependencies

- **OkHttp**: HTTP client for API calls (already in project)
- **Jackson**: JSON serialization/deserialization
- **Kotlin Coroutines**: Async/await support

Note: The implementation uses OkHttp directly instead of Ktor to avoid dependency resolution issues while maintaining the same functionality.

## License

Part of the AIChat project.
