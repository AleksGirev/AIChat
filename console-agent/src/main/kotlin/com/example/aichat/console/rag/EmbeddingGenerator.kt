package com.example.aichat.console.rag

import com.example.aichat.console.rag.model.TextChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Generates embeddings using Ollama Embedding API
 * POST http://localhost:11434/api/embeddings
 */
class EmbeddingGenerator(
    private val baseUrl: String = "http://localhost:11434",
    val model: String = "nomic-embed-text",
    private val timeoutSeconds: Int = 60
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
    
    private val mediaType = "application/json".toMediaType()
    
    @Serializable
    private data class EmbeddingRequest(
        val model: String,
        val prompt: String
    )
    
    @Serializable
    private data class EmbeddingResponse(
        val embedding: List<Double>
    )
    
    /**
     * Generates an embedding for a text chunk
     * @param chunk Text chunk to embed
     * @return FloatArray embedding vector, or null if generation fails
     */
    suspend fun generateEmbedding(chunk: TextChunk): FloatArray? {
        return generateEmbedding(chunk.content)
    }
    
    /**
     * Generates an embedding for a text string
     * @param text Text to embed
     * @return FloatArray embedding vector, or null if generation fails
     */
    suspend fun generateEmbedding(text: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            val requestBody = EmbeddingRequest(
                model = model,
                prompt = text
            )
            
            val jsonBody = json.encodeToString(EmbeddingRequest.serializer(), requestBody)
            
            val request = Request.Builder()
                .url("$baseUrl/api/embeddings")
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("Ollama API error ${response.code}: $errorBody")
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val embeddingResponse = json.decodeFromString<EmbeddingResponse>(responseBody)
            
            // Convert List<Double> to FloatArray
            embeddingResponse.embedding.map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            throw Exception("Failed to generate embedding: ${e.message}", e)
        }
    }
    
    /**
     * Generates embeddings for multiple chunks in batch
     * Note: Ollama API doesn't support true batching, so this processes sequentially
     * @param chunks List of text chunks
     * @param onProgress Optional callback for progress updates
     * @return Map of chunk index to embedding vector
     */
    suspend fun generateEmbeddings(
        chunks: List<TextChunk>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Map<Int, FloatArray> = withContext(Dispatchers.IO) {
        val embeddings = mutableMapOf<Int, FloatArray>()
        
        chunks.forEachIndexed { index, chunk ->
            try {
                val embedding = generateEmbedding(chunk)
                if (embedding != null) {
                    embeddings[index] = embedding
                }
                onProgress?.invoke(index + 1, chunks.size)
            } catch (e: Exception) {
                // Log error but continue with other chunks
                System.err.println("Failed to generate embedding for chunk $index: ${e.message}")
            }
        }
        
        embeddings
    }
    
    /**
     * Generates embeddings for multiple chunks with cancellation support
     */
    suspend fun generateEmbeddingsCancellable(
        chunks: List<TextChunk>,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): Map<Int, FloatArray> = withContext(Dispatchers.IO) {
        val embeddings = mutableMapOf<Int, FloatArray>()
        
        for ((index, chunk) in chunks.withIndex()) {
            if (isCancelled()) {
                break
            }
            
            try {
                val embedding = generateEmbedding(chunk)
                if (embedding != null) {
                    embeddings[index] = embedding
                }
                onProgress?.invoke(index + 1, chunks.size)
            } catch (e: Exception) {
                System.err.println("Failed to generate embedding for chunk $index: ${e.message}")
            }
        }
        
        embeddings
    }
}

