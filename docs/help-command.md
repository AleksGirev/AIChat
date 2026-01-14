# /help Command Guide

## Overview

The `/help` command provides project-aware AI assistance by combining:
1. **RAG (Retrieval-Augmented Generation)** - Searches indexed project documentation
2. **MCP (Model Context Protocol)** - Fetches current repository context (git branch, remote URL)

## Usage

```bash
/help <your question>
```

### Examples

```bash
/help How do we handle navigation in Compose?
/help What is the architecture of this project?
/help How do I add a new screen?
/help What coding conventions should I follow?
```

## How It Works

1. **RAG Search**: Searches the indexed documentation (from `docs/` directory) for relevant content
2. **Repo Context**: Fetches current Git branch and remote URL via MCP
3. **LLM Processing**: Sends query + context to LLM with project-specific instructions
4. **Response**: Returns answer based on project documentation

## Setup

### 1. Index Project Documentation

First, index the generated documentation:

```bash
./gradlew :console-agent:run
# Then in the console:
rag index docs/
```

Or use the RAG example:

```bash
./gradlew :console-agent:runRag -Prag.args="docs/"
```

### 2. Start Console Agent

```bash
./gradlew :console-agent:run
```

The console agent will:
- Initialize RAG pipeline
- Start repo MCP server (for git/fs tools)
- Create HelpService

### 3. Use /help Command

```bash
> /help How do we handle navigation in Compose?
```

## Requirements

- **RAG must be enabled** (default)
- **Ollama running** for embeddings (or configure different embedding service)
- **Git repository** (for repo context - optional but recommended)

## Troubleshooting

### "Help service is not available"

- Ensure RAG is enabled (start without `--no-rag` flag)
- Check that RAG database has indexed documents

### "Repo MCP client not available"

- This is a warning, not an error
- `/help` will work without repo context (just RAG)
- To enable repo context, ensure Git is installed and you're in a Git repository

### No relevant documentation found

- Index the documentation first: `rag index docs/`
- Check RAG status: `/rag status`
- Adjust similarity threshold: `/rag threshold 0.2` (lower = more results)

## Architecture

```
User Query
    ↓
/help command
    ↓
HelpService
    ├─→ RAGPipeline.search() → Relevant docs
    ├─→ RepoMcpClient → Git context
    └─→ OpenAiClient → LLM with context
    ↓
Answer
```

## MCP Tools Available

The repo MCP server exposes:

- `git.getCurrentBranch()` - Current Git branch
- `git.getRemoteUrl(remote)` - Remote repository URL
- `fs.readFile(path)` - Read file contents
- `repo.listFiles(dir, recursive)` - List files in directory

These tools are automatically used by `/help` to provide repository context.

