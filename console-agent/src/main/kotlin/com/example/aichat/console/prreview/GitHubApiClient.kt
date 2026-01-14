package com.example.aichat.console.prreview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * GitHub API client for PR operations
 * Handles fetching PR data and posting comments
 */
class GitHubApiClient(
    private val token: String,
    private val owner: String,
    private val repo: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val baseUrl = "https://api.github.com"
    private val mediaType = "application/json".toMediaType()
    
    /**
     * Fetches PR details including diff
     */
    suspend fun getPullRequest(prNumber: Int): Result<PrData> = withContext(Dispatchers.IO) {
        try {
            // Fetch PR details
            val prUrl = "$baseUrl/repos/$owner/$repo/pulls/$prNumber"
            val prRequest = Request.Builder()
                .url(prUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            
            val prResponse = client.newCall(prRequest).execute()
            if (!prResponse.isSuccessful) {
                val errorBody = prResponse.body?.string() ?: "Unknown error"
                val errorMessage = when (prResponse.code) {
                    404 -> "PR #$prNumber not found in repository $owner/$repo. " +
                            "Please verify:\n" +
                            "  1. The PR number exists: https://github.com/$owner/$repo/pull/$prNumber\n" +
                            "  2. The repository name is correct: $owner/$repo\n" +
                            "  3. Your token has access to this repository\n" +
                            "API Error: $errorBody"
                    401 -> "Unauthorized. Please check your GITHUB_TOKEN has 'repo' scope. Error: $errorBody"
                    403 -> "Forbidden. Your token may not have access to repository $owner/$repo. Error: $errorBody"
                    else -> "Failed to fetch PR: ${prResponse.code} - $errorBody\nRequested URL: $prUrl"
                }
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            val prBody = prResponse.body?.string() ?: throw Exception("Empty PR response")
            val prData = json.decodeFromString<PrResponse>(prBody)
            
            // Fetch diff
            val diffUrl = "$baseUrl/repos/$owner/$repo/pulls/$prNumber"
            val diffRequest = Request.Builder()
                .url("$diffUrl.diff")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3.diff")
                .get()
                .build()
            
            val diffResponse = client.newCall(diffRequest).execute()
            if (!diffResponse.isSuccessful) {
                val errorBody = diffResponse.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    Exception("Failed to fetch diff: ${diffResponse.code} - $errorBody")
                )
            }
            
            val diff = diffResponse.body?.string() ?: ""
            
            // Parse diff to extract file changes
            val fileChanges = parseDiff(diff)
            
            Result.success(
                PrData(
                    number = prData.number,
                    title = prData.title,
                    body = prData.body ?: "",
                    baseBranch = prData.base?.ref ?: "main",
                    headBranch = prData.head?.ref ?: "",
                    diff = diff,
                    fileChanges = fileChanges
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Posts a comment on a PR
     */
    suspend fun postComment(prNumber: Int, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/repos/$owner/$repo/issues/$prNumber/comments"
            
            val requestBody = CommentRequest(body = body)
            val jsonBody = json.encodeToString(CommentRequest.serializer(), requestBody)
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            
            val response = client.newCall(request).execute()
            
            // Handle rate limits and permissions
            if (response.code == 403) {
                val errorBody = response.body?.string() ?: "Unknown error"
                val rateLimitRemaining = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: 0
                
                // Check if it's a rate limit issue
                if (rateLimitRemaining == 0) {
                    val rateLimitReset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                    val resetTime = rateLimitReset?.let { 
                        java.time.Instant.ofEpochSecond(it).toString()
                    } ?: "unknown"
                    return@withContext Result.failure(
                        Exception("GitHub API rate limit exceeded. Reset at: $resetTime")
                    )
                }
                
                // Otherwise it's a permissions issue
                val errorMessage = if (errorBody.contains("Resource not accessible")) {
                    "GitHub token doesn't have required permissions. " +
                    "Possible causes:\n" +
                    "  1. Token missing 'repo' scope - regenerate token with 'repo' scope\n" +
                    "  2. PR is from a fork - GitHub Actions can't post comments on fork PRs\n" +
                    "  3. Repository is private and token doesn't have access\n" +
                    "Error: $errorBody"
                } else {
                    "Failed to post comment: ${response.code} - $errorBody"
                }
                return@withContext Result.failure(Exception(errorMessage))
            }
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    Exception("Failed to post comment: ${response.code} - $errorBody")
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Checks if a bot comment already exists (to avoid duplicates)
     */
    suspend fun findExistingBotComment(prNumber: Int, botMarker: String): Result<Int?> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/repos/$owner/$repo/issues/$prNumber/comments"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.success(null) // Fail gracefully
            }
            
            val body = response.body?.string() ?: return@withContext Result.success(null)
            val comments = json.decodeFromString<List<CommentResponse>>(body)
            
            // Find comment that contains the bot marker
            val existingComment = comments.firstOrNull { comment ->
                comment.body.contains(botMarker, ignoreCase = true)
            }
            
            Result.success(existingComment?.id)
        } catch (e: Exception) {
            Result.success(null) // Fail gracefully - don't block on this
        }
    }
    
    /**
     * Parses diff to extract per-file changes
     */
    private fun parseDiff(diff: String): List<FileChange> {
        if (diff.isEmpty()) return emptyList()
        
        val fileChanges = mutableListOf<FileChange>()
        val lines = diff.lines()
        var currentFile: String? = null
        var currentContent = StringBuilder()
        
        for (line in lines) {
            when {
                line.startsWith("diff --git") -> {
                    // Save previous file if exists
                    currentFile?.let { file ->
                        if (currentContent.isNotEmpty()) {
                            fileChanges.add(
                                FileChange(
                                    path = file,
                                    diff = currentContent.toString()
                                )
                            )
                        }
                    }
                    
                    // Extract file path from "diff --git a/path b/path"
                    val match = Regex("diff --git a/(.+) b/(.+)").find(line)
                    currentFile = match?.groupValues?.get(2) // Use "b" path (new file)
                    currentContent = StringBuilder()
                    currentContent.appendLine(line)
                }
                line.startsWith("+++") || line.startsWith("---") -> {
                    currentContent.appendLine(line)
                }
                else -> {
                    currentContent.appendLine(line)
                }
            }
        }
        
        // Add last file
        currentFile?.let { file ->
            if (currentContent.isNotEmpty()) {
                fileChanges.add(
                    FileChange(
                        path = file,
                        diff = currentContent.toString()
                    )
                )
            }
        }
        
        return fileChanges
    }
    
    @Serializable
    private data class PrResponse(
        val number: Int,
        val title: String,
        val body: String?,
        val base: BranchRef?,
        val head: BranchRef?
    )
    
    @Serializable
    private data class BranchRef(
        val ref: String
    )
    
    @Serializable
    private data class CommentRequest(
        val body: String
    )
    
    @Serializable
    private data class CommentResponse(
        val id: Int,
        val body: String
    )
}

/**
 * PR data model
 */
data class PrData(
    val number: Int,
    val title: String,
    val body: String,
    val baseBranch: String,
    val headBranch: String,
    val diff: String,
    val fileChanges: List<FileChange>
)

/**
 * File change model
 */
data class FileChange(
    val path: String,
    val diff: String
)
