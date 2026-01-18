#!/usr/bin/env kotlin

/**
 * Changelog Entry Generator
 * 
 * Analyzes the latest commit using an LLM and generates a structured changelog entry
 * that is sent to a remote knowledge base API.
 * 
 * Usage:
 *   kotlinc -script generate-changelog-entry.kt -- \
 *     --commit-hash <hash> \
 *     --commit-message "<message>" \
 *     --openai-key <key> \
 *     --kb-url <url> \
 *     --kb-key <key> \
 *     --pattern "<regex_pattern>"
 */

@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")
@file:DependsOn("com.google.code.gson:gson:2.10.1")

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

// ============================================================================
// Configuration & Argument Parsing
// ============================================================================

data class Config(
    val commitHash: String,
    val commitMessage: String,
    val openaiApiKey: String?,
    val kbApiUrl: String,
    val kbApiKey: String?,
    val ticketPattern: Pattern
)

fun parseArgs(args: Array<String>): Config {
    var commitHash = ""
    var commitMessage = ""
    var openaiApiKey: String? = null
    var kbApiUrl = "https://httpbin.org/post"
    var kbApiKey: String? = null
    var ticketPattern = Pattern.compile("\\[([A-Z]+-\\d+)\\]") // Default: [TKT-123]
    
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--commit-hash" -> commitHash = args[++i]
            "--commit-message" -> commitMessage = args[++i]
            "--openai-key" -> openaiApiKey = args[++i]
            "--kb-url" -> kbApiUrl = args[++i]
            "--kb-key" -> kbApiKey = args[++i]
            "--pattern" -> ticketPattern = Pattern.compile(args[++i])
            else -> {
                if (!args[i].startsWith("--")) {
                    // Handle positional args or continue
                }
            }
        }
        i++
    }
    
    require(commitHash.isNotBlank()) { "Missing --commit-hash" }
    require(commitMessage.isNotBlank()) { "Missing --commit-message" }
    
    return Config(commitHash, commitMessage, openaiApiKey, kbApiUrl, kbApiKey, ticketPattern)
}

// ============================================================================
// Version Extraction
// ============================================================================

fun extractVersionFromGradle(): String {
    val buildFile = File("app/build.gradle.kts")
    if (!buildFile.exists()) {
        println("Warning: app/build.gradle.kts not found, using default version")
        return "1.0.0"
    }
    
    val content = buildFile.readText()
    val versionMatch = Regex("versionName\\s*=\\s*[\"']([^\"']+)[\"']").find(content)
    return versionMatch?.groupValues?.get(1) ?: "1.0.0"
}

// ============================================================================
// Ticket ID Extraction
// ============================================================================

fun extractTicketId(commitMessage: String, pattern: Pattern): String {
    val matcher = pattern.matcher(commitMessage)
    return if (matcher.find()) {
        matcher.group(1) ?: "UNKNOWN"
    } else {
        "UNKNOWN"
    }
}

// ============================================================================
// LLM Integration (OpenAI-compatible API)
// ============================================================================

data class OpenAIMessage(val role: String, val content: String)
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 150
)
data class OpenAIChoice(val message: OpenAIMessage)
data class OpenAIResponse(val choices: List<OpenAIChoice>)

