#!/bin/bash

# Baseline recipe test - default Ollama settings
# Temperature: 0.8 (default), no system prompt

curl http://localhost:11434/api/generate -d '{
  "model": "qwen2:7b-instruct",
  "prompt": "Suggest a gluten-free dinner with chicken for two.",
  "stream": false
}' | python3 -m json.tool 2>/dev/null | grep -A 100 '"response"' || cat
