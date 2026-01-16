# Team Assistant - Day 23 Implementation

## Overview

Team Assistant is an integrated AI assistant that combines:
- **RAG (Retrieval-Augmented Generation)**: Answers questions about the project using indexed documentation
- **Tasks MCP Server**: Manages team tasks through MCP tools
- **CLI Interface**: Interactive command-line interface for team collaboration

## Features

### 1. Project Knowledge (RAG)
- Answers questions about project architecture, implementation, and code patterns
- Searches indexed documentation to provide accurate, context-aware answers
- Cites sources when using documentation

### 2. Task Management (MCP)
- Create tasks with title, description, priority, assignee, tags, due dates
- View tasks with filters (status, priority, assignee, tags)
- Update task status, priority, assignee, and other fields
- Get tasks by priority level
- Get project status summary with statistics

### 3. Smart Recommendations
- Analyzes task dependencies (blockedBy, blocks relationships)
- Recommends which tasks to prioritize
- Considers task status, priority, due dates, and dependencies

## Architecture

```
┌─────────────────┐
│   User Input    │
│   (CLI)         │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────┐
│  TeamAssistantOrchestrator      │
│  - RAG Pipeline                 │
│  - Tasks MCP Client             │
│  - LLM Client                   │
└────────┬────────────────────────┘
         │
         ├──► RAG Search (for project questions)
         │
         └──► Tasks MCP Tools (for task operations)
                  │
                  ▼
         ┌─────────────────┐
         │  TasksMcpServer │
         │  (stdio)        │
         └────────┬────────┘
                  │
                  ▼
         ┌─────────────────┐
         │ team_tasks.json │
         │ (mock data)     │
         └─────────────────┘
```

## Components

### 1. Data Files
- **`console-agent/data/team_tasks.json`**: Mock data file with team tasks and team members

### 2. MCP Server
- **`TasksMcpServer.kt`**: MCP server implementation (JSON-RPC 2.0 over stdio)
- **`TaskService.kt`**: Service for managing tasks (CRUD operations)
- **`model.kt`**: Data models (Task, TeamMember, TeamData, ProjectStatus)

**Available Tools:**
- `tasks.createTask` - Create a new task
- `tasks.getTasks` - Get tasks with filters
- `tasks.getTask` - Get specific task by ID
- `tasks.updateTask` - Update task fields
- `tasks.getTasksByPriority` - Get tasks by priority
- `tasks.getProjectStatus` - Get project status summary

### 3. Orchestrator
- **`TeamAssistantOrchestrator.kt`**: Combines RAG + Tasks MCP
  - Automatically detects if question is about project (uses RAG) or task management (uses MCP)
  - Integrates RAG context with task management operations

### 4. Prompt
- **`docs/team-assistant-prompt-en.md`**: Comprehensive English prompt for LLM
  - Defines capabilities, response formats, examples
  - Guidelines for task management and recommendations

## Usage

### Starting the Team Assistant

**Recommended: Use JAR directly** (better for interactive input):
```bash
# Build and install
./gradlew :console-agent:installDist

# Run with team assistant flag
./console-agent/build/install/console-agent/bin/console-agent --team-assistant
```

**Alternative: Via Gradle** (may have input issues):
```bash
# From project root
./gradlew :console-agent:run --args="--team-assistant"
```

**Note**: 
- The `--team-assistant` flag enables Team Assistant mode. Without it, the agent runs in regular RAG mode.
- If you experience input issues (text overwriting) when using Gradle, use the JAR method instead.

The assistant will:
1. Initialize RAG pipeline (if documents are indexed)
2. Start Tasks MCP server
3. Load team assistant prompt
4. Display ready message

### Example Commands

#### Project Questions (uses RAG)
```
> How does authentication work in this project?
> What is the architecture of the RAG system?
> Where is the MCP client implementation?
> Explain how task dependencies work
```

#### Task Management (uses MCP tools)
```
> Show me tasks with high priority
> Create a task to fix the database migration bug
> What's the current project status?
> Show me tasks with high priority and suggest what to do first
> Update task-001 status to in-progress
```

#### Combined Operations
```
> How does RAG work and show me related tasks
> What tasks are blocking the authentication feature?
```

## Task Data Structure

Tasks are stored in `console-agent/data/team_tasks.json`:

```json
{
  "tasks": [
    {
      "taskId": "task-001",
      "title": "Implement user authentication flow",
      "description": "...",
      "status": "in-progress",
      "priority": "high",
      "assignee": "alex",
      "tags": ["auth", "ui"],
      "createdAt": 1704067200000,
      "updatedAt": 1704153600000,
      "dueDate": 1704240000000,
      "blockedBy": [],
      "blocks": ["task-002"],
      "estimatedHours": 8,
      "actualHours": 4
    }
  ],
  "teamMembers": [...]
}
```

