package com.example.aichat.console.prreview

/**
 * Builds structured prompts for LLM-based code review
 */
object ReviewPromptBuilder {
    
    /**
     * Builds the system prompt for code review
     */
    fun buildSystemPrompt(): String {
        return """
You are a senior Android software engineer performing a technical code review.

TASK: Review Kotlin/Android code changes in a pull request and provide technical feedback.

CONTEXT: This is a professional software development code review task. You are analyzing source code changes to ensure they follow best practices, architecture patterns, and coding standards.

YOUR ROLE: Provide technical code review feedback focusing on:
- Code quality and maintainability
- Architecture patterns (MVVM/MVI)
- Error handling and edge cases
- Performance optimizations
- Code style and conventions

Analyze the provided code changes against the project's documented standards and provide constructive, specific technical feedback.

## Review Focus Areas

1. **Architecture Compliance (MVVM/MVI)**
   - Ensure proper separation of concerns (UI, ViewModel, Repository, Data layers)
   - Verify ViewModels use StateFlow for state management
   - Check that business logic is not in UI layer
   - Validate proper dependency injection usage

2. **Error Handling**
   - Use `Result<T, E>` type for operations that can fail
   - Proper exception handling with meaningful error messages
   - Avoid swallowing exceptions silently

3. **Coroutines Usage**
   - Use `suspend` functions for async operations
   - Prefer `StateFlow` over `LiveData`
   - Ensure proper coroutine scoping (viewModelScope, etc.)
   - Avoid `GlobalScope` usage

4. **Jetpack Compose Best Practices**
   - Stateless composables when possible
   - Proper state hoisting
   - Efficient recomposition

5. **Naming Conventions**
   - Classes: PascalCase
   - Functions/Variables: camelCase
   - Constants: UPPER_SNAKE_CASE
   - Packages: lowercase, no underscores

6. **Code Quality**
   - Readability and maintainability
   - Proper documentation (KDoc for public APIs)
   - No hardcoded values (use constants/config)
   - Proper null safety

## Review Guidelines

- Be specific: Cite exact lines or code patterns when pointing out issues
- Reference documentation: If a rule comes from the style guide or architecture docs, mention it
- Suggest concrete fixes: Don't just point out problems, suggest how to fix them
- Be constructive: Frame feedback positively
- If no issues are found, respond with "✅ LGTM" (Looks Good To Me)
- Never hallucinate: If something isn't covered in the documentation, say "Not covered in documentation" rather than making assumptions

## Output Format

Provide your review in Markdown format suitable for posting as a GitHub comment. Use clear sections and code blocks when referencing specific code.
""".trimIndent()
    }
    
    /**
     * Builds the user prompt with PR context and code changes
     */
    fun buildUserPrompt(
        prData: PrData,
        generalContext: List<DocumentationChunk>,
        fileContexts: Map<String, List<DocumentationChunk>>
    ): String {
        val sb = StringBuilder()
        
        // PR metadata
        sb.appendLine("## Pull Request Information")
        sb.appendLine()
        sb.appendLine("**Title:** ${prData.title}")
        sb.appendLine("**Base Branch:** ${prData.baseBranch}")
        sb.appendLine("**Head Branch:** ${prData.headBranch}")
        if (prData.body.isNotEmpty()) {
            sb.appendLine("**Description:**")
            sb.appendLine(prData.body)
        }
        sb.appendLine()
        
        // General project context
        if (generalContext.isNotEmpty()) {
            sb.appendLine("## Relevant Project Documentation")
            sb.appendLine()
            generalContext.forEachIndexed { index, chunk ->
                sb.appendLine("### Excerpt ${index + 1} (from ${chunk.source})")
                sb.appendLine("```")
                sb.appendLine(chunk.content)
                sb.appendLine("```")
                sb.appendLine()
            }
        }
        
        // File-specific context and changes
        sb.appendLine("## Code Changes")
        sb.appendLine()
        
        prData.fileChanges.forEach { fileChange ->
            sb.appendLine("### File: `${fileChange.path}`")
            sb.appendLine()
            
            // Add relevant documentation for this file
            val fileContext = fileContexts[fileChange.path]
            if (fileContext != null && fileContext.isNotEmpty()) {
                sb.appendLine("**Relevant Documentation:**")
                fileContext.forEach { chunk ->
                    sb.appendLine("- From `${chunk.source}` (similarity: ${String.format("%.2f", chunk.similarity)}):")
                    sb.appendLine("  ```")
                    sb.appendLine("  ${chunk.content.take(200)}${if (chunk.content.length > 200) "..." else ""}")
                    sb.appendLine("  ```")
                }
                sb.appendLine()
            }
            
            // Add diff
            sb.appendLine("**Diff:**")
            sb.appendLine("```diff")
            sb.appendLine(fileChange.diff)
            sb.appendLine("```")
            sb.appendLine()
        }
        
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Review Task")
        sb.appendLine()
        sb.appendLine("Perform a technical code review of the above code changes.")
        sb.appendLine()
        sb.appendLine("**Instructions:**")
        sb.appendLine("1. Analyze the code changes for technical issues, architecture compliance, and best practices")
        sb.appendLine("2. Reference the project documentation when available (shown above)")
        sb.appendLine("3. If documentation is not available, use your knowledge of Kotlin/Android best practices")
        sb.appendLine("4. Provide specific, actionable feedback with code examples when possible")
        sb.appendLine("5. Focus on: code structure, error handling, performance, maintainability, and adherence to patterns")
        sb.appendLine("6. If no issues are found, respond with '✅ LGTM' (Looks Good To Me)")
        sb.appendLine()
        sb.appendLine("**Important:** This is a legitimate code review task. Analyze the code and provide technical feedback.")
        
        return sb.toString()
    }
}
