package com.example.aichat.data.util

import com.example.aichat.data.model.ChatMessage

/**
 * Utility class for counting tokens in text and chat messages
 * Uses approximation algorithms since tiktoken is not available for Kotlin/Android
 * 
 * For OpenAI models, a common approximation is:
 * - English text: ~4 characters per token
 * - More accurate: count words and add overhead for special tokens
 */
object TokenCounter {
    
    /**
     * Estimates the number of tokens in a text string
     * This is an approximation - actual tokenization depends on the model
     * 
     * @param text The text to count tokens for
     * @return Estimated token count
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        // Simple approximation: tokens ≈ characters / 4 (for English)
        // This is a rough estimate, actual tokenization varies by model
        val charCount = text.length
        
        // More accurate approximation:
        // - Count words (split by whitespace)
        // - Each word is roughly 1.3 tokens on average
        // - Add overhead for punctuation and special characters
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordBasedEstimate = (words.size * 1.3).toInt()
        
        // Character-based estimate (more conservative)
        val charBasedEstimate = (charCount / 4.0).toInt()
        
        // Use the average of both methods for better accuracy
        // Add 2 tokens overhead for message formatting
        return ((wordBasedEstimate + charBasedEstimate) / 2.0).toInt() + 2
    }
    
    /**
     * Estimates tokens for a single chat message
     * Includes overhead for role and formatting
     * 
     * @param message The chat message
     * @return Estimated token count including role overhead
     */
    fun estimateMessageTokens(message: ChatMessage): Int {
        // Base content tokens
        val contentTokens = estimateTokens(message.content)
        
        // Role overhead: "user", "assistant", "system" + formatting ≈ 4 tokens
        val roleOverhead = 4
        
        return contentTokens + roleOverhead
    }
    
    /**
     * Estimates total tokens for a list of chat messages
     * Includes overhead for message formatting and system prompts
     * 
     * @param messages List of chat messages
     * @return Total estimated token count
     */
    fun estimateRequestTokens(messages: List<ChatMessage>): Int {
        if (messages.isEmpty()) return 0
        
        // Count tokens for each message
        val messageTokens = messages.sumOf { estimateMessageTokens(it) }
        
        // Add overhead for request formatting (JSON structure, etc.)
        // Approximately 2-3 tokens per message for formatting
        val formattingOverhead = messages.size * 2
        
        return messageTokens + formattingOverhead
    }
    
    /**
     * Estimates tokens for a response text
     * 
     * @param responseText The response text
     * @return Estimated token count
     */
    fun estimateResponseTokens(responseText: String): Int {
        return estimateTokens(responseText)
    }
    
    /**
     * Gets model context window limits (in tokens)
     * These are approximate limits for common models
     */
    fun getModelContextLimit(modelName: String): Int {
        return when {
            modelName.contains("gpt-4o", ignoreCase = true) -> 128_000
            modelName.contains("gpt-4", ignoreCase = true) -> 8_192
            modelName.contains("gpt-3.5", ignoreCase = true) -> 16_385
            modelName.contains("claude-3.5", ignoreCase = true) -> 200_000
            modelName.contains("claude-3", ignoreCase = true) -> 200_000
            modelName.contains("nova", ignoreCase = true) -> 128_000
            modelName.contains("yandex", ignoreCase = true) -> 8_000
            modelName.contains("llama", ignoreCase = true) -> 32_768
            modelName.contains("gemini", ignoreCase = true) -> 1_000_000
            else -> 8_192 // Default conservative limit
        }
    }
    
    /**
     * Checks if estimated tokens exceed model limit
     * 
     * @param messages List of chat messages
     * @param modelName The model name
     * @return Pair of (exceedsLimit: Boolean, estimatedTokens: Int, limit: Int)
     */
    fun checkTokenLimit(
        messages: List<ChatMessage>,
        modelName: String
    ): Triple<Boolean, Int, Int> {
        val estimatedTokens = estimateRequestTokens(messages)
        val limit = getModelContextLimit(modelName)
        val exceedsLimit = estimatedTokens > limit
        
        return Triple(exceedsLimit, estimatedTokens, limit)
    }
}