### Task Status Values
- `todo` - Not started
- `in-progress` - Actively being worked on
- `review` - Complete, waiting for review
- `done` - Completed and reviewed
- `blocked` - Cannot proceed due to dependencies
- `cancelled` - Cancelled

### Task Priority Values
- `low` - Nice-to-have features
- `medium` - Normal priority work
- `high` - Important features or bugs
- `critical` - Urgent issues blocking the team

## Configuration

### RAG Settings
- `ragEnabled`: Enable/disable RAG (default: true)
- `ragContextLimit`: Number of RAG results (default: 10)
- `minSimilarity`: Minimum similarity threshold (default: 0.3)
- `rerankThresholdValue`: Reranker threshold (default: 0.71)
- `useReranker`: Enable reranker (default: true)

### Prompt File
The assistant loads the prompt from `docs/team-assistant-prompt-en.md`. If not found, uses a default prompt.

## Example Interactions

### Example 1: Task Query with Recommendations
```
> Show me tasks with high priority and suggest what to do first

[Agent]: Here are the high priority tasks:

1. [task-008] Fix database migration error (CRITICAL)
   Status: open | Assignee: maria | Blocks: task-002, task-003
   Description: App crashes on startup after update...

2. [task-001] Implement user authentication flow (HIGH, IN-PROGRESS)
   Status: in-progress | Assignee: alex | Blocks: task-002
   Progress: 4/8 hours (50%)

**Recommendation**: Start with task-008 (Fix database migration error) because:
- It's CRITICAL priority and affects production stability
- It's blocking 2 other tasks (task-002, task-003)
- Once fixed, it will unblock other high-priority work
```

### Example 2: Project Question
```
> How does authentication work in this project?

[Agent]: Based on the project documentation, authentication in this project works as follows:

**Authentication Flow:**
1. User enters email/username and password on the AuthScreen
2. AuthViewModel handles authentication logic
3. Authentication state is stored in EncryptedSharedPreferences
...

**Source**: docs/product/authorization-guide.md
```

### Example 3: Creating a Task
```
> Create a task to fix the authorization bug

[Agent]: ✓ Task created successfully!

Task ID: task-016
Title: Fix authorization bug
Description: Fix the authorization bug that prevents users from logging in
Priority: high
Status: todo
Assignee: Unassigned
```

## Development

### Adding New Task Fields
1. Update `Task` model in `model.kt`
2. Update `TaskService` methods
3. Update `TasksMcpServer` tool definitions
4. Update mock data in `team_tasks.json`

### Modifying the Prompt
Edit `docs/team-assistant-prompt-en.md` and restart the assistant.

### Testing
1. Start the assistant: `./gradlew :console-agent:run`
2. Test task operations: create, list, update tasks
3. Test project questions: ask about architecture, implementation
4. Test recommendations: ask for task prioritization

## Files Created

1. **Data:**
   - `console-agent/data/team_tasks.json` - Mock task data

2. **MCP Server:**
   - `console-agent/src/main/kotlin/com/example/aichat/console/mcp/tasks/model.kt`
   - `console-agent/src/main/kotlin/com/example/aichat/console/mcp/tasks/TaskService.kt`
   - `console-agent/src/main/kotlin/com/example/aichat/console/mcp/tasks/TasksMcpServer.kt`
   - `console-agent/src/main/kotlin/com/example/aichat/console/mcp/tasks/TasksMcpServerMain.kt`

3. **Orchestrator:**
   - `console-agent/src/main/kotlin/com/example/aichat/console/agent/TeamAssistantOrchestrator.kt`

4. **Documentation:**
   - `docs/team-assistant-prompt-en.md` - LLM prompt
   - `docs/TEAM_ASSISTANT_README.md` - This file

5. **Factory Update:**
   - `console-agent/src/main/kotlin/com/example/aichat/console/mcp/McpClientFactory.kt` - Added `createTasksMcpClient()`

## Integration Status

✅ **Fully Integrated!**

The Team Assistant is now integrated into the main CLI:
- Added `--team-assistant` flag support in `Main.kt`
- `TeamAssistantOrchestrator` is initialized when flag is set
- Tasks MCP server is started automatically
- All commands work seamlessly

## Notes

- Tasks are stored in JSON file (mock data). For production, consider using a database.
- RAG index must be populated before asking project questions.
- The assistant automatically detects whether to use RAG or MCP tools based on the query.
- Task dependencies (blockedBy, blocks) are used for smart recommendations.
