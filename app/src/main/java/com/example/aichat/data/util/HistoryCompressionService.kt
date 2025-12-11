package com.example.aichat.data.util

import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.ui.model.UiMessage

/**
 * Service for compressing chat history by creating summaries of old messages
 * Every COMPRESSION_THRESHOLD messages, the oldest messages are summarized and replaced
 */
class HistoryCompressionService(
    private val chatRepository: ChatRepository
) {
    
    companion object {
        const val COMPRESSION_THRESHOLD = 5 // Compress every 5 messages
    }
    
    /**
     * Checks if compression should be triggered before sending a new message
     * This is called BEFORE adding the new user message to check if we've reached the threshold
     * 
     * @param allMessages All messages in the conversation (including summaries), excluding the new message
     * @return true if compression should be performed before sending the next message
     */
    fun shouldCompressBeforeSending(allMessages: List<UiMessage>): Boolean {
        val nonSummaryMessages = allMessages.filter { !it.isSummary }
        return nonSummaryMessages.size >= COMPRESSION_THRESHOLD
    }
    
    /**
     * Compresses all previous messages (excluding the current one) and returns the summary
     * This is called when threshold is reached BEFORE sending a new message
     * 
     * @param allMessages All messages in the conversation (including summaries), excluding the new message
     * @param model The model to use for summary generation
     * @return Pair of (summary message, list of compressed message IDs) or null if compression failed
     */
    suspend fun compressAllPreviousMessages(
        allMessages: List<UiMessage>,
        model: String = "amazon/nova-2-lite-v1:free"
    ): Pair<UiMessage, List<String>>? {
        if (allMessages.isEmpty()) return null
        
        // Get all non-summary messages (these are what we'll compress)
        val nonSummaryMessages = allMessages.filter { !it.isSummary }
        
        // Find all summaries that exist (to include in compression)
        val existingSummaries = allMessages.filter { it.isSummary }
        
        // Build list of all content to compress: summaries + all non-summary messages
        val allContentToCompress = existingSummaries + nonSummaryMessages
        
        if (allContentToCompress.isEmpty()) return null
        
        // Generate summary including previous summaries
        val summary = generateSummary(allContentToCompress, existingSummaries.isNotEmpty(), model) 
            ?: return null
        
        // Collect all IDs that will be compressed (from summaries and messages)
        val allCompressedIds = existingSummaries.flatMap { it.compressedMessageIds } + nonSummaryMessages.map { it.id }
        
        // Create summary message
        val summaryMessage = UiMessage(
            id = "summary_${System.currentTimeMillis()}",
            content = summary,
            isUser = false,
            timestamp = allContentToCompress.first().timestamp, // Use timestamp of first compressed item
            isSummary = true,
            compressedMessageIds = allCompressedIds.distinct()
        )
        
        return Pair(summaryMessage, allCompressedIds.distinct())
    }
    
    /**
     * Checks if compression is needed and performs it if necessary
     * 
     * @param allMessages All messages in the conversation (including summaries)
     * @param model The model to use for summary generation
     * @return Pair of (compressed messages list, whether compression was performed)
     */
    suspend fun compressIfNeeded(
        allMessages: List<UiMessage>,
        model: String = "amazon/nova-2-lite-v1:free"
    ): Pair<List<UiMessage>, Boolean> {
        // Get messages that should be compressed:
        // - Oldest non-summary messages up to COMPRESSION_THRESHOLD
        // - Plus any summaries that come before them (to compress summaries recursively)
        val nonSummaryMessages = allMessages.filter { !it.isSummary }
        
        // Check if we have enough non-summary messages to compress
        if (nonSummaryMessages.size < COMPRESSION_THRESHOLD) {
            return Pair(allMessages, false)
        }
        
        // Find the oldest COMPRESSION_THRESHOLD non-summary messages
        val messagesToCompress = nonSummaryMessages.take(COMPRESSION_THRESHOLD)
        val compressedIds = messagesToCompress.map { it.id }.toSet()
        
        // Find all summaries that come before the messages we're compressing
        // These should be included in the new summary (recursive compression)
        val firstCompressedTimestamp = messagesToCompress.first().timestamp
        val summariesToInclude = allMessages.filter { summary ->
            summary.isSummary && summary.timestamp < firstCompressedTimestamp
        }
        
        // Check if these messages are already compressed (shouldn't happen, but safety check)
        val alreadyCompressed = allMessages.any { summary ->
            summary.isSummary && summary.compressedMessageIds.any { it in compressedIds }
        }
        
        if (alreadyCompressed) {
            return Pair(allMessages, false)
        }
        
        // Build list of all content to compress: summaries + messages
        val allContentToCompress = summariesToInclude + messagesToCompress
        
        // Generate summary including previous summaries
        val summary = generateSummary(allContentToCompress, summariesToInclude.isNotEmpty(), model) 
            ?: return Pair(allMessages, false)
        
        // Collect all IDs that will be compressed (from summaries and messages)
        val allCompressedIds = summariesToInclude.flatMap { it.compressedMessageIds } + compressedIds
        
        // Create summary message
        val summaryMessage = UiMessage(
            id = "summary_${System.currentTimeMillis()}",
            content = summary,
            isUser = false,
            timestamp = allContentToCompress.first().timestamp, // Use timestamp of first compressed item
            isSummary = true,
            compressedMessageIds = allCompressedIds.distinct()
        )
        
        // Build new message list: replace compressed messages and summaries with new summary, maintaining order
        val compressedMessages = mutableListOf<UiMessage>()
        var summaryInserted = false
        val summariesToRemoveIds = summariesToInclude.map { it.id }.toSet()
        
        for (message in allMessages) {
            if (message.id in compressedIds || message.id in summariesToRemoveIds) {
                // Skip compressed message or old summary, insert new summary once at the position of first compressed item
                if (!summaryInserted) {
                    compressedMessages.add(summaryMessage)
                    summaryInserted = true
                }
            } else {
                compressedMessages.add(message)
            }
        }
        
        return Pair(compressedMessages, true)
    }
    
    /**
     * Generates a summary of the given messages using the LLM
     * 
     * @param messages Messages to summarize (can include both regular messages and summaries)
     * @param includesPreviousSummaries Whether the messages include previous summaries (for better prompt)
     * @param model The model to use for summary generation
     */
    private suspend fun generateSummary(
        messages: List<UiMessage>, 
        includesPreviousSummaries: Boolean,
        model: String
    ): String? {
        if (messages.isEmpty()) return null
        
        // Build conversation context for summary generation
        // If there are previous summaries, we need to handle them specially
        val conversationParts = mutableListOf<String>()
        
        if (includesPreviousSummaries) {
            // Separate summaries and regular messages
            val summaries = messages.filter { it.isSummary }
            val regularMessages = messages.filter { !it.isSummary }
            
            if (summaries.isNotEmpty()) {
                conversationParts.add("Предыдущие резюме диалога:")
                summaries.forEach { summary ->
                    conversationParts.add("Резюме: ${summary.content}")
                }
            }
            
            if (regularMessages.isNotEmpty()) {
                conversationParts.add("\nПоследующие сообщения диалога:")
                regularMessages.forEach { message ->
                    conversationParts.add("${if (message.isUser) "User" else "Assistant"}: ${message.content}")
                }
            }
        } else {
            // Regular messages only
            messages.forEach { message ->
                conversationParts.add("${if (message.isUser) "User" else "Assistant"}: ${message.content}")
            }
        }
        
        val conversationText = conversationParts.joinToString("\n")
        
        val summaryPrompt = if (includesPreviousSummaries) {
            """
            Объедини предыдущие резюме диалога с новыми сообщениями в одно краткое резюме.
            Сохрани всю ключевую информацию и контекст для продолжения разговора.
            Резюме должно быть на русском языке и содержать только важные детали.
            
            $conversationText
            
            Объединенное резюме:
            """.trimIndent()
        } else {
            """
            Создай краткое резюме следующего диалога, сохраняя ключевую информацию и контекст для продолжения разговора.
            Резюме должно быть на русском языке и содержать только важные детали.
            
            Диалог:
            $conversationText
            
            Резюме:
            """.trimIndent()
        }
        
        val summaryMessages = listOf(
            ChatMessage(role = "user", content = summaryPrompt)
        )
        
        val result = chatRepository.sendChatRequest(
            messages = summaryMessages,
            model = model,
            temperature = 0.3 // Lower temperature for more consistent summaries
        )
        
        return result.getOrNull()?.choices?.firstOrNull()?.message?.content
    }
    
    /**
     * Builds conversation history for API requests, using summaries instead of original messages
     * when appropriate. Summary messages are used to replace the compressed messages they represent.
     * 
     * @param messages All messages including summaries (summary messages are hidden from user)
     * @return Pair of (conversation history, summary context for system message if needed)
     */
    fun buildConversationHistory(messages: List<UiMessage>): Pair<List<ChatMessage>, String?> {
        val result = mutableListOf<ChatMessage>()
        val processedIds = mutableSetOf<String>()
        val allSummaries = mutableListOf<UiMessage>()
        
        // First pass: collect all summaries and mark their compressed message IDs as processed
        for (message in messages) {
            if (message.isSummary) {
                allSummaries.add(message)
                // Mark all compressed message IDs as processed so they won't be added again
                processedIds.addAll(message.compressedMessageIds)
            }
        }
        
        // Second pass: add regular messages (user/assistant) that weren't compressed
        for (message in messages) {
            // Skip if already processed (as part of compressed messages that were replaced by a summary)
            if (message.id in processedIds) continue
            
            if (!message.isSummary) {
                // Regular message (user or assistant) - add it normally
                result.add(
                    ChatMessage(
                        role = if (message.isUser) "user" else "assistant",
                        content = message.content
                    )
                )
                processedIds.add(message.id)
            }
        }
        
        // All summaries should be converted to context for system message
        // This prevents "first message from assistant" error and ensures conversation continuity
        val summaryContext = if (allSummaries.isNotEmpty()) {
            // Sort summaries by timestamp to maintain chronological order
            allSummaries.sortedBy { it.timestamp }
                .joinToString("\n\n") { it.content }
        } else {
            null
        }
        
        return Pair(result, summaryContext)
    }
}