fun generateTaskDescription(commitMessage: String, commitHash: String, apiKey: String?): String {
    if (apiKey.isNullOrBlank()) {
        println("Warning: OPENAI_API_KEY not provided, using commit message as fallback")
        return commitMessage.lines().firstOrNull()?.take(100) ?: "No description available"
    }
    
    val client = OkHttpClient()
    val gson = Gson()
    
    val prompt = """
        Analyze the following git commit and generate a concise, user-friendly task description.
        Focus on what changed and why it matters to end users.
        
        Commit hash: $commitHash
        Commit message:
        $commitMessage
        
        Requirements:
        - Maximum 80 characters
        - Use present tense (e.g., "Fix notification crash" not "Fixed")
        - Be specific but concise
        - Focus on user-visible impact
        
        Return ONLY the task description, no prefixes or explanations.
    """.trimIndent()
    
    val requestBody = gson.toJson(OpenAIRequest(
        model = "gpt-3.5-turbo",
        messages = listOf(
            OpenAIMessage("system", "You are a technical writer specializing in changelog entries."),
            OpenAIMessage("user", prompt)
        )
    ))
    
    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(requestBody.toRequestBody("application/json".toMediaType()))
        .build()
    
    return try {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            println("Warning: LLM API returned ${response.code}: $responseBody")
            return commitMessage.lines().firstOrNull()?.take(100) ?: "No description available"
        }
        
        val openaiResponse = gson.fromJson(responseBody, OpenAIResponse::class.java)
        val description = openaiResponse.choices.firstOrNull()?.message?.content?.trim() 
            ?: commitMessage.lines().firstOrNull()?.take(100) ?: "No description available"
        
        // Clean up any quotes or extra formatting
        description.replace("\"", "").replace("'", "").trim()
    } catch (e: Exception) {
        println("Warning: Failed to call LLM API: ${e.message}")
        commitMessage.lines().firstOrNull()?.take(100) ?: "No description available"
    }
}

// ============================================================================
// Knowledge Base API Integration
// ============================================================================

data class ChangelogEntry(
    val timestamp: String,
    val app_version: String,
    val ticket_id: String,
    val task_description: String,
    val commit_hash: String
)

fun sendToKnowledgeBase(entry: ChangelogEntry, apiUrl: String, apiKey: String?): Boolean {
    val client = OkHttpClient()
    val gson = Gson()
    val json = gson.toJson(entry)
    
    val requestBuilder = Request.Builder()
        .url(apiUrl)
        .addHeader("Content-Type", "application/json")
    
    if (!apiKey.isNullOrBlank()) {
        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
    }
    
    val request = requestBuilder
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()
    
    return try {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        
        if (response.isSuccessful) {
            println("✓ Successfully sent changelog entry to knowledge base")
            println("Response: ${responseBody.take(200)}")
            true
        } else {
            println("✗ Failed to send changelog entry: HTTP ${response.code}")
            println("Response: ${responseBody.take(200)}")
            false
        }
    } catch (e: Exception) {
        println("✗ Error sending to knowledge base: ${e.message}")
        false
    }
}

// ============================================================================
// Main Execution
// ============================================================================

fun main(args: Array<String>) {
    val config = try {
        parseArgs(args)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        System.exit(1)
    }
    
    println("Generating changelog entry for commit: ${config.commitHash}")
    
    // Extract version
    val appVersion = extractVersionFromGradle()
    println("App version: $appVersion")
    
    // Extract ticket ID
    val ticketId = extractTicketId(config.commitMessage, config.ticketPattern)
    println("Ticket ID: $ticketId")
    
    // Generate task description using LLM
    val taskDescription = generateTaskDescription(
        config.commitMessage,
        config.commitHash,
        config.openaiApiKey
    )
    println("Task description: $taskDescription")
    
    // Create changelog entry
    val entry = ChangelogEntry(
        timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
        app_version = appVersion,
        ticket_id = ticketId,
        task_description = taskDescription,
        commit_hash = config.commitHash.take(7) // Short hash
    )
    
    // Save to file for inspection
    val gson = Gson()
    val jsonOutput = gson.toJson(entry)
    File("changelog-entry.json").writeText(jsonOutput)
    println("\nGenerated changelog entry saved to changelog-entry.json:")
    println(jsonOutput)
    
    // Send to knowledge base
    val success = sendToKnowledgeBase(entry, config.kbApiUrl, config.kbApiKey)
    
    if (!success) {
        println("\nWarning: Failed to send to knowledge base, but entry was generated successfully")
        System.exit(0) // Don't fail the workflow if KB is unavailable
    }
    
    println("\n✓ Changelog automation completed successfully")
}

main(args)
