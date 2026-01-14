# AI PR Review Setup Guide

## Quick Start

### 1. Build RAG Index

```bash
# Index project documentation
./gradlew :console-agent:runRag -Prag.args="index docs/ README.md"

# Verify index was created
./gradlew :console-agent:runRag -Prag.args="stats"
```

### 2. Configure Secrets (GitHub Actions)

Go to your repository → Settings → Secrets and variables → Actions, and add:

**Required:**
- `LLM_API_KEY` - Your LLM API key (OpenAI, YandexGPT, etc.)

**Optional:**
- `LLM_BASE_URL` - Default: `https://api.openai.com/v1`
- `LLM_MODEL` - Default: `gpt-4o`
- `YANDEX_FOLDER_ID` - For YandexGPT
- `OLLAMA_BASE_URL` - Default: `http://localhost:11434`
- `OLLAMA_MODEL` - Default: `nomic-embed-text`

**Note:** `GITHUB_TOKEN` is automatically provided by GitHub Actions.

### 3. Test Locally

```bash
# Set environment variables
export GITHUB_TOKEN=ghp_...
export LLM_API_KEY=sk-...

# Run review for a PR
./gradlew :console-agent:runPrReview -Pprreview.args="123 owner-name repo-name"
```

### 4. Enable GitHub Actions

The workflow (`.github/workflows/ai-review.yml`) will automatically run on:
- PR opened
- PR reopened
- PR synchronized (new commits)

No additional configuration needed!

## Architecture

### Components

1. **GitHubApiClient** - Fetches PR data and posts comments
2. **RagContextService** - Retrieves relevant documentation using RAG
3. **ReviewPromptBuilder** - Constructs structured LLM prompts
4. **AiPrReview** - Main orchestrator

### Data Flow

```
PR Event → GitHub Actions
    ↓
Fetch PR Data (GitHub API)
    ↓
Retrieve RAG Context (Vector Search)
    ↓
Generate Review (LLM)
    ↓
Post Comment (GitHub API)
```

## Review Criteria

The system reviews code against:

1. **Architecture** - MVVM/MVI compliance, layer separation
2. **Error Handling** - Result<T, E> usage, exception handling
3. **Coroutines** - suspend functions, StateFlow, scoping
4. **Compose** - Stateless composables, state hoisting
5. **Naming** - PascalCase, camelCase, UPPER_SNAKE_CASE
6. **Quality** - Readability, documentation, no hardcoded values

## Troubleshooting

### RAG Index Not Found

```bash
# Rebuild index
./gradlew :console-agent:runRag -Prag.args="index docs/ README.md"
```

### Ollama Not Available in CI

The system will continue without RAG context. Options:
1. Use remote embedding service
2. Pre-build and cache RAG index
3. Use GitHub Actions cache (already configured)

### Rate Limits

- **GitHub API**: 5,000 requests/hour (authenticated)
- **LLM API**: Varies by provider

The system detects and reports rate limits. For high-volume repos:
- Use GitHub App tokens (higher limits)
- Cache PR data
- Batch reviews

### Review Not Posted

Check:
1. GitHub token has `repo` permissions
2. PR is not from a fork (GitHub Actions limitation)
3. Review generation succeeded (check logs)
4. No duplicate comment exists

## Customization

### Adjust Review Focus

Edit `ReviewPromptBuilder.kt` to modify:
- Review criteria
- Prompt structure
- Documentation emphasis

### Change LLM Model

Set `LLM_MODEL` secret:
- `gpt-4o` (default)
- `gpt-4-turbo`
- `claude-3-opus`
- `yandexgpt` (with `YANDEX_FOLDER_ID`)

### Modify RAG Context

Edit `RagContextService.kt` to:
- Change number of chunks retrieved
- Adjust similarity threshold
- Customize query building

## Performance

- **Typical runtime**: 30-60 seconds
- **Timeout**: 5 minutes (GitHub Actions)
- **Fail-safe**: Never blocks PRs (`continue-on-error: true`)

## Security

- **No code logging**: Diffs and source code are never logged
- **Token security**: API keys stored as GitHub secrets
- **Rate limit handling**: Graceful degradation on limits
- **Error handling**: Fail-safe design

## Next Steps

1. Build RAG index from your documentation
2. Configure LLM API key in GitHub Secrets
3. Create a test PR to verify the system works
4. Monitor first few reviews for quality
5. Adjust prompts if needed

For detailed documentation, see `console-agent/src/main/kotlin/com/example/aichat/console/prreview/README.md`.
