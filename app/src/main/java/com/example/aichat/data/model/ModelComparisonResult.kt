package com.example.aichat.data.model

/**
 * Result of a model comparison request
 */
data class ModelComparisonResult(
    val modelName: String,
    val responseContent: String?,
    val responseTimeMs: Long,
    val tokenCount: Int,
    val cost: Double,
    val error: String? = null,
    val isSuccess: Boolean = true
) {
    /**
     * Formats response time as a human-readable string
     */
    fun getFormattedResponseTime(): String {
        return when {
            responseTimeMs < 1000 -> "${responseTimeMs}ms"
            else -> String.format("%.2fs", responseTimeMs / 1000.0)
        }
    }
    
    /**
     * Formats cost as a human-readable string
     */
    fun getFormattedCost(): String {
        return when {
            cost == 0.0 -> "Free"
            cost < 0.01 -> String.format("$%.4f", cost)
            else -> String.format("$%.3f", cost)
        }
    }
}

/**
 * Information about an AI model
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String = "huggingface"
)

/**
 * Represents a comparison message with user prompt and results
 */
data class ComparisonMessage(
    val id: String,
    val userPrompt: String,
    val results: List<ModelComparisonResult>,
    val timestamp: Long = System.currentTimeMillis()
)

