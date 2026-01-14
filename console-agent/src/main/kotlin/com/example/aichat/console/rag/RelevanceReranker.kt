package com.example.aichat.console.rag

import com.example.aichat.console.rag.model.SearchResult

/**
 * Second-stage reranker/filter for RAG search results
 * Filters and reranks results after vector similarity search
 */
class RelevanceReranker {
    
    /**
     * Reranks and filters search results based on relevance criteria
     * 
     * @param results Initial search results from vector store
     * @param rerankThreshold Additional threshold for reranking (0.0 to 1.0)
     * @param maxResults Maximum number of results to return after reranking
     * @return Filtered and reranked results
     */
    fun rerank(
        results: List<SearchResult>,
        rerankThreshold: Float = 0.0f,
        maxResults: Int = 10
    ): List<SearchResult> {
        if (results.isEmpty()) {
            return emptyList()
        }
        
        // Apply additional filtering based on threshold
        val filtered = if (rerankThreshold > 0.0f) {
            results.filter {
                it.similarity >= rerankThreshold

            }

        } else {
            results
        }
        
        // Additional reranking logic can be added here:
        // - Boost recent documents
        // - Boost documents with certain metadata
        // - Use cross-encoder model for better ranking
        // - Consider query-document overlap
        
        // For now, we just sort by similarity (already sorted, but ensure it)
        val reranked = filtered.sortedByDescending { it.similarity }
        
        // Limit results
        return reranked.take(maxResults)
    }
    
    /**
     * Calculates relevance score combining multiple factors
     * Can be extended with more sophisticated scoring
     */
    private fun calculateRelevanceScore(result: SearchResult): Float {
        var score = result.similarity
        
        // Example: boost score based on chunk length (longer chunks might be more informative)
        val chunkLength = result.chunk.content.length
        val lengthBoost = when {
            chunkLength > 500 -> 0.05f  // Boost longer chunks slightly
            chunkLength < 100 -> -0.05f // Penalize very short chunks
            else -> 0.0f
        }
        
        return (score + lengthBoost).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Advanced reranking using calculated relevance scores
     */
    fun rerankWithScoring(
        results: List<SearchResult>,
        rerankThreshold: Float = 0.0f,
        maxResults: Int = 10
    ): List<SearchResult> {
        if (results.isEmpty()) {
            return emptyList()
        }
        
        // Calculate relevance scores
        val scoredResults = results.map { result ->
            val relevanceScore = calculateRelevanceScore(result)
            ScoredSearchResult(result, relevanceScore)
        }
        
        // Filter by threshold
        val filtered = if (rerankThreshold > 0.0f) {
            scoredResults.filter { it.relevanceScore >= rerankThreshold }
        } else {
            scoredResults
        }
        
        // Sort by relevance score
        val sorted = filtered.sortedByDescending { it.relevanceScore }
        
        // Return original results in new order
        return sorted.take(maxResults).map { it.searchResult }
    }
    
    /**
     * Wrapper for search result with calculated relevance score
     */
    private data class ScoredSearchResult(
        val searchResult: SearchResult,
        val relevanceScore: Float
    )
}




