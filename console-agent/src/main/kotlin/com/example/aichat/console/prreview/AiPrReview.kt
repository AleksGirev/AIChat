package com.example.aichat.console.prreview

import com.example.aichat.console.ConsoleConfig
import com.example.aichat.console.llm.OpenAiClient
import com.example.aichat.console.model.ChatMessage
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.runBlocking

/**
 * Main orchestrator for AI-powered PR review
 * 
 * Usage:
 * ```bash
 * ./gradlew :console-agent:run --args="pr-review <pr_number> <github_owner> <github_repo>"
 * ```
 * 
 * Environment variables:
 * - GITHUB_TOKEN: GitHub personal access token
 * - LLM_API_KEY: API key for LLM (OpenAI, YandexGPT, etc.)
 * - LLM_BASE_URL: Base URL for LLM API (default: https://api.openai.com/v1)
 * - LLM_MODEL: Model name (default: gpt-4o)
 * - YANDEX_FOLDER_ID: Folder ID for YandexGPT (if using YandexGPT)
 * - OLLAMA_BASE_URL: Base URL for Ollama embeddings (default: http://localhost:11434)
 * - OLLAMA_MODEL: Embedding model (default: nomic-embed-text)
 * - RAG_DB_PATH: Path to RAG index database (optional)
 */
object AiPrReview {
    
