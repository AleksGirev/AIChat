package com.example.aichat.console.rag

import com.example.aichat.console.rag.model.Document
import com.example.aichat.console.rag.model.TextChunk
import java.text.BreakIterator
import java.util.*

/**
 * Splits text into chunks with configurable size and overlap
 * Supports multilingual text and preserves context via overlap
 */
class TextSplitter(
    private val chunkSize: Int = 768,  // Characters per chunk (default ~512 tokens)
    private val chunkOverlap: Int = 200, // Overlap in characters (~50 tokens)
    private val useTokenEstimate: Boolean = false, // If true, uses token estimation
    private val tokenToCharRatio: Double = 1.5 // Approximate ratio (1 token ≈ 1.5 chars for English)
) {
    /**
     * Splits a document into chunks
     * @param document Document to split
     * @return List of text chunks
     */
    fun splitDocument(document: Document): List<TextChunk> {
        return splitText(
            text = document.content,
            source = document.source,
            metadata = document.metadata
        )
    }
    
    /**
     * Splits text into chunks with overlap
     * Tries to split at sentence boundaries when possible
     */
    fun splitText(
        text: String,
        source: String,
        metadata: Map<String, String> = emptyMap()
    ): List<TextChunk> {
        if (text.isEmpty()) {
            return emptyList()
        }
        
        val chunks = mutableListOf<TextChunk>()
        var currentIndex = 0
        var chunkIndex = 0
        
        while (currentIndex < text.length) {
            val chunkEnd = calculateChunkEnd(text, currentIndex)
            val chunkText = text.substring(currentIndex, chunkEnd)
            
            // Estimate token count (rough approximation)
            val tokenCount = if (useTokenEstimate) {
                estimateTokenCount(chunkText)
            } else {
                null
            }
            
            chunks.add(
                TextChunk(
                    source = source,
                    content = chunkText.trim(),
                    chunkIndex = chunkIndex++,
                    startChar = currentIndex,
                    endChar = chunkEnd,
                    tokenCount = tokenCount,
                    metadata = metadata
                )
            )
            
            // Move to next chunk with overlap
            // Ensure we don't go backwards (negative index) or get stuck
            val nextIndex = chunkEnd - chunkOverlap
            if (nextIndex <= currentIndex || nextIndex < 0) {
                // If overlap would cause us to go backwards or negative, move forward by at least 1
                currentIndex = chunkEnd
            } else {
                currentIndex = nextIndex
            }
            
            // If we've reached the end, break
            if (currentIndex >= text.length) {
                break
            }
        }
        
        return chunks
    }
    
    /**
     * Calculates the end position of a chunk, trying to break at sentence boundaries
     */
    private fun calculateChunkEnd(text: String, startIndex: Int): Int {
        val maxEnd = minOf(startIndex + chunkSize, text.length)
        
        // If we're at the end of text, return it
        if (maxEnd >= text.length) {
            return text.length
        }
        
        // Try to find a sentence boundary near the target end
        val targetEnd = maxEnd
        val searchStart = maxOf(startIndex, targetEnd - chunkOverlap)
        
        // Use BreakIterator to find sentence boundaries (works for multiple languages)
        val breakIterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        breakIterator.setText(text)
        
        // Find the last sentence boundary before or at targetEnd
        var lastBoundary = startIndex
        var currentBoundary = breakIterator.next()
        
        while (currentBoundary != BreakIterator.DONE && currentBoundary <= targetEnd) {
            if (currentBoundary >= searchStart) {
                lastBoundary = currentBoundary
            }
            currentBoundary = breakIterator.next()
        }
        
        // If we found a good boundary, use it; otherwise use targetEnd
        return if (lastBoundary > startIndex && lastBoundary <= targetEnd) {
            lastBoundary
        } else {
            targetEnd
        }
    }
    
    /**
     * Estimates token count for a text (rough approximation)
     * Uses character count divided by average token length
     */
    private fun estimateTokenCount(text: String): Int {
        // Simple heuristic: average token is ~1.5 characters for English
        // For other languages, this might vary, but it's a reasonable approximation
        return (text.length / tokenToCharRatio).toInt()
    }
    
    companion object {
        /**
         * Creates a splitter optimized for token-based chunking
         */
        fun tokenBased(
            chunkTokens: Int = 512,
            overlapTokens: Int = 50,
            tokenToCharRatio: Double = 1.5
        ): TextSplitter {
            val chunkSize = (chunkTokens * tokenToCharRatio).toInt()
            val chunkOverlap = (overlapTokens * tokenToCharRatio).toInt()
            return TextSplitter(
                chunkSize = chunkSize,
                chunkOverlap = chunkOverlap,
                useTokenEstimate = true,
                tokenToCharRatio = tokenToCharRatio
            )
        }
        
        /**
         * Creates a splitter optimized for character-based chunking
         */
        fun characterBased(
            chunkChars: Int = 768,
            overlapChars: Int = 200
        ): TextSplitter {
            return TextSplitter(
                chunkSize = chunkChars,
                chunkOverlap = overlapChars,
                useTokenEstimate = false
            )
        }
    }
}


