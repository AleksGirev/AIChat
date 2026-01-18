#!/bin/bash
# Changelog Entry Generator (Bash version)
# 
# Analyzes the latest commit using an LLM and generates a structured changelog entry
# that is sent to a remote knowledge base API.

set -euo pipefail

# Default values
COMMIT_HASH=""
COMMIT_MESSAGE=""
LLM_API_KEY=""
LLM_PROVIDER="yandexgpt"  # Options: "openai" or "yandexgpt"
LLM_FOLDER_ID=""  # Required for YandexGPT
KB_API_URL="https://httpbin.org/post"
KB_API_KEY=""
TICKET_PATTERN='\[([A-Z]+-[0-9]+)\]'

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --commit-hash)
            COMMIT_HASH="$2"
            shift 2
            ;;
        --commit-message)
            COMMIT_MESSAGE="$2"
            shift 2
            ;;
        --openai-key|--llm-api-key)
            LLM_API_KEY="$2"
            shift 2
            ;;
        --llm-provider)
            LLM_PROVIDER="$2"
            shift 2
            ;;
        --llm-folder-id)
            LLM_FOLDER_ID="$2"
            shift 2
            ;;
        --kb-url)
            KB_API_URL="$2"
            shift 2
            ;;
        --kb-key)
            KB_API_KEY="$2"
            shift 2
            ;;
        --pattern)
            TICKET_PATTERN="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$COMMIT_HASH" ]] || [[ -z "$COMMIT_MESSAGE" ]]; then
    echo "Error: --commit-hash and --commit-message are required"
    exit 1
fi

echo "Generating changelog entry for commit: $COMMIT_HASH"

# Extract version from build.gradle.kts
extract_version() {
    local build_file="app/build.gradle.kts"
    if [[ ! -f "$build_file" ]]; then
        echo "Warning: app/build.gradle.kts not found, using default version" >&2
        echo "1.0.0"
        return
    fi
    
    # Extract versionName using grep and sed - extract only the quoted value
    local version=$(grep -E "versionName\s*=\s*[\"']" "$build_file" | head -1 | sed -E "s/.*versionName[[:space:]]*=[[:space:]]*[\"']([^\"']+)[\"'].*/\\1/" | tr -d '[:space:]')
    if [[ -z "$version" ]] || [[ "$version" == *"versionName"* ]]; then
        echo "1.0.0"
    else
        echo "$version"
    fi
}

APP_VERSION=$(extract_version)
echo "App version: $APP_VERSION"

# Extract ticket ID from commit message
extract_ticket_id() {
    local msg="$1"
    local pattern="$2"
    
    # Use grep with extended regex
    if echo "$msg" | grep -qE "$pattern"; then
        echo "$msg" | grep -oE "$pattern" | head -1 | grep -oE '[A-Z]+-[0-9]+' | head -1
    else
        echo "UNKNOWN"
    fi
}

TICKET_ID=$(extract_ticket_id "$COMMIT_MESSAGE" "$TICKET_PATTERN")
echo "Ticket ID: $TICKET_ID"

