package com.example.aichat.console.rag.model

import java.io.File

/**
 * Represents a loaded document with its source and content
 */
data class Document(
    val source: String,  // File path or identifier
    val content: String, // Extracted text content
    val metadata: Map<String, String> = emptyMap() // Additional metadata (e.g., file type, size)
)

/**
 * Represents a text chunk with its position and metadata
 */
data class TextChunk(
    val id: Long? = null,  // Database ID (null for new chunks)
    val source: String,    // Source document path
    val content: String,   // Chunk text content
    val chunkIndex: Int,   // Index of chunk in the document
    val startChar: Int,    // Start character position in original document
    val endChar: Int,      // End character position in original document
    val tokenCount: Int? = null, // Estimated token count
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Represents an embedding vector with its associated chunk
 */
data class Embedding(
    val chunkId: Long,
    val vector: FloatArray, // Embedding vector
    val model: String       // Model used to generate the embedding
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Embedding
        
        if (chunkId != other.chunkId) return false
        if (!vector.contentEquals(other.vector)) return false
        if (model != other.model) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + model.hashCode()
        return result
    }
}

/**
 * Represents a search result with similarity score
 */
data class SearchResult(
    val chunk: TextChunk,
    val embedding: Embedding,
    val similarity: Float  // Cosine similarity score
)