    private const val BOT_MARKER = "🤖 **AI Code Review**"
    
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            printUsage()
            System.exit(1)
        }
        
        val prNumber = args[0].toIntOrNull() ?: run {
            println("Error: Invalid PR number: ${args[0]}")
            printUsage()
            System.exit(1)
            return
        }
        
        val owner = args[1]
        val repo = args[2]
        
        // Load configuration from environment
        val githubToken = System.getenv("GITHUB_TOKEN")
        if (githubToken.isNullOrEmpty()) {
            println("Error: GITHUB_TOKEN environment variable is required")
            System.exit(1)
            return
        }
        
        // Get API key - check environment first, then ConsoleConfig for YandexGPT
        val llmApiKey = System.getenv("LLM_API_KEY") 
            ?: System.getenv("OPENAI_API_KEY")
            ?: ConsoleConfig.YANDEX_IAM_TOKEN.takeIf { it.isNotEmpty() }
        
        if (llmApiKey.isNullOrEmpty()) {
            println("Error: LLM_API_KEY or OPENAI_API_KEY environment variable is required")
            System.exit(1)
            return
        }
        
        // Auto-detect YandexGPT by token format (starts with AQVN) or explicit config
        val isYandexGpt = llmApiKey.startsWith("AQVN") || 
                         System.getenv("LLM_MODEL")?.lowercase()?.contains("yandex") == true
        
        val yandexFolderId = System.getenv("YANDEX_FOLDER_ID") 
            ?: ConsoleConfig.YANDEX_FOLDER_ID.takeIf { it.isNotEmpty() }
        
        val llmBaseUrl = System.getenv("LLM_BASE_URL") ?: if (isYandexGpt) {
            "https://llm.api.cloud.yandex.net/v1"
        } else {
            "https://api.openai.com/v1"
        }
        
        // YandexGPT requires model format: gpt://{folderId}/yandexgpt/latest
        val llmModel = System.getenv("LLM_MODEL") ?: if (isYandexGpt && yandexFolderId != null) {
            "gpt://$yandexFolderId/yandexgpt/latest"
        } else if (isYandexGpt) {
            "yandexgpt" // Fallback if folder ID not available
        } else {
            "gpt-4o"
        }
        
        val ollamaBaseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val ollamaModel: String = System.getenv("OLLAMA_MODEL") ?: "nomic-embed-text"
        
        val ragDbPath = System.getenv("RAG_DB_PATH") ?: ConsoleConfig.getRAGDatabasePath()
        
        println("=== AI PR Review ===")
        println("PR: #$prNumber")
        println("Repository: $owner/$repo")
        println("RAG Database: $ragDbPath")
        println("LLM: $llmModel at $llmBaseUrl")
        if (isYandexGpt) {
            println("YandexGPT Folder ID: ${yandexFolderId ?: "NOT SET"}")
        }
        println()
        
        runBlocking {
            try {
                // Initialize components
                val githubClient = GitHubApiClient(githubToken, owner, repo)
                val ragPipeline = RAGPipeline.create(
                    dbPath = ragDbPath,
                    ollamaBaseUrl = ollamaBaseUrl,
                    ollamaModel = ollamaModel
                )
                val ragService = RagContextService(ragPipeline)
                val llmClient = OpenAiClient(
                    apiKey = llmApiKey,
                    baseUrl = llmBaseUrl,
                    model = llmModel,
                    folderId = yandexFolderId
                )
                
                // Check if RAG index exists
                val stats = ragPipeline.getStats()
                if (stats.totalChunks == 0) {
                    println("⚠ Warning: RAG index is empty. Review will proceed without documentation context.")
                    println("   To build the index, run: ./gradlew :console-agent:runRag --args=\"index docs/\"")
                    println()
                } else {
                    println("✓ RAG index loaded: ${stats.totalChunks} chunks from ${stats.uniqueSources} sources")
                    println()
                }
                
                // Step 1: Fetch PR data
                println("Fetching PR data...")
                println("  Repository: $owner/$repo")
                println("  PR Number: #$prNumber")
                println("  URL: https://github.com/$owner/$repo/pull/$prNumber")
                println()
                
                val prDataResult = githubClient.getPullRequest(prNumber)
                if (prDataResult.isFailure) {
                    val error = prDataResult.exceptionOrNull()
                    println("✗ Failed to fetch PR data:")
                    println("  ${error?.message}")
                    println()
                    println("Troubleshooting:")
                    println("  1. Verify the PR exists: https://github.com/$owner/$repo/pull/$prNumber")
                    println("  2. Check repository name: $owner/$repo")
                    println("  3. Ensure your GITHUB_TOKEN has 'repo' scope")
                    error?.printStackTrace()
                    System.exit(1)
                }
                
                val prData = prDataResult.getOrThrow()
                println("✓ PR fetched: ${prData.title}")
                println("  Changed files: ${prData.fileChanges.size}")
                println()
                
                if (prData.fileChanges.isEmpty()) {
                    println("No file changes detected. Skipping review.")
                    System.exit(0)
                }
                
                // Step 2: Retrieve RAG context
                println("Retrieving relevant documentation...")
                val generalContextResult = ragService.getGeneralContext(limit = 5)
                val generalContext = if (generalContextResult.isSuccess) {
                    val context = generalContextResult.getOrThrow()
                    if (context.isEmpty()) {
                        println("⚠ Warning: No general documentation context found in RAG index")
                    }
                    context
                } else {
                    println("⚠ Warning: Failed to retrieve general context: ${generalContextResult.exceptionOrNull()?.message}")
                    emptyList()
                }
                
                val fileContexts = mutableMapOf<String, List<DocumentationChunk>>()
                prData.fileChanges.forEach { fileChange ->
                    val contextResult = ragService.getRelevantContext(fileChange.path, limit = 3)
                    if (contextResult.isSuccess) {
                        val context = contextResult.getOrThrow()
                        if (context.isNotEmpty()) {
                            fileContexts[fileChange.path] = context
                        }
                    }
                }
                
                val filesWithContext = fileContexts.size
                val totalFiles = prData.fileChanges.size
                if (filesWithContext == 0 && totalFiles > 0) {
                    println("⚠ Warning: No RAG context found for any changed files")
                    println("   Review will proceed using general project knowledge and code analysis")
                } else {
                    println("✓ Retrieved context for $filesWithContext/$totalFiles files")
                }
                println()
                
                // Step 3: Generate review
                println("Generating AI review...")
                val systemPrompt = ReviewPromptBuilder.buildSystemPrompt()
                val userPrompt = ReviewPromptBuilder.buildUserPrompt(prData, generalContext, fileContexts)
                
                val messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userPrompt)
                )
                
                val reviewResult = llmClient.chatCompletion(
                    messages = messages,
                    temperature = 0.3, // Lower temperature for more consistent reviews
                    maxTokens = 4000
                )
                
                if (reviewResult.isFailure) {
                    val error = reviewResult.exceptionOrNull()
                    println("✗ Failed to generate review: ${error?.message}")
                    error?.printStackTrace()
                    System.exit(1)
                }
                
                val reviewResponse = reviewResult.getOrThrow()
                val reviewText = reviewResponse.choices.firstOrNull()?.message?.content
                    ?: "Failed to generate review content"
                
                // Format review with bot marker
                val formattedReview = buildReviewComment(reviewText)
                println("✓ Review generated (${reviewText.length} characters)")
                println()
                
                // Step 4: Check for existing comment (optional - to avoid duplicates)
                println("Checking for existing review comment...")
                val existingCommentResult = githubClient.findExistingBotComment(prNumber, BOT_MARKER)
                val existingCommentId = existingCommentResult.getOrNull()
                
                if (existingCommentId != null) {
                    println("⚠ Found existing review comment (ID: $existingCommentId)")
                    println("   Skipping comment posting to avoid duplicates.")
                    println("   If you want to update, delete the existing comment first.")
                } else {
                    // Step 5: Post comment
                    println("Posting review comment...")
                    val postResult = githubClient.postComment(prNumber, formattedReview)
                    
                    if (postResult.isSuccess) {
                        println("✓ Review comment posted successfully!")
                    } else {
                        val error = postResult.exceptionOrNull()
                        println("✗ Failed to post comment: ${error?.message}")
                        error?.printStackTrace()
                        // Don't exit - review was generated, just couldn't post
                        println()
                        println("Generated review (not posted):")
                        println("=".repeat(80))
                        println(formattedReview)
                    }
                }
                
                // Cleanup
                ragPipeline.close()
                
            } catch (e: Exception) {
                println("✗ Unexpected error: ${e.message}")
                e.printStackTrace()
                System.exit(1)
            }
        }
    }
    
    /**
     * Formats the review text with bot marker
     */
    private fun buildReviewComment(reviewText: String): String {
        return """
$BOT_MARKER (powered by RAG + project docs)

$reviewText

---
*This review was generated automatically by analyzing code changes against project documentation.*
""".trimIndent()
    }
    
    private fun printUsage() {
        println("""
            AI PR Review - Automated code review using RAG and LLM
            
            Usage:
              pr-review <pr_number> <github_owner> <github_repo>
            
            Example:
              pr-review 123 owner-name repo-name
            
            Required Environment Variables:
              GITHUB_TOKEN          GitHub personal access token
              LLM_API_KEY           API key for LLM (or OPENAI_API_KEY)
            
            Optional Environment Variables:
              LLM_BASE_URL          LLM API base URL (default: https://api.openai.com/v1)
              LLM_MODEL             Model name (default: gpt-4o)
              YANDEX_FOLDER_ID      Folder ID for YandexGPT (if using YandexGPT)
              OLLAMA_BASE_URL       Ollama base URL for embeddings (default: http://localhost:11434)
              OLLAMA_MODEL          Embedding model (default: nomic-embed-text)
              RAG_DB_PATH           Path to RAG index database (optional)
            
            Prerequisites:
              1. RAG index should be built (run: ./gradlew :console-agent:runRag --args="index docs/")
              2. Ollama should be running for embeddings (if using local embeddings)
              3. GitHub token with repo permissions
        """.trimIndent())
    }
}
