package com.example.aichat.data.util

import com.example.aichat.data.model.Usage

/**
 * Utility class for calculating costs based on model usage
 * Uses OpenRouter pricing structure where available
 */
object ModelCostCalculator {
    
    /**
     * Pricing map for popular models (per 1M tokens)
     * Format: (inputPricePerMillion, outputPricePerMillion)
     * Prices are in USD
     */
    private val modelPricing = mapOf(
        // Free models
        "amazon/nova-2-lite-v1:free" to (0.0 to 0.0),
        "yandexgpt" to (0.0 to 0.0), // YandexGPT pricing - update if needed
        "meta-llama/llama-3.2-3b-instruct:free" to (0.0 to 0.0),
        "google/gemma-2-2b-it:free" to (0.0 to 0.0),
        
        // Paid models (example pricing - should be updated with actual OpenRouter pricing)
        "openai/gpt-4o-mini" to (0.15 to 0.6),
        "openai/gpt-4o" to (2.5 to 10.0),
        "anthropic/claude-3.5-sonnet" to (3.0 to 15.0),
        "google/gemini-pro-1.5" to (1.25 to 5.0),
        
        // HuggingFace models (most are free through OpenRouter)
        "huggingface/meta-llama/Llama-2-7b-chat-hf" to (0.0 to 0.0),
        "huggingface/mistralai/Mistral-7B-Instruct-v0.1" to (0.0 to 0.0),
        "huggingface/google/flan-t5-large" to (0.0 to 0.0),
    )
    
    /**
     * Calculates the cost for a model request based on token usage
     * 
     * @param modelName The name of the model
     * @param usage The usage information containing token counts
     * @return The calculated cost in USD
     */
    fun calculateCost(modelName: String, usage: Usage?): Double {
        if (usage == null) return 0.0
        
        val pricing = modelPricing[modelName] ?: return 0.0 // Default to free if not found
        
        val inputPricePerMillion = pricing.first
        val outputPricePerMillion = pricing.second
        
        val inputCost = (usage.promptTokens / 1_000_000.0) * inputPricePerMillion
        val outputCost = (usage.completionTokens / 1_000_000.0) * outputPricePerMillion
        
        return inputCost + outputCost
    }
    
    /**
     * Checks if a model is free
     */
    fun isFree(modelName: String): Boolean {
        val pricing = modelPricing[modelName] ?: return true // Default to free if not found
        return pricing.first == 0.0 && pricing.second == 0.0
    }
}

