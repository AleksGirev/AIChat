package com.example.aichat.console.mcp.repo.git

import java.io.File

/**
 * Service for Git operations
 * Uses shell commands for cross-platform compatibility
 */
class GitService {
    
    /**
     * Gets the current Git branch name
     * @return Branch name or "unknown" if not in a git repository
     */
    fun getCurrentBranch(): String {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && output.isNotBlank()) {
                output
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Gets the remote repository URL
     * @param remote Remote name (default: "origin")
     * @return Remote URL or empty string if not found
     */
    fun getRemoteUrl(remote: String = "origin"): String {
        return try {
            val process = ProcessBuilder("git", "config", "--get", "remote.$remote.url")
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && output.isNotBlank()) {
                output
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Checks if the current directory is a Git repository
     */
    fun isGitRepository(): Boolean {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--git-dir")
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}

