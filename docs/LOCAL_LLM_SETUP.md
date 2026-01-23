# Local LLM Setup Guide

## Quick Start

### 1. Start Ollama Locally

```bash
# Start Ollama server (listens on all interfaces)
export OLLAMA_HOST=0.0.0.0
ollama serve

# Or for localhost only
ollama serve
```

### 2. Pull Model (if not already done)

```bash
ollama pull qwen2:7b-instruct
```

### 3. Connect via CLI

```bash
# Using console-agent command
console-agent offline

# Or directly via Gradle
./gradlew :console-agent:runOfflineChat
```

## Configuration

### Environment Variables

```bash
# Custom server URL
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2:7b-instruct

# Then run
console-agent offline
```

### Default Settings

- **Server**: `http://localhost:11434`
- **Model**: `qwen2:7b-instruct`
- **Timeout**: 300 seconds (5 minutes)

## Testing Recipe Optimization

### Baseline Test

```bash
./scripts/test-recipe-baseline.sh
```

### Optimized Test

```bash
./scripts/test-recipe-optimized.sh
```

## Usage Examples

### Basic Chat

```
console-agent offline

You: Привет!
AI: Привет! Как дела?

You: exit
```

### With Custom Server

```bash
export OLLAMA_BASE_URL=http://192.168.1.100:11434
console-agent offline
```

## Troubleshooting

### Connection Issues

```bash
# Check if Ollama is running
curl http://localhost:11434/api/version

# Check available models
curl http://localhost:11434/api/tags
```

### Timeout Issues

- Default timeout is 300 seconds
- For longer generations, increase timeout in `LlmClientOkHttp.kt`

### Model Not Found

```bash
# Pull the model
ollama pull qwen2:7b-instruct

# Verify
ollama list
```

## Advanced: Custom Generation Parameters

The `LlmClientOkHttp` class now supports:

- `system`: System prompt
- `temperature`: 0.0-1.0 (default: 0.8)
- `maxTokens`: Maximum tokens to generate
- `topP`: Top-p sampling (default: 0.9)

See `docs/LLM_OPTIMIZATION_RECIPES.md` for detailed examples.
