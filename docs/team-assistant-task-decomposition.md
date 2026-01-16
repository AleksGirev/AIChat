# Day 23: Team Assistant - Task Decomposition

## Overview

Create an integrated team assistant that combines:
- **RAG (Retrieval-Augmented Generation)**: Knows the project through indexed documentation
- **MCP (Model Context Protocol)**: Can interact with team services (task management)
- **Task Management**: Create tasks, query project status, provide priority recommendations

## Example Use Cases

- "Show me tasks with high priority and suggest what to do first"
- "What's the current project status?"
- "Create a task to fix the authorization bug"
- "What tasks are blocking others?"

## Architecture Components

### 1. Team Tasks MCP Server
- **Location**: `console-agent/src/main/kotlin/com/example/aichat/console/mcp/tasks/`
- **Purpose**: MCP server for managing team tasks
- **Data Source**: JSON file with mock task data
- **Tools**:
  - `tasks.createTask` - Create a new task
  - `tasks.getTasks` - Get list of tasks (with filters)
  - `tasks.getTask` - Get specific task by ID
  - `tasks.updateTask` - Update task status/priority/etc
  - `tasks.getTasksByPriority` - Get tasks filtered by priority
  - `tasks.getProjectStatus` - Get overall project status summary

### 2. Task Data Model
- **File**: `console-agent/data/team_tasks.json`
- **Structure**:
  ```json
  {
    "tasks": [
      {
        "taskId": "task-1",
        "title": "Fix authorization bug",
        "description": "Users cannot log in after update",
        "status": "in-progress",
        "priority": "high",
        "assignee": "alice",
        "creator": "bob",
        "createdAt": 1704300000000,
        "updatedAt": 1704300000000,
        "tags": ["bug", "auth", "critical"],
        "blockedBy": [],
        "blocks": ["task-2"]
      }
    ],
    "teamMembers": [
      {
        "userId": "alice",
        "name": "Alice Developer",
        "email": "alice@example.com",
        "role": "backend"
      }
    ]
  }
  ```

### 3. CLI Integration
- **Location**: `console-agent/src/main/kotlin/com/example/aichat/console/Main.kt`
- **Changes**: 
  - Initialize Tasks MCP server alongside other MCP servers
  - Connect Tasks MCP client to orchestrator
  - Enable task management tools in LLM context

### 4. LLM Prompt
- **Purpose**: System prompt that instructs LLM on how to use task management tools
- **Key Instructions**:
  - Use RAG to answer questions about project
  - Use task management tools to create/query tasks
  - Provide priority recommendations based on task analysis
  - Consider task dependencies when suggesting next actions

## Implementation Steps

### Step 1: Create Task Data Model
- Define Kotlin data classes for Task, TeamMember
- Create JSON serialization

### Step 2: Create Mock Data
- Generate realistic mock tasks with various statuses, priorities
- Include task dependencies (blockedBy, blocks)
- Add team members

### Step 3: Create Tasks MCP Server
- Implement JSON-RPC 2.0 over stdio
- Implement all required tools
- Handle task CRUD operations
- Provide project status aggregation

### Step 4: Create TaskService
- Load/save tasks from JSON file
- Implement filtering, searching
- Handle task relationships

### Step 5: Integrate with CLI
- Add Tasks MCP server to Main.kt
- Connect to orchestrator
- Test task management commands

### Step 6: Create LLM Prompt
- Write comprehensive system prompt
- Include examples of task management
- Add priority recommendation logic

## Task Status Values
- `todo` - Not started
- `in-progress` - Currently being worked on
- `review` - Waiting for review
- `done` - Completed
- `blocked` - Blocked by another task
- `cancelled` - Cancelled

## Priority Values
- `low` - Low priority
- `medium` - Medium priority
- `high` - High priority
- `critical` - Critical, urgent

## Task Relationships
- `blockedBy`: Array of task IDs that block this task
- `blocks`: Array of task IDs that this task blocks
- Used for dependency analysis and recommendations

## Project Status Summary
Should include:
- Total tasks by status
- Tasks by priority
- Blocked tasks
- Tasks by assignee
- Recent activity
- Recommendations
