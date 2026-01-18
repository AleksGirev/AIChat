# Changelog Automation Pipeline

This document describes the automated changelog generation and knowledge base integration system that runs on every push to the `main` or `master` branch.

## Overview

The pipeline automatically:
1. Analyzes the latest commit using an LLM (OpenAI-compatible API)
2. Extracts app version from `app/build.gradle.kts`
3. Parses ticket ID from commit message using a configurable pattern
4. Generates a structured JSON changelog entry
5. Sends the entry to a remote knowledge base API

## Architecture

```
GitHub Push → GitHub Actions → Kotlin Script → LLM API → Knowledge Base API
```

### Components

- **`.github/workflows/update-changelog.yml`**: GitHub Actions workflow definition
- **`scripts/generate-changelog-entry.kts`**: Kotlin script that orchestrates the process
- **Knowledge Base API**: Remote endpoint that receives changelog entries (configurable)

## Setup

### 1. Required GitHub Secrets

Configure the following secrets in your GitHub repository settings (`Settings → Secrets and variables → Actions`):

| Secret Name | Description | Required | Example |
|------------|-------------|----------|---------|
| `LLM_API_KEY` | LLM API key (YandexGPT IAM token or OpenAI API key) | Yes* | `t1.xxx...` (YandexGPT) or `sk-...` (OpenAI) |
| `LLM_FOLDER_ID` | Yandex Cloud folder ID (required for YandexGPT) | Yes** | `b1gxxx...` |
| `KB_API_URL` | Knowledge base API endpoint URL | No* | `https://api.example.com/changelog` |
| `KB_API_KEY` | API key for knowledge base authentication | No* | `your-api-key` |

\* If not provided, defaults to `https://httpbin.org/post` (mock endpoint for testing)  
\** Required only if using YandexGPT (set `LLM_PROVIDER=yandexgpt`)

### 2. Optional GitHub Variables

Configure repository variables (`Settings → Secrets and variables → Actions → Variables`) to customize behavior:

| Variable Name | Description | Default |
|--------------|-------------|---------|
| `LLM_PROVIDER` | LLM provider: `yandexgpt` or `openai` | `yandexgpt` |
| `COMMIT_MESSAGE_PATTERN` | Regex pattern to extract ticket ID | `\[([A-Z]+-\d+)\]` |

### 3. Commit Message Format

The script extracts ticket IDs from commit messages using a regex pattern. By default, it looks for patterns like:

```
[TKT-123] Fix notification crash on Android 14
[PROJ-456] Add dark mode support
```

**Customizing the Pattern:**

If your team uses a different format, set the `COMMIT_MESSAGE_PATTERN` variable:

- **Jira format**: `\[([A-Z]+-\d+)\]` (default)
- **GitHub issues**: `#(\d+)`
- **Custom**: `(PROJ-\d+)` (matches PROJ-123, PROJ-456, etc.)

**Pattern Requirements:**
- Must include a capture group `()` for the ticket ID
- First capture group will be used as the ticket ID
- If no match is found, `UNKNOWN` is used

## LLM Configuration

### YandexGPT Setup

