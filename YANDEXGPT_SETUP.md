# YandexGPT Setup Guide

This guide explains how to configure the changelog automation to use YandexGPT instead of OpenAI.

## Prerequisites

1. Yandex Cloud account ([console.cloud.yandex.ru](https://console.cloud.yandex.ru/))
2. A folder in Yandex Cloud with Foundation Models API enabled
3. Service account with appropriate permissions

## Step-by-Step Setup

### 1. Create Service Account

1. Go to [Yandex Cloud Console](https://console.cloud.yandex.ru/)
2. Navigate to **IAM → Service accounts**
3. Click **Create service account**
4. Name it (e.g., `changelog-automation`)
5. Assign role: `ai.languageModels.user` (or `editor` for broader access)
6. Click **Create**

### 2. Get Folder ID

1. In Yandex Cloud Console, select your folder
2. The folder ID is visible in:
   - URL: `https://console.cloud.yandex.ru/folders/b1gxxx...`
   - Folder settings page
3. Copy the folder ID (format: `b1gxxx...`)

### 3. Generate IAM Token

**Option A: Using Yandex Cloud CLI**

```bash
# Install YC CLI (if not installed)
curl -sSL https://storage.yandexcloud.net/yandexcloud-yc/install.sh | bash

# Initialize
yc init

# Create IAM token
yc iam create-token
```

**Option B: Using Yandex Cloud Console**

1. Go to **IAM → Service accounts**
2. Select your service account
3. Go to **Access keys** tab
4. Click **Create new key → IAM token**
5. Copy the token (starts with `t1.`)

### 4. Configure GitHub Secrets

1. Go to your GitHub repository
2. Navigate to **Settings → Secrets and variables → Actions**
3. Add the following secrets:

| Secret Name | Value | Description |
|------------|-------|-------------|
| `LLM_API_KEY` | `t1.xxx...` | IAM token from step 3 |
| `LLM_FOLDER_ID` | `b1gxxx...` | Folder ID from step 2 |

### 5. Configure GitHub Variables (Optional)

1. Go to **Settings → Secrets and variables → Actions → Variables**
2. Add variable:

| Variable Name | Value |
|---------------|-------|
| `LLM_PROVIDER` | `yandexgpt` |

**Note:** `yandexgpt` is the default, so this step is optional.

## Testing

### Test Locally

```bash
# Set environment variables
export LLM_API_KEY="t1.your-iam-token"
export LLM_FOLDER_ID="b1gxxx-your-folder-id"

# Run the script
bash scripts/generate-changelog-entry.sh \
  --commit-hash "test123" \
  --commit-message "[TKT-123] Fix notification crash" \
  --llm-api-key "$LLM_API_KEY" \
  --llm-provider "yandexgpt" \
  --llm-folder-id "$LLM_FOLDER_ID" \
  --kb-url "https://httpbin.org/post"
```

### Test in GitHub Actions

1. Push a commit to `main` or `master` branch
2. Go to **Actions** tab
3. Check the workflow run logs
4. Verify that YandexGPT API is called successfully

## Troubleshooting

### Error: "LLM_FOLDER_ID not provided"

**Solution:** Make sure `LLM_FOLDER_ID` secret is set in GitHub repository settings.

### Error: "401 Unauthorized"

**Solutions:**
- Verify IAM token is correct and not expired
- Check service account has `ai.languageModels.user` role
- Regenerate IAM token if needed

### Error: "403 Forbidden"

**Solutions:**
- Verify folder ID is correct
- Check Foundation Models API is enabled for the folder
- Ensure service account has access to the folder

### Error: "Model not found"

**Solutions:**
- Verify folder ID format: should be `b1gxxx...`
- Check that Foundation Models API is enabled
- Try using `yandexgpt/latest` model (default)

## API Endpoints

The script uses the following YandexGPT endpoint:

```
POST https://llm.api.cloud.yandex.net/foundationModels/v1/completion
```

With the following request format:

```json
{
  "modelUri": "gpt://<folder_id>/yandexgpt/latest",
  "completionOptions": {
    "stream": false,
    "temperature": 0.3,
    "maxTokens": 150
  },
  "messages": [
    {
      "role": "system",
      "text": "You are a technical writer..."
    },
    {
      "role": "user",
      "text": "Analyze the following git commit..."
    }
  ]
}
```

## Cost Considerations

- YandexGPT pricing: Check [Yandex Cloud pricing](https://yandex.cloud/prices)
- Each changelog generation uses ~150 tokens
- Consider rate limits and quotas in your Yandex Cloud plan

## Switching Back to OpenAI

If you want to use OpenAI instead:

1. Set `LLM_PROVIDER=openai` variable
2. Update `LLM_API_KEY` secret with OpenAI API key
3. Remove or leave `LLM_FOLDER_ID` (not used for OpenAI)

## Additional Resources

- [YandexGPT Documentation](https://yandex.cloud/en/docs/foundation-models/)
- [Yandex Cloud IAM](https://yandex.cloud/en/docs/iam/)
- [Foundation Models API Reference](https://yandex.cloud/en/docs/foundation-models/api-ref/)
