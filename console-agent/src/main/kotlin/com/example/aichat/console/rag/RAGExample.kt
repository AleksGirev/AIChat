package com.example.aichat.console.rag

import com.example.aichat.console.ConsoleConfig
import kotlinx.coroutines.runBlocking

/**
 * Example usage of the RAG pipeline
 * 
 * Prerequisites:
 * 1. Install Ollama: https://ollama.ai
 * 2. Pull an embedding model:
 *    ```bash
 *    ollama pull nomic-embed-text
 *    # or
 *    ollama pull mxbai-embed-large
 *    ```
 * 3. Start Ollama (if not running as a service):
 *    ```bash
 *    ollama serve
 *    ```
 * 
 * Usage:
 * ```bash
 * ./gradlew :console-agent:run --args="rag index /path/to/documents"
 * ./gradlew :console-agent:run --args="rag search 'your query'"
 * ```
 */
object RAGExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }
        
        // Skip "rag" prefix if present
        val actualArgs = if (args[0] == "rag" && args.size > 1) {
            args.drop(1).toTypedArray()
        } else {
            args
        }
        
        if (actualArgs.isEmpty()) {
            printUsage()
            return
        }
        
        val command = actualArgs[0]
        
        when (command) {
            "index" -> {
                if (actualArgs.size < 2) {
                    println("Error: Please provide document paths")
                    printUsage()
                    return
                }
                // Remove quotes from paths if present
                val paths = actualArgs.drop(1).map { path ->
                    path.trim().removeSurrounding("\"").removeSurrounding("'")
                }
                indexDocuments(paths)
            }
            "search" -> {
                if (actualArgs.size < 2) {
                    println("Error: Please provide a search query")
                    printUsage()
                    return
                }
                // Remove quotes from query if present
                val query = actualArgs.drop(1).joinToString(" ")
                    .trim().removeSurrounding("\"").removeSurrounding("'")
                searchDocuments(query)
            }
            "stats" -> {
                showStats()
            }
            "clear" -> {
                clearIndex()
            }
            else -> {
                println("Unknown command: $command")
                printUsage()
            }
        }
    }
    
    private fun indexDocuments(paths: List<String>) {
        println("=== RAG Pipeline: Indexing Documents ===")
        println()
        
        // Configuration
        val dbPath = ConsoleConfig.getRAGDatabasePath()
        val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val ollamaModel = System.getenv("OLLAMA_MODEL") ?: "nomic-embed-text"
        
        println("Configuration:")
        println("  Database: $dbPath")
        println("  Ollama URL: $ollamaBaseUrl")
        println("  Ollama Model: $ollamaModel")
        println()
        
        runBlocking {
            val pipeline = RAGPipeline.create(
                dbPath = dbPath,
                ollamaBaseUrl = ollamaBaseUrl,
                ollamaModel = ollamaModel,
                chunkSize = 768,
                chunkOverlap = 200
            )
            
            try {
                println("Processing documents...")
                println()
                
                val result = pipeline.processDocuments(
                    paths = paths,
                    onProgress = { progress ->
                        val percentage = progress.getProgressPercentage()
                        val message = progress.getStatusMessage()
                        print("\r[$percentage%] $message")
                        System.out.flush()
                    }
                )
                
                println()
                println()
                
                if (result.isSuccess) {
                    val chunksStored = result.getOrThrow()
                    println("✓ Successfully indexed $chunksStored chunks")
                    
                    val stats = pipeline.getStats()
                    println()
                    println("Statistics:")
                    println("  Total chunks: ${stats.totalChunks}")
                    println("  Total embeddings: ${stats.totalEmbeddings}")
                    println("  Unique sources: ${stats.uniqueSources}")
                } else {
                    val error = result.exceptionOrNull()
                    println("✗ Error: ${error?.message}")
                    error?.printStackTrace()
                }
            } finally {
                pipeline.close()
            }
        }
    }
    
    private fun searchDocuments(query: String) {
        println("=== RAG Pipeline: Search ===")
        println("Query: $query")
        println()
        
        val dbPath = "rag_index.db"
        val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val ollamaModel = System.getenv("OLLAMA_MODEL") ?: "nomic-embed-text"
        
        runBlocking {
            val pipeline = RAGPipeline.create(
                dbPath = dbPath,
                ollamaBaseUrl = ollamaBaseUrl,
                ollamaModel = ollamaModel
            )
            
            try {
                // Check if database has data
                val stats = pipeline.getStats()
                if (stats.totalChunks == 0) {
                    println("⚠ Warning: No documents indexed!")
                    println("Please index documents first using: rag index <path>")
                    return@runBlocking
                }
                
                println("Database contains ${stats.totalChunks} chunks from ${stats.uniqueSources} source(s)")
                println()
                
                println("Generating query embedding...")
                
                // Try with lower similarity threshold for Russian queries
                var result = pipeline.search(query, limit = 10, minSimilarity = 0.0f)
                
                if (result.isSuccess) {
                    val results = result.getOrThrow()
                    
                    if (results.isEmpty()) {
                        println("No results found. This might mean:")
                        println("  1. The document wasn't indexed properly")
                        println("  2. The query doesn't match any content")
                        println("  3. Try a different query or check if the document was indexed")
                    } else {
                        // Filter results by similarity (show only relevant ones)
                        val filteredResults = results.filter { it.similarity >= 0.2f }
                        
                        if (filteredResults.isEmpty()) {
                            println("Found ${results.size} results, but similarity is too low (< 0.2)")
                            println("Showing top 3 results anyway for debugging:")
                            println()
                            results.take(3).forEachIndexed { index, searchResult ->
                                println("${index + 1}. [Similarity: ${String.format("%.3f", searchResult.similarity)}]")
                                println("   Source: ${searchResult.chunk.source}")
                                println("   Chunk #${searchResult.chunk.chunkIndex}")
                                println("   Content: ${searchResult.chunk.content.take(300)}${if (searchResult.chunk.content.length > 300) "..." else ""}")
                                println()
                            }
                        } else {
                            println("Found ${filteredResults.size} relevant results:")
                            println()
                            
                            filteredResults.forEachIndexed { index, searchResult ->
                                println("${index + 1}. [Similarity: ${String.format("%.3f", searchResult.similarity)}]")
                                println("   Source: ${searchResult.chunk.source}")
                                println("   Chunk #${searchResult.chunk.chunkIndex}")
                                println("   Content: ${searchResult.chunk.content.take(300)}${if (searchResult.chunk.content.length > 300) "..." else ""}")
                                println()
                            }
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    println("✗ Error: ${error?.message}")
                    error?.printStackTrace()
                }
            } finally {
                pipeline.close()
            }
        }
    }
    
    private fun showStats() {
        val dbPath = "rag_index.db"
        
        runBlocking {
            val pipeline = RAGPipeline.create(dbPath = dbPath)
            
            try {
                val stats = pipeline.getStats()
                println("=== Vector Store Statistics ===")
                println("Total chunks: ${stats.totalChunks}")
                println("Total embeddings: ${stats.totalEmbeddings}")
                println("Unique sources: ${stats.uniqueSources}")
            } finally {
                pipeline.close()
            }
        }
    }
    
    private fun clearIndex() {
        val dbPath = "rag_index.db"
        
        println("Clearing index at: $dbPath")
        print("Are you sure? (yes/no): ")
        
        val confirmation = readLine()?.trim()?.lowercase()
        if (confirmation == "yes") {
            runBlocking {
                val pipeline = RAGPipeline.create(dbPath = dbPath)
                try {
                    pipeline.clear()
                    println("Index cleared successfully")
                } finally {
                    pipeline.close()
                }
            }
        } else {
            println("Cancelled")
        }
    }
    
    private fun printUsage() {
        println("""
            RAG Pipeline - Document Indexing and Search
            
            Usage:
              rag index <path1> [path2] ...    Index documents from file/directory paths
              rag search <query>               Search for similar content
              rag stats                        Show index statistics
              rag clear                        Clear the index
            
            Examples:
              rag index document.pdf
              rag index /path/to/documents
              rag index file1.md file2.txt src/
              rag search "how does authentication work?"
            
            Environment Variables:
              OLLAMA_BASE_URL    Ollama API base URL (default: http://localhost:11434)
              OLLAMA_MODEL       Embedding model name (default: nomic-embed-text)
            
            Prerequisites:
              1. Install Ollama: https://ollama.ai
              2. Pull an embedding model:
                 ollama pull nomic-embed-text
              3. Start Ollama (if not running as a service):
                 ollama serve
        """.trimIndent())
    }
}