1. **Get IAM Token:**
   - Go to [Yandex Cloud Console](https://console.cloud.yandex.ru/)
   - Create a service account or use existing
   - Generate IAM token for the service account
   - Add as `LLM_API_KEY` secret

2. **Get Folder ID:**
   - In Yandex Cloud Console, select your folder
   - Copy the folder ID from the URL or folder settings
   - Add as `LLM_FOLDER_ID` secret

3. **Set Provider:**
   - Set variable `LLM_PROVIDER=yandexgpt` (default)

### OpenAI Setup (Alternative)

1. **Get API Key:**
   - Sign up at https://platform.openai.com/
   - Create API key at https://platform.openai.com/api-keys
   - Add as `LLM_API_KEY` secret

2. **Set Provider:**
   - Set variable `LLM_PROVIDER=openai`

## LLM Prompt Structure

The script uses the following prompt structure when calling the LLM:

```
System: You are a technical writer specializing in changelog entries.

User: Analyze the following git commit and generate a concise, user-friendly 
task description. Focus on what changed and why it matters to end users.

Commit hash: <hash>
Commit message:
<full commit message>

Requirements:
- Maximum 80 characters
- Use present tense (e.g., "Fix notification crash" not "Fixed")
- Be specific but concise
- Focus on user-visible impact

Return ONLY the task description, no prefixes or explanations.
```

**LLM Configuration:**
- **YandexGPT**: Model `yandexgpt/latest`, Temperature `0.3`, Max tokens `150`
- **OpenAI**: Model `gpt-3.5-turbo`, Temperature `0.3`, Max tokens `150`

**Fallback Behavior:**
If the LLM API is unavailable or fails, the script uses the first line of the commit message (truncated to 100 characters) as a fallback.

## Output Format

The generated changelog entry follows this JSON structure:

```json
{
  "timestamp": "2025-01-18T14:30:00Z",
  "app_version": "1.0",
  "ticket_id": "TKT-123",
  "task_description": "Fixed notification crash on Android 14",
  "commit_hash": "a1b2c3d"
}
```

### Field Descriptions

| Field | Source | Description |
|-------|--------|-------------|
| `timestamp` | Generated | ISO 8601 timestamp of when the entry was created |
| `app_version` | `app/build.gradle.kts` | Extracted from `versionName` property |
| `ticket_id` | Commit message | Parsed using regex pattern, or `UNKNOWN` |
| `task_description` | LLM analysis | Generated description of the change |
| `commit_hash` | Git | Short commit hash (7 characters) |

## Knowledge Base Integration

### Mock Endpoint (Testing)

By default, the pipeline uses `https://httpbin.org/post` for testing. This endpoint:
- Accepts POST requests
- Returns the received JSON payload
- Requires no authentication

### Real API Integration

To integrate with your actual knowledge base:

1. **Set the API URL:**
   ```bash
   # In GitHub Secrets
   KB_API_URL=https://your-api.example.com/api/v1/changelog
   ```

2. **Configure Authentication:**
   ```bash
   # In GitHub Secrets
   KB_API_KEY=your-bearer-token-or-api-key
   ```

3. **Expected API Contract:**
   - **Method**: `POST`
   - **Content-Type**: `application/json`
   - **Headers**: `Authorization: Bearer <KB_API_KEY>` (if provided)
   - **Body**: Changelog entry JSON (see Output Format above)
   - **Response**: Any 2xx status code indicates success

### Error Handling

The pipeline is designed to be resilient:
- ✅ LLM failures → Falls back to commit message
- ✅ Knowledge base failures → Logs warning but doesn't fail the workflow
- ✅ Missing secrets → Uses defaults or skips optional steps

## Local Testing

You can test the script locally:

```bash
# Install dependencies (one-time setup)
# Note: Kotlin script dependencies are resolved automatically

# Run the script
kotlinc -script scripts/generate-changelog-entry.kts -- \
  --commit-hash "abc1234" \
  --commit-message "[TKT-123] Fix notification crash" \
  --openai-key "sk-your-key" \
  --kb-url "https://httpbin.org/post" \
  --pattern "\\[([A-Z]+-\\d+)\\]"

# Check output
cat changelog-entry.json
```

## Troubleshooting

### Workflow Fails Immediately

**Problem**: Workflow fails before generating changelog.

**Solutions**:
- Check that `OPENAI_API_KEY` secret is set
- Verify Kotlin version compatibility (script uses Kotlin 1.9.22)
- Ensure `app/build.gradle.kts` exists and contains `versionName`

### LLM Returns Generic Descriptions

**Problem**: Task descriptions are too generic or don't match commit content.

**Solutions**:
- Ensure commit messages are descriptive
- Check that `OPENAI_API_KEY` is valid and has credits
- Review LLM prompt in `scripts/generate-changelog-entry.kts` and customize if needed

### Ticket ID Always Shows "UNKNOWN"

**Problem**: Ticket IDs are not being extracted from commit messages.

**Solutions**:
- Verify commit message format matches the pattern
- Check `COMMIT_MESSAGE_PATTERN` variable in repository settings
- Test pattern locally: `echo "[TKT-123] Test" | grep -E "\[([A-Z]+-\d+)\]"`

### Knowledge Base Not Receiving Entries

**Problem**: Entries are generated but not sent to API.

**Solutions**:
- Check `KB_API_URL` secret is set correctly
- Verify API endpoint is accessible from GitHub Actions
- Review API authentication (check `KB_API_KEY` if required)
- Check workflow logs for HTTP error codes

## Security Best Practices

✅ **Implemented:**
- Secrets stored in GitHub Secrets (never hardcoded)
- No sensitive data logged (commit diffs excluded)
- API keys passed via environment variables
- Graceful degradation if services unavailable

⚠️ **Recommendations:**
- Rotate API keys regularly
- Use least-privilege API keys (read-only for OpenAI if possible)
- Monitor API usage and costs
- Consider rate limiting for knowledge base API

## Customization

### Changing the LLM Model

Edit `scripts/generate-changelog-entry.kt`:

```kotlin
val requestBody = gson.toJson(OpenAIRequest(
    model = "gpt-4",  // Change here
    messages = ...
))
```

### Adding Custom Fields

1. Update `ChangelogEntry` data class in the script
2. Add extraction logic in `main()`
3. Update knowledge base API to accept new fields

### Using a Different LLM Provider

Replace the OpenAI API call in `generateTaskDescription()` with your provider's API. Ensure the response format matches `OpenAIResponse`.

## Example Workflow Run

```
✓ Checkout repository
✓ Set up JDK 17
✓ Setup Kotlin
✓ Generate changelog entry
  App version: 1.0
  Ticket ID: TKT-123
  Task description: Fixed notification crash on Android 14
  ✓ Successfully sent changelog entry to knowledge base
✓ Display generated changelog
```

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions
2. Review `changelog-entry.json` output (if generated)
3. Verify secrets and variables are configured correctly
4. Test script locally with `kotlinc -script`
