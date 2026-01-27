#!/bin/bash

# Setup script for Vosk speech recognition model
# This script downloads and sets up the Vosk model for offline speech recognition

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VOSK_MODELS_DIR="$PROJECT_ROOT/vosk-models"
MODEL_NAME="vosk-model-small-ru-0.22"
MODEL_URL="https://alphacephei.com/kaldi/models/vosk-model-small-ru-0.22.zip"

echo "🎤 Vosk Speech Recognition Setup"
echo "=================================="
echo ""

# Create models directory
mkdir -p "$VOSK_MODELS_DIR"
cd "$VOSK_MODELS_DIR"

# Check if model already exists
if [ -d "$MODEL_NAME" ]; then
    echo "✓ Model already exists: $VOSK_MODELS_DIR/$MODEL_NAME"
    echo ""
    echo "To use the model, set environment variable:"
    echo "  export VOSK_MODEL_PATH=\"$VOSK_MODELS_DIR/$MODEL_NAME\""
    echo ""
    echo "Or add to your ~/.bashrc or ~/.zshrc:"
    echo "  export VOSK_MODEL_PATH=\"$VOSK_MODELS_DIR/$MODEL_NAME\""
    exit 0
fi

# Download model
echo "📥 Downloading Vosk model..."
echo "   URL: $MODEL_URL"
echo "   Destination: $VOSK_MODELS_DIR"
echo ""

if command -v curl &> /dev/null; then
    curl -L -o "${MODEL_NAME}.zip" "$MODEL_URL"
elif command -v wget &> /dev/null; then
    wget -O "${MODEL_NAME}.zip" "$MODEL_URL"
else
    echo "✗ Error: Neither curl nor wget found. Please install one of them."
    exit 1
fi

# Extract model
echo ""
echo "📦 Extracting model..."
unzip -q "${MODEL_NAME}.zip"

# Clean up zip file
rm "${MODEL_NAME}.zip"

echo ""
echo "✓ Model installed successfully!"
echo ""
echo "Model location: $VOSK_MODELS_DIR/$MODEL_NAME"
echo ""
echo "To use the model, set environment variable:"
echo "  export VOSK_MODEL_PATH=\"$VOSK_MODELS_DIR/$MODEL_NAME\""
echo ""
echo "Or add to your ~/.bashrc or ~/.zshrc:"
echo "  export VOSK_MODEL_PATH=\"$VOSK_MODELS_DIR/$MODEL_NAME\""
echo ""
echo "The console agent will also automatically detect the model if it's in:"
echo "  $VOSK_MODELS_DIR/$MODEL_NAME"
echo ""
