package com.example.aichat.data.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simple RAG service for searching product documentation
 * 
 * For MVP: Searches documentation files directly using text matching
 * For production: Would connect to RAG pipeline API or use vector search
 */
class RagService(
    private val context: Context
) {
    private val tag = "RagService"
    
    /**
     * Search product documentation for relevant content
     * 
     * @param query Search query
     * @param limit Maximum number of results
     * @return Formatted context string with relevant documentation chunks
     */
    suspend fun searchDocumentation(
        query: String,
        limit: Int = 5
    ): String = withContext(Dispatchers.IO) {
        Log.d("GIREV", "=== RagService: Searching documentation ===")
        Log.d("GIREV", "Query: $query, Limit: $limit")
        
        try {
            val docsDir = File(context.filesDir, "product_docs")
            Log.d("GIREV", "Docs directory: ${docsDir.absolutePath}")
            
            // For MVP: If docs directory doesn't exist, return empty
            // In production, docs would be bundled with app or downloaded
            if (!docsDir.exists() || !docsDir.isDirectory) {
                Log.d("GIREV", "✗ Product docs directory not found: ${docsDir.absolutePath}")
                Log.d(tag, "Product docs directory not found, skipping RAG search")
                return@withContext ""
            }
            
            Log.d("GIREV", "✓ Docs directory exists, searching files...")
            
            val queryLower = query.lowercase()
            val results = mutableListOf<Pair<String, String>>() // (filename, content)
            
            val mdFiles = docsDir.walkTopDown()
                .filter { it.isFile && it.extension == "md" }
                .toList()
            
            Log.d("GIREV", "Found ${mdFiles.size} markdown files to search")
            
            // Search all markdown files in docs directory
            mdFiles.forEach { file ->
                    try {
                        val content = file.readText()
                        val contentLower = content.lowercase()
                        
                        // Simple keyword matching (for MVP)
                        // In production, would use semantic search via RAG pipeline
                        val score = calculateRelevanceScore(queryLower, contentLower)
                        
                        if (score > 0) {
                            // Extract relevant section around matches
                            val relevantSection = extractRelevantSection(content, queryLower, 500)
                            if (relevantSection.isNotEmpty()) {
                                results.add(Pair(file.name, relevantSection))
                                Log.d("GIREV", "✓ Found relevant content in: ${file.name} (score: $score)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("GIREV", "Error reading file ${file.name}: ${e.message}")
                        Log.w(tag, "Error reading file ${file.name}", e)
                    }
                }
            
            Log.d("GIREV", "Found ${results.size} relevant sections, sorting by relevance...")
            
            // Sort by relevance and limit results
            val sortedResults = results
                .sortedByDescending { calculateRelevanceScore(queryLower, it.second.lowercase()) }
                .take(limit)
            
            Log.d("GIREV", "Selected ${sortedResults.size} top results")
            
            if (sortedResults.isEmpty()) {
                Log.d("GIREV", "✗ No relevant documentation found")
                return@withContext ""
            }
            
            // Format results
            val formatted = buildString {
                sortedResults.forEachIndexed { index, (filename, content) ->
                    appendLine("--- ${filename.replace(".md", "").replace("-", " ").capitalize()} ---")
                    appendLine(content)
                    if (index < sortedResults.size - 1) {
                        appendLine()
                    }
                }
            }
            
            Log.d("GIREV", "✓ RAG search complete: ${formatted.length} chars from ${sortedResults.size} files")
            Log.d("GIREV", "Files used: ${sortedResults.map { it.first }.joinToString(", ")}")
            
            formatted
        } catch (e: Exception) {
            Log.e("GIREV", "✗ Error searching documentation: ${e.message}", e)
            Log.e(tag, "Error searching documentation", e)
            ""
        }
    }
    
    /**
     * Simple relevance scoring based on keyword matches
     * For MVP: Basic text matching
     * For production: Would use semantic similarity from RAG pipeline
     */
    private fun calculateRelevanceScore(query: String, content: String): Int {
        val queryWords = query.split(" ").filter { it.length > 2 }
        var score = 0
        
        queryWords.forEach { word ->
            val count = content.split(word).size - 1
            score += count
        }
        
        return score
    }
    
    /**
     * Extract relevant section around query matches
     */
    private fun extractRelevantSection(
        content: String,
        query: String,
        contextChars: Int
    ): String {
        val queryWords = query.split(" ").filter { it.length > 2 }
        val contentLower = content.lowercase()
        
        // Find first match position
        var firstMatch = Int.MAX_VALUE
        queryWords.forEach { word ->
            val index = contentLower.indexOf(word)
            if (index != -1 && index < firstMatch) {
                firstMatch = index
            }
        }
        
        if (firstMatch == Int.MAX_VALUE) {
            // No match found, return beginning of content
            return content.take(contextChars)
        }
        
        // Extract section around match
        val start = maxOf(0, firstMatch - contextChars / 2)
        val end = minOf(content.length, firstMatch + contextChars / 2)
        
        var extracted = content.substring(start, end)
        
        // Try to start at sentence boundary
        val sentenceStart = extracted.indexOfLast { it == '.' || it == '\n' }
        if (sentenceStart > 50) {
            extracted = extracted.substring(sentenceStart + 1).trimStart()
        }
        
        // Try to end at sentence boundary
        val sentenceEnd = extracted.indexOfFirst { it == '.' || it == '\n' }
        if (sentenceEnd > 50 && sentenceEnd < extracted.length - 50) {
            extracted = extracted.substring(0, sentenceEnd + 1)
        }
        
        return extracted.trim()
    }
    
    /**
     * Copy product documentation from assets to app files directory
     * Call this once on app startup to make docs available for search
     */
    suspend fun initializeDocumentation(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val docsDir = File(context.filesDir, "product_docs")
            docsDir.mkdirs()
            
            // For MVP: Documentation files should be in assets/product/
            // Copy them to files directory for search
            val assetManager = context.assets
            
            try {
                val assetFiles = assetManager.list("product") ?: emptyArray()
                
                assetFiles.forEach { filename ->
                    try {
                        val targetFile = File(docsDir, filename)
                        if (!targetFile.exists()) {
                            assetManager.open("product/$filename").use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(tag, "Copied documentation file: $filename")
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Error copying file $filename", e)
                    }
                }
                
                Log.d(tag, "Documentation initialized: ${assetFiles.size} files")
                Result.success(Unit)
            } catch (e: Exception) {
                // Assets directory might not exist - that's OK for MVP
                Log.d(tag, "Product docs not in assets (will use empty RAG): ${e.message}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing documentation", e)
            Result.failure(e)
        }
    }
}
