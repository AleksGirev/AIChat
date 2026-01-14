package com.example.aichat.console.prreview

import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for retrieving relevant documentation context using RAG
 */
class RagContextService(
    private val ragPipeline: RAGPipeline
) {
    /**
     * Retrieves top relevant documentation chunks for a given file path
     * @param filePath The path of the changed file
     * @param limit Maximum number of chunks to retrieve (default: 3)
     * @return List of relevant documentation chunks
     */
    suspend fun getRelevantContext(filePath: String, limit: Int = 3): Result<List<DocumentationChunk>> = withContext(Dispatchers.IO) {
        try {
            // Build query from file path and common keywords
            val query = buildQueryFromFilePath(filePath)
            
            // Search RAG index with lower similarity threshold to get more results
            // Try with 0.0f first, then filter if needed
            var searchResult = ragPipeline.search(query, limit = limit * 2, minSimilarity = 0.0f)
            
            // If no results, try a more general query
            if (searchResult.isSuccess && searchResult.getOrThrow().isEmpty()) {
                val generalQuery = filePath.split("/").lastOrNull() ?: filePath
                searchResult = ragPipeline.search(generalQuery, limit = limit, minSimilarity = 0.0f)
            }
            
            // Final search with original parameters if still no results
            if (searchResult.isSuccess && searchResult.getOrThrow().isEmpty()) {
                searchResult = ragPipeline.search(query, limit = limit, minSimilarity = 0.0f)
            }
            
            if (searchResult.isFailure) {
                return@withContext Result.failure(
                    searchResult.exceptionOrNull() ?: Exception("RAG search failed")
                )
            }
            
            val results = searchResult.getOrThrow()
            
            // Convert to DocumentationChunk
            val chunks = results.map { result ->
                DocumentationChunk(
                    source = result.chunk.source,
                    content = result.chunk.content,
                    similarity = result.similarity
                )
            }
            
            Result.success(chunks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Retrieves general project documentation (architecture, style guide, etc.)
     * @param limit Maximum number of chunks to retrieve (default: 5)
     * @return List of relevant documentation chunks
     */
    suspend fun getGeneralContext(limit: Int = 5): Result<List<DocumentationChunk>> = withContext(Dispatchers.IO) {
        try {
            // Query for general project documentation
            val queries = listOf(
                "architecture MVVM clean architecture",
                "style guide coding conventions Kotlin",
                "error handling Result type",
                "coroutines StateFlow best practices",
                "Jetpack Compose patterns"
            )
            
            val allChunks = mutableListOf<DocumentationChunk>()
            
            for (query in queries) {
                val searchResult = ragPipeline.search(query, limit = 2, minSimilarity = 0.2f)
                if (searchResult.isSuccess) {
                    val results = searchResult.getOrThrow()
                    results.forEach { result ->
                        // Avoid duplicates
                        if (allChunks.none { it.source == result.chunk.source && it.content == result.chunk.content }) {
                            allChunks.add(
                                DocumentationChunk(
                                    source = result.chunk.source,
                                    content = result.chunk.content,
                                    similarity = result.similarity
                                )
                            )
                        }
                    }
                }
            }
            
            // Sort by similarity and take top results
            val sortedChunks = allChunks.sortedByDescending { it.similarity }.take(limit)
            
            Result.success(sortedChunks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Builds a search query from a file path
     * Extracts relevant keywords from the path structure
     */
    private fun buildQueryFromFilePath(filePath: String): String {
        val parts = filePath.split("/")
        val fileName = parts.lastOrNull() ?: filePath
        
        // Extract module/package information
        val moduleKeywords = mutableListOf<String>()
        
        // Check for common patterns
        when {
            filePath.contains("ui/") || filePath.contains("viewmodel/") -> {
                moduleKeywords.add("UI ViewModel MVVM")
                moduleKeywords.add("Jetpack Compose")
            }
            filePath.contains("repository/") -> {
                moduleKeywords.add("repository pattern")
                moduleKeywords.add("data layer")
            }
            filePath.contains("data/") -> {
                moduleKeywords.add("data layer")
                moduleKeywords.add("Room database")
            }
            filePath.contains("di/") -> {
                moduleKeywords.add("dependency injection")
                moduleKeywords.add("Koin")
            }
            filePath.contains("mcp/") -> {
                moduleKeywords.add("MCP Model Context Protocol")
            }
            filePath.contains("rag/") -> {
                moduleKeywords.add("RAG retrieval augmented generation")
            }
        }
        
        // Extract file type keywords
        when {
            fileName.endsWith("ViewModel.kt") -> moduleKeywords.add("ViewModel StateFlow")
            fileName.endsWith("Repository.kt") -> moduleKeywords.add("Repository pattern")
            fileName.endsWith("Screen.kt") -> moduleKeywords.add("Compose Screen")
            fileName.endsWith("Dao.kt") -> moduleKeywords.add("Room DAO database")
            fileName.endsWith("Entity.kt") -> moduleKeywords.add("Room Entity")
        }
        
        // Combine keywords
        val query = if (moduleKeywords.isNotEmpty()) {
            moduleKeywords.joinToString(" ") + " " + fileName
        } else {
            fileName
        }
        
        return query
    }
}

/**
 * Documentation chunk model
 */
data class DocumentationChunk(
    val source: String,
    val content: String,
    val similarity: Float
)
