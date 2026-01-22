#!/bin/bash

# Installation script for console-agent command
# This script creates a symlink to make 'console-agent' available in PATH

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONSOLE_AGENT_SCRIPT="$PROJECT_ROOT/scripts/console-agent"

# Determine installation directory
if [ -d "$HOME/.local/bin" ]; then
    INSTALL_DIR="$HOME/.local/bin"
elif [ -d "$HOME/bin" ]; then
    INSTALL_DIR="$HOME/bin"
else
    INSTALL_DIR="$HOME/.local/bin"
    mkdir -p "$INSTALL_DIR"
fi

SYMLINK_PATH="$INSTALL_DIR/console-agent"

echo "Installing console-agent command..."
echo ""

# Check if symlink already exists
if [ -L "$SYMLINK_PATH" ]; then
    echo "Removing existing symlink: $SYMLINK_PATH"
    rm "$SYMLINK_PATH"
elif [ -f "$SYMLINK_PATH" ]; then
    echo "Warning: File already exists at $SYMLINK_PATH"
    read -p "Overwrite? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Installation cancelled."
        exit 1
    fi
    rm "$SYMLINK_PATH"
fi

# Create symlink
ln -s "$CONSOLE_AGENT_SCRIPT" "$SYMLINK_PATH"
echo "✓ Created symlink: $SYMLINK_PATH -> $CONSOLE_AGENT_SCRIPT"

# Check if directory is in PATH
if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
    echo ""
    echo "⚠️  Warning: $INSTALL_DIR is not in your PATH"
    echo ""
    echo "Add this line to your ~/.zshrc or ~/.bashrc:"
    echo "  export PATH=\"\$PATH:$INSTALL_DIR\""
    echo ""
    echo "Or run:"
    echo "  echo 'export PATH=\"\$PATH:$INSTALL_DIR\"' >> ~/.zshrc"
    echo "  source ~/.zshrc"
else
    echo "✓ $INSTALL_DIR is already in PATH"
fi

echo ""
echo "Installation complete!"
echo ""
echo "You can now use 'console-agent' command:"
echo "  console-agent          # Run remote chat"
echo "  console-agent remote   # Run remote chat"
echo "  console-agent offline  # Run offline chat"
echo "  console-agent help     # Show help"
