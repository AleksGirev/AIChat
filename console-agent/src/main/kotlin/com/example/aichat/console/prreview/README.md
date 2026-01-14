# AI-Powered PR Review System

An automated code review system that uses RAG (Retrieval-Augmented Generation) and LLMs to provide context-aware feedback on pull requests.

## Overview

This system:
1. **Fetches PR data** from GitHub API (diff, metadata, file changes)
2. **Retrieves relevant documentation** using RAG index built from project docs
3. **Generates AI review** using LLM with structured prompts
4. **Posts review as comment** on the PR

## Architecture

```
┌─────────────────┐
│  GitHub PR      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│ GitHubApiClient │────▶│  PR Data     │────▶│  File Diffs │
└────────┬────────┘     └──────────────┘     └─────────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│ RagContextService│────▶│  RAG Index   │────▶│  Docs Chunks│
└────────┬────────┘     └──────────────┘     └─────────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│ReviewPromptBuilder│────▶│  LLM Prompt  │────▶│  OpenAiClient│
└────────┬────────┘     └──────────────┘     └─────────────┘
         │
         ▼
┌─────────────────┐
│  GitHub Comment │
└─────────────────┘
```

## Prerequisites

### 1. RAG Index

Build the RAG index from project documentation:

```bash
# Index documentation files
./gradlew :console-agent:runRag --args="index docs/ README.md"

# Verify index
./gradlew :console-agent:runRag --args="stats"
```

The index will be stored at `console-agent/rag_index.db` by default.

### 2. Embedding Service (for RAG)

If using local embeddings, ensure Ollama is running:

```bash
# Install Ollama: https://ollama.ai
ollama pull nomic-embed-text
ollama serve
```

Or use a remote embedding service by setting `OLLAMA_BASE_URL`.

### 3. LLM API Access

Configure your LLM API credentials:

**For OpenAI:**
```bash
export LLM_API_KEY=sk-...
export LLM_BASE_URL=https://api.openai.com/v1
export LLM_MODEL=gpt-4o
```

**For YandexGPT:**
```bash
export LLM_API_KEY=<IAM_TOKEN>
export LLM_BASE_URL=https://llm.api.cloud.yandex.net/foundationModels/v1
export LLM_MODEL=yandexgpt
export YANDEX_FOLDER_ID=<FOLDER_ID>
```

**For Local Ollama:**
```bash
export LLM_API_KEY=ollama  # Not used, but required
export LLM_BASE_URL=http://localhost:11434/v1
export LLM_MODEL=llama3
```

### 4. GitHub Token

Create a GitHub personal access token with `repo` permissions:

```bash
export GITHUB_TOKEN=ghp_...
```

## Usage

### Local Testing

```bash
# Set environment variables
export GITHUB_TOKEN=ghp_...
export LLM_API_KEY=sk-...

# Run review for PR #123
./gradlew :console-agent:runPrReview -Pprreview.args="123 owner-name repo-name"
```

### GitHub Actions

The system is integrated with GitHub Actions. See `.github/workflows/ai-review.yml`.

**Required Secrets:**
- `GITHUB_TOKEN` - Automatically provided by GitHub Actions
- `LLM_API_KEY` - Your LLM API key

**Optional Secrets:**
- `LLM_BASE_URL` - LLM API base URL (default: `https://api.openai.com/v1`)
- `LLM_MODEL` - Model name (default: `gpt-4o`)
- `YANDEX_FOLDER_ID` - For YandexGPT
- `OLLAMA_BASE_URL` - Ollama base URL (default: `http://localhost:11434`)
- `OLLAMA_MODEL` - Embedding model (default: `nomic-embed-text`)

## How It Works

### 1. PR Data Ingestion

- Fetches PR metadata (title, description, branches)
- Downloads full diff using GitHub API
- Parses diff to extract per-file changes

### 2. RAG Context Retrieval