# Generate task description using LLM
generate_task_description() {
    local commit_msg="$1"
    local commit_hash="$2"
    local api_key="$3"
    local provider="$4"
    local folder_id="$5"
    
    if [[ -z "$api_key" ]]; then
        echo "Warning: LLM API key not provided, using commit message as fallback" >&2
        echo "$commit_msg" | head -1 | cut -c1-100
        return
    fi
    
    local prompt="Analyze the following git commit and generate a concise, user-friendly task description. Focus on what changed and why it matters to end users.

Commit hash: $commit_hash
Commit message:
$commit_msg

Requirements:
- Maximum 80 characters
- Use present tense (e.g., \"Fix notification crash\" not \"Fixed\")
- Be specific but concise
- Focus on user-visible impact

Return ONLY the task description, no prefixes or explanations."
    
    local system_msg="You are a technical writer specializing in changelog entries."
    
    # Escape JSON strings properly
    local escaped_system=$(echo "$system_msg" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
    local escaped_prompt=$(echo "$prompt" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
    
    # Prepare request payload based on provider
    local api_url=""
    local auth_header=""
    local request_body=""
    
    if [[ "$provider" == "yandexgpt" ]]; then
        # YandexGPT API (OpenAI-compatible)
        api_url="https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        
        if [[ -z "$folder_id" ]]; then
            echo "Warning: LLM_FOLDER_ID not provided for YandexGPT, using commit message as fallback" >&2
            echo "$commit_msg" | head -1 | cut -c1-100
            return
        fi
        
        # YandexGPT uses IAM token in Authorization header and folderId in body
        auth_header="Authorization: Bearer $api_key"
        
        # YandexGPT format (slightly different from OpenAI)
        request_body=$(cat <<EOF
{
    "modelUri": "gpt://$folder_id/yandexgpt/latest",
    "completionOptions": {
        "stream": false,
        "temperature": 0.3,
        "maxTokens": 150
    },
    "messages": [
        {
            "role": "system",
            "text": "$escaped_system"
        },
        {
            "role": "user",
            "text": "$escaped_prompt"
        }
    ]
}
EOF
)
    else
        # OpenAI API (default)
        api_url="https://api.openai.com/v1/chat/completions"
        auth_header="Authorization: Bearer $api_key"
        
        request_body=$(cat <<EOF
{
    "model": "gpt-3.5-turbo",
    "messages": [
        {"role": "system", "content": "$escaped_system"},
        {"role": "user", "content": "$escaped_prompt"}
    ],
    "temperature": 0.3,
    "max_tokens": 150
}
EOF
)
    fi
    
    # Call LLM API
    local response=$(curl -s -w "\n%{http_code}" \
        -X POST "$api_url" \
        -H "$auth_header" \
        -H "Content-Type: application/json" \
        -d "$request_body")
    
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | sed '$d')
    
    if [[ "$http_code" != "200" ]]; then
        echo "Warning: LLM API ($provider) returned $http_code: $(echo "$body" | head -c 200)" >&2
        echo "$commit_msg" | head -1 | cut -c1-100
        return
    fi
    
    # Extract description from JSON response
    local description=""
    if command -v jq &> /dev/null; then
        if [[ "$provider" == "yandexgpt" ]]; then
            # YandexGPT response format: .result.alternatives[0].message.text
            description=$(echo "$body" | jq -r '.result.alternatives[0].message.text // .result.alternatives[0].text // empty' 2>/dev/null)
        else
            # OpenAI response format: .choices[0].message.content
            description=$(echo "$body" | jq -r '.choices[0].message.content // empty' 2>/dev/null)
        fi
    else
        # Fallback: extract content using grep/sed
        if [[ "$provider" == "yandexgpt" ]]; then
            description=$(echo "$body" | grep -oE '"text"\s*:\s*"[^"]*"' | head -1 | sed -E 's/.*"text"\s*:\s*"([^"]*)".*/\1/')
        else
            description=$(echo "$body" | grep -oE '"content"\s*:\s*"[^"]*"' | head -1 | sed -E 's/.*"content"\s*:\s*"([^"]*)".*/\1/')
        fi
    fi
    
    # Clean up description
    description=$(echo "$description" | head -1 | tr -d '"' | sed "s/^'//;s/'$//" | xargs)
    
    if [[ -z "$description" ]] || [[ "$description" == "null" ]]; then
        echo "$commit_msg" | head -1 | cut -c1-100
    else
        echo "$description" | cut -c1-100
    fi
}

TASK_DESCRIPTION=$(generate_task_description "$COMMIT_MESSAGE" "$COMMIT_HASH" "$LLM_API_KEY" "$LLM_PROVIDER" "$LLM_FOLDER_ID")
echo "Task description: $TASK_DESCRIPTION"

# Create changelog entry JSON
SHORT_HASH=$(echo "$COMMIT_HASH" | cut -c1-7)
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Escape task description for JSON (escape quotes and backslashes)
ESCAPED_DESC=$(echo "$TASK_DESCRIPTION" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')

# Create JSON using jq if available (most reliable), otherwise manual
if command -v jq &> /dev/null; then
    CHANGELOG_JSON=$(jq -n \
        --arg ts "$TIMESTAMP" \
        --arg ver "$APP_VERSION" \
        --arg ticket "$TICKET_ID" \
        --arg desc "$TASK_DESCRIPTION" \
        --arg hash "$SHORT_HASH" \
        '{timestamp: $ts, app_version: $ver, ticket_id: $ticket, task_description: $desc, commit_hash: $hash}')
else
    # Manual JSON creation with proper escaping
    CHANGELOG_JSON="{\"timestamp\":\"$TIMESTAMP\",\"app_version\":\"$APP_VERSION\",\"ticket_id\":\"$TICKET_ID\",\"task_description\":\"$ESCAPED_DESC\",\"commit_hash\":\"$SHORT_HASH\"}"
fi

# Save to file
echo "$CHANGELOG_JSON" > changelog-entry.json
echo ""
echo "Generated changelog entry saved to changelog-entry.json:"
if command -v jq &> /dev/null; then
    echo "$CHANGELOG_JSON" | jq .
else
    echo "$CHANGELOG_JSON"
fi

# Send to knowledge base
send_to_kb() {
    local json="$1"
    local url="$2"
    local key="$3"
    
    local headers=(-H "Content-Type: application/json")
    if [[ -n "$key" ]]; then
        headers+=(-H "Authorization: Bearer $key")
    fi
    
    local response=$(curl -s -w "\n%{http_code}" \
        -X POST "$url" \
        "${headers[@]}" \
        -d "$json")
    
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | sed '$d')
    
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
        echo ""
        echo "✓ Successfully sent changelog entry to knowledge base"
        echo "Response: $(echo "$body" | head -c 200)"
        return 0
    else
        echo ""
        echo "✗ Failed to send changelog entry: HTTP $http_code"
        echo "Response: $(echo "$body" | head -c 200)"
        return 1
    fi
}

if send_to_kb "$CHANGELOG_JSON" "$KB_API_URL" "$KB_API_KEY"; then
    echo ""
    echo "✓ Changelog automation completed successfully"
else
    echo ""
    echo "Warning: Failed to send to knowledge base, but entry was generated successfully"
    exit 0  # Don't fail the workflow if KB is unavailable
fi
