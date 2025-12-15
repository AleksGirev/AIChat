package com.example.aichat.data.example

import com.example.aichat.data.Config
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Example usage of ChatRepository
 * 
 * This file demonstrates how to use the data layer to send requests to LLM
 * and receive responses. This is for reference only and can be deleted.
 */
//object ChatRepositoryUsageExample {
//
//    /**
//     * Example 1: Simple message sending
//     */
//    fun exampleSimpleMessage() {
//        // Initialize network dependencies
//        val networkDeps = NetworkModule.createNetworkDependencies()
//
//        // Create repository with API key
//        val repository = ChatRepository(
//            apiService = networkDeps.apiService,
//            apiKey = Config.OPENAI_API_KEY, // Replace with your actual API key
//            gson = networkDeps.gson
//        )
//
//        // Send a message
//        CoroutineScope(Dispatchers.Main).launch {
//            val result = repository.sendMessage(
//                userMessage = "Hello, how are you?"
//            )
//
//            result.onSuccess { response ->
//                println("Response: $response")
//            }.onFailure { error ->
//                println("Error: ${error.message}")
//            }
//        }
//    }
//
//    /**
//     * Example 2: Conversation with history
//     */
//    fun exampleWithConversationHistory() {
//        val networkDeps = NetworkModule.createNetworkDependencies()
//        val repository = ChatRepository(
//            apiService = networkDeps.apiService,
//            apiKey = Config.OPENAI_API_KEY,
//            gson = networkDeps.gson
//        )
//
//        // Build conversation history
//        val conversationHistory = listOf(
//            ChatMessage(role = "user", content = "What is Android?"),
//            ChatMessage(role = "assistant", content = "Android is a mobile operating system...")
//        )
//
//        CoroutineScope(Dispatchers.Main).launch {
//            val result = repository.sendMessage(
//                userMessage = "Tell me more about it",
//                conversationHistory = conversationHistory
//            )
//
//            result.onSuccess { response ->
//                println("Response: $response")
//            }.onFailure { error ->
//                println("Error: ${error.message}")
//            }
//        }
//    }
//
//    /**
//     * Example 3: Advanced request with custom parameters
//     */
//    fun exampleAdvancedRequest() {
//        val networkDeps = NetworkModule.createNetworkDependencies()
//        val repository = ChatRepository(
//            apiService = networkDeps.apiService,
//            apiKey = Config.OPENAI_API_KEY,
//            gson = networkDeps.gson
//        )
//
//        val messages = listOf(
//            ChatMessage(role = "user", content = "Write a short poem about coding")
//        )
//
//        CoroutineScope(Dispatchers.Main).launch {
//            val result = repository.sendChatRequest(
//                messages = messages,
//                model = "gpt-3.5-turbo",
//                maxTokens = 150,
//                temperature = 0.7
//            )
//
//            result.onSuccess { chatResponse ->
//                val assistantMessage = chatResponse.choices.firstOrNull()?.message?.content
//                val usage = chatResponse.usage
//
//                println("Response: $assistantMessage")
//                println("Tokens used: ${usage?.totalTokens}")
//            }.onFailure { error ->
//                println("Error: ${error.message}")
//            }
//        }
//    }
//}



