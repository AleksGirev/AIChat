package com.example.aichat.console.rag

import com.example.aichat.console.rag.model.Document
import com.example.aichat.console.rag.model.SearchResult
import com.example.aichat.console.rag.model.TextChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Main RAG pipeline orchestrator
 * Coordinates document loading, chunking, embedding generation, and storage
 */
class RAGPipeline(
    private val documentLoader: DocumentLoader = DocumentLoader(),
    private val textSplitter: TextSplitter = TextSplitter(),
    private val embeddingGenerator: EmbeddingGenerator = EmbeddingGenerator(),
    private val vectorStore: VectorStore
) {
    private val _progress = MutableStateFlow<PipelineProgress?>(null)
    val progress: StateFlow<PipelineProgress?> = _progress
    
    /**
     * Processes documents from file paths and stores them in the vector store
     * @param paths List of file paths or directory paths
     * @param onProgress Optional callback for progress updates
     * @param isCancelled Optional cancellation check function
     * @param replaceExisting If true, deletes existing chunks from the same sources before indexing (default: true)
     * @return Result with number of chunks processed
     */
    suspend fun processDocuments(
        paths: List<String>,
        onProgress: ((PipelineProgress) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        replaceExisting: Boolean = true
    ): Result<Int> {
        return try {
            // Step 1: Load documents
            updateProgress(PipelineProgress.LoadingDocuments(0, paths.size))
            val documents = try {
                documentLoader.loadDocuments(paths)
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to load documents: ${e.message}", e))
            }
            
            if (documents.isEmpty()) {
                return Result.failure(Exception("No documents loaded from paths: $paths"))
            }
            
            updateProgress(PipelineProgress.LoadingDocuments(documents.size, documents.size))
            onProgress?.invoke(_progress.value!!)
            
            if (isCancelled()) {
                return Result.failure(Exception("Operation cancelled"))
            }
            
            // Step 2: Split documents into chunks
            updateProgress(PipelineProgress.SplittingChunks(0, documents.size))
            val allChunks = mutableListOf<TextChunk>()
            
            documents.forEachIndexed { index, document ->
                if (isCancelled()) {
                    return Result.failure(Exception("Operation cancelled"))
                }
                
                val chunks = textSplitter.splitDocument(document)
                allChunks.addAll(chunks)
                updateProgress(PipelineProgress.SplittingChunks(index + 1, documents.size))
                onProgress?.invoke(_progress.value!!)
            }
            
            if (allChunks.isEmpty()) {
                return Result.failure(Exception("No chunks generated from documents"))
            }
            
            // Step 3: Generate embeddings
            updateProgress(PipelineProgress.GeneratingEmbeddings(0, allChunks.size))
            val embeddings = embeddingGenerator.generateEmbeddingsCancellable(
                chunks = allChunks,
                onProgress = { current, total ->
                    updateProgress(PipelineProgress.GeneratingEmbeddings(current, total))
                    onProgress?.invoke(_progress.value!!)
                },
                isCancelled = isCancelled
            )
            
            if (embeddings.isEmpty()) {
                return Result.failure(Exception("No embeddings generated"))
            }
            
            // Step 4: Store chunks and embeddings
            updateProgress(PipelineProgress.StoringVectors(0, embeddings.size))
            vectorStore.storeChunks(
                chunks = allChunks,
                embeddings = embeddings,
                model = embeddingGenerator.model,
                replaceExisting = replaceExisting
            )
            
            updateProgress(PipelineProgress.Completed(embeddings.size))
            onProgress?.invoke(_progress.value!!)
            
            Result.success(embeddings.size)
        } catch (e: Exception) {
            updateProgress(PipelineProgress.Error(e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }
    
    /**
     * Processes a single document
     */
    suspend fun processDocument(
        path: String,
        onProgress: ((PipelineProgress) -> Unit)? = null,
        replaceExisting: Boolean = true
    ): Result<Int> {
        return processDocuments(listOf(path), onProgress, replaceExisting = replaceExisting)
    }
    
    /**
     * Searches for similar chunks using a query text
     * @param queryText Query text to search for
     * @param limit Maximum number of results
     * @param minSimilarity Minimum similarity threshold
     * @return List of search results
     */
    suspend fun search(
        queryText: String,
        limit: Int = 10,
        minSimilarity: Float = 0.0f
    ): Result<List<SearchResult>> {
        return try {
            // Generate embedding for query
            val queryEmbedding = embeddingGenerator.generateEmbedding(queryText)
                ?: return Result.failure(Exception("Failed to generate query embedding"))
            
            // Search vector store
            val results = vectorStore.search(queryEmbedding, limit, minSimilarity)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Searches using a pre-computed embedding
     */
    fun searchWithEmbedding(
        queryEmbedding: FloatArray,
        limit: Int = 10,
        minSimilarity: Float = 0.0f
    ): List<SearchResult> {
        return vectorStore.search(queryEmbedding, limit, minSimilarity)
    }
    
    /**
     * Gets statistics about the vector store
     */
    fun getStats(): VectorStoreStats {
        return vectorStore.getStats()
    }
    
    /**
     * Clears all data from the vector store
     */
    fun clear() {
        vectorStore.clear()
    }
    
    /**
     * Closes the vector store connection
     */
    fun close() {
        vectorStore.close()
    }
    
    private fun updateProgress(progress: PipelineProgress) {
        _progress.value = progress
    }
    
    companion object {
        /**
         * Creates a RAG pipeline with default components
         */
        fun create(
            dbPath: String,
            ollamaBaseUrl: String = "http://localhost:11434",
            ollamaModel: String = "nomic-embed-text",
            chunkSize: Int = 768,
            chunkOverlap: Int = 200
        ): RAGPipeline {
            val documentLoader = DocumentLoader()
            val textSplitter = TextSplitter(
                chunkSize = chunkSize,
                chunkOverlap = chunkOverlap
            )
            val embeddingGenerator = EmbeddingGenerator(
                baseUrl = ollamaBaseUrl,
                model = ollamaModel
            )
            val vectorStore = VectorStore(dbPath)
            
            return RAGPipeline(
                documentLoader = documentLoader,
                textSplitter = textSplitter,
                embeddingGenerator = embeddingGenerator,
                vectorStore = vectorStore
            )
        }
    }
}

/**
 * Represents the progress of the RAG pipeline
 */
sealed class PipelineProgress {
    data class LoadingDocuments(val loaded: Int, val total: Int) : PipelineProgress()
    data class SplittingChunks(val processed: Int, val total: Int) : PipelineProgress()
    data class GeneratingEmbeddings(val generated: Int, val total: Int) : PipelineProgress()
    data class StoringVectors(val stored: Int, val total: Int) : PipelineProgress()
    data class Completed(val chunksStored: Int) : PipelineProgress()
    data class Error(val message: String) : PipelineProgress()
    
    fun getProgressPercentage(): Int {
        return when (this) {
            is LoadingDocuments -> if (total > 0) (loaded * 100 / total) else 0
            is SplittingChunks -> if (total > 0) (processed * 100 / total) else 0
            is GeneratingEmbeddings -> if (total > 0) (generated * 100 / total) else 0
            is StoringVectors -> if (total > 0) (stored * 100 / total) else 0
            is Completed -> 100
            is Error -> 0
        }
    }
    
    fun getStatusMessage(): String {
        return when (this) {
            is LoadingDocuments -> "Loading documents: $loaded/$total"
            is SplittingChunks -> "Splitting chunks: $processed/$total documents"
            is GeneratingEmbeddings -> "Generating embeddings: $generated/$total chunks"
            is StoringVectors -> "Storing vectors: $stored/$total"
            is Completed -> "Completed! Stored $chunksStored chunks"
            is Error -> "Error: $message"
        }
    }
}