For each changed file:
- Builds semantic query from file path and structure
- Searches RAG index for top-3 relevant documentation chunks
- Retrieves general project context (architecture, style guide)

### 3. LLM Review Generation

Constructs structured prompt with:
- System prompt defining review criteria
- PR metadata and description
- Relevant documentation excerpts
- Full diff for each changed file

LLM analyzes code against:
- Architecture compliance (MVVM/MVI)
- Error handling patterns
- Coroutines best practices
- Jetpack Compose guidelines
- Naming conventions
- Code quality standards

### 4. Comment Posting

- Formats review with bot marker
- Checks for existing comments (avoids duplicates)
- Posts as PR comment via GitHub API
- Handles rate limits gracefully

## Review Focus Areas

The system reviews code for:

1. **Architecture Compliance**
   - MVVM/MVI pattern adherence
   - Layer separation
   - Dependency injection

2. **Error Handling**
   - `Result<T, E>` usage
   - Exception handling
   - Meaningful error messages

3. **Coroutines**
   - `suspend` functions
   - `StateFlow` usage
   - Proper scoping

4. **Jetpack Compose**
   - Stateless composables
   - State hoisting
   - Efficient recomposition

5. **Naming Conventions**
   - PascalCase for classes
   - camelCase for functions/variables
   - UPPER_SNAKE_CASE for constants

6. **Code Quality**
   - Readability
   - Documentation
   - No hardcoded values

## Error Handling

The system is designed to be **fail-safe**:

- **RAG index missing**: Continues without documentation context
- **API failures**: Logs error but doesn't block PR
- **Rate limits**: Detects and reports rate limit status
- **Empty diffs**: Skips review gracefully
- **Duplicate comments**: Detects and skips posting

In GitHub Actions, the workflow uses `continue-on-error: true` to never block PRs.

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GITHUB_TOKEN` | Yes | - | GitHub personal access token |
| `LLM_API_KEY` | Yes | - | LLM API key (or `OPENAI_API_KEY`) |
| `LLM_BASE_URL` | No | `https://api.openai.com/v1` | LLM API base URL |
| `LLM_MODEL` | No | `gpt-4o` | LLM model name |
| `YANDEX_FOLDER_ID` | No | - | YandexGPT folder ID |
| `OLLAMA_BASE_URL` | No | `http://localhost:11434` | Ollama base URL |
| `OLLAMA_MODEL` | No | `nomic-embed-text` | Embedding model |
| `RAG_DB_PATH` | No | Auto-detected | Path to RAG index database |

## Troubleshooting

### RAG Index Not Found

```bash
# Build the index
./gradlew :console-agent:runRag --args="index docs/ README.md"
```

### Ollama Not Available

The system will continue without RAG context. For CI/CD, consider:
- Using a remote embedding service
- Pre-building and caching the RAG index
- Using a service that provides embeddings

### Rate Limit Errors

GitHub API has rate limits:
- Authenticated: 5,000 requests/hour
- Unauthenticated: 60 requests/hour

The system detects rate limits and reports reset time. For high-volume repos, consider:
- Using GitHub App tokens (higher limits)
- Caching PR data
- Batching reviews

### LLM API Errors

Check:
- API key is valid
- Base URL is correct
- Model name is available
- Quota/rate limits not exceeded

## Limitations

1. **RAG Index**: Must be built and maintained separately
2. **Embedding Service**: Requires Ollama or remote service
3. **Token Limits**: Large PRs may exceed LLM context window
4. **Rate Limits**: GitHub API and LLM API have rate limits
5. **Cost**: LLM API calls incur costs

## Future Improvements

- [ ] Support for updating existing comments
- [ ] Batch processing for multiple PRs
- [ ] Custom review criteria per repository
- [ ] Integration with PR status checks
- [ ] Review summary statistics
- [ ] Support for different LLM providers
- [ ] Caching of RAG queries
- [ ] Incremental RAG index updates

## License

Part of the AIChat project.
