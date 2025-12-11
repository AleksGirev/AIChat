package com.example.aichat.data.model

/**
 * Result of a token comparison test for different request lengths
 */
data class TokenComparisonResult(
    val requestType: RequestType,
    val userMessage: String,
    val estimatedRequestTokens: Int,
    val modelLimit: Int,
    val exceedsLimit: Boolean,
    val response: ChatResponse?,
    val error: String? = null,
    val isSuccess: Boolean = true,
    val responseTimeMs: Long = 0L
) {
    /**
     * Gets actual token usage from response
     */
    val actualRequestTokens: Int?
        get() = response?.usage?.promptTokens
    
    val actualResponseTokens: Int?
        get() = response?.usage?.completionTokens
    
    val actualTotalTokens: Int?
        get() = response?.usage?.totalTokens
    
    /**
     * Gets response content
     */
    val responseContent: String?
        get() = response?.choices?.firstOrNull()?.message?.content
    
    /**
     * Gets finish reason (may indicate truncation)
     */
    val finishReason: String?
        get() = response?.choices?.firstOrNull()?.finishReason
}

/**
 * Types of requests for comparison
 */
enum class RequestType(val displayName: String, val description: String) {
    SHORT("Короткий запрос", "Небольшой запрос, далеко от лимита модели"),
    LONG("Длинный запрос", "Большой запрос, но в пределах лимита"),
    EXCEEDS_LIMIT("Превышает лимит", "Запрос превышает контекстное окно модели")
}


