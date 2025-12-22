# RAG Pipeline

A complete RAG (Retrieval-Augmented Generation) pipeline implementation in Kotlin for console applications.

## Features

- **Document Loading**: Supports `.md`, `.txt`, `.pdf`, `.kt`, `.java`, and other text formats
- **Text Extraction**: Uses Apache Tika for PDF and complex document parsing
- **Smart Chunking**: Configurable chunk size with overlap, preserves sentence boundaries
- **Multilingual Support**: Handles text in multiple languages
- **Embedding Generation**: Uses Ollama Embedding API for local embeddings
- **Vector Storage**: SQLite-based vector store with cosine similarity search
- **Progress Tracking**: Real-time progress updates with cancellation support

## Architecture

```
┌─────────────────┐
│ DocumentLoader  │ → Loads documents from files/directories
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  TextSplitter   │ → Splits text into chunks with overlap
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│EmbeddingGenerator│ → Generates embeddings via Ollama API
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  VectorStore    │ → Stores chunks and embeddings in SQLite
└─────────────────┘
```

## Prerequisites

1. **Install Ollama**: https://ollama.ai

2. **Pull an embedding model**:
   ```bash
   ollama pull nomic-embed-text
   # or
   ollama pull mxbai-embed-large
   ```

3. **Start Ollama** (if not running as a service):
   ```bash
   ollama serve
   ```

## Quick Start

### Index Documents

```kotlin
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val pipeline = RAGPipeline.create(
        dbPath = "rag_index.db",
        ollamaModel = "nomic-embed-text"
    )
    
    try {
        val result = pipeline.processDocuments(
            paths = listOf("document.pdf", "/path/to/documents"),
            onProgress = { progress ->
                println("${progress.getProgressPercentage()}% - ${progress.getStatusMessage()}")
            }
        )
        
        if (result.isSuccess) {
            println("Indexed ${result.getOrThrow()} chunks")
        }
    } finally {
        pipeline.close()
    }
}
```

### Search Documents

```kotlin
fun main() = runBlocking {
    val pipeline = RAGPipeline.create(dbPath = "rag_index.db")
    
    try {
        val result = pipeline.search(
            queryText = "how does authentication work?",
            limit = 5,
            minSimilarity = 0.3f
        )
        
        if (result.isSuccess) {
            result.getOrThrow().forEach { searchResult ->
                println("Similarity: ${searchResult.similarity}")
                println("Source: ${searchResult.chunk.source}")
                println("Content: ${searchResult.chunk.content}")
            }
        }
    } finally {
        pipeline.close()
    }
}
```

## Components

### DocumentLoader

Loads documents from various file formats:

```kotlin
val loader = DocumentLoader()

// Load single document
val doc = loader.loadDocument("document.pdf")

// Load multiple documents
val docs = loader.loadDocuments(listOf("file1.md", "file2.txt", "/path/to/dir"))
```

**Supported formats**: `.md`, `.txt`, `.pdf`, `.kt`, `.java`, `.py`, `.js`, `.ts`, `.json`, `.xml`, `.yaml`, `.html`, `.csv`

### TextSplitter

Splits text into chunks with configurable size and overlap:

```kotlin
// Character-based splitting
val splitter = TextSplitter.characterBased(
    chunkChars = 768,
    overlapChars = 200
)

// Token-based splitting (estimated)
val splitter = TextSplitter.tokenBased(
    chunkTokens = 512,
    overlapTokens = 50
)

val chunks = splitter.splitDocument(document)
```

**Features**:
- Preserves sentence boundaries when possible
- Multilingual support via `BreakIterator`
- Configurable chunk size and overlap
- Token count estimation

### EmbeddingGenerator

Generates embeddings using Ollama API:

```kotlin
val generator = EmbeddingGenerator(
    baseUrl = "http://localhost:11434",
    model = "nomic-embed-text"
)

// Generate single embedding
val embedding = generator.generateEmbedding("text to embed")

// Generate embeddings for multiple chunks
val embeddings = generator.generateEmbeddings(chunks) { current, total ->
    println("Progress: $current/$total")
}
```

### VectorStore

SQLite-based vector store with cosine similarity search:

```kotlin
val store = VectorStore("rag_index.db")

// Store chunk with embedding
store.storeChunk(chunk, embedding, model = "nomic-embed-text")

// Search for similar chunks
val results = store.search(
    queryEmbedding = embedding,
    limit = 10,
    minSimilarity = 0.3f
)

// Get statistics
val stats = store.getStats()
println("Total chunks: ${stats.totalChunks}")
```

**Database Schema**:
```sql
CREATE TABLE chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    start_char INTEGER NOT NULL,
    end_char INTEGER NOT NULL,
    token_count INTEGER,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE embeddings (
    chunk_id INTEGER PRIMARY KEY,
    embedding BLOB NOT NULL,
    model TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE
);
```

## Configuration

### Environment Variables

- `OLLAMA_BASE_URL`: Ollama API base URL (default: `http://localhost:11434`)
- `OLLAMA_MODEL`: Embedding model name (default: `nomic-embed-text`)

### Custom Configuration

```kotlin
val pipeline = RAGPipeline.create(
    dbPath = "custom_index.db",
    ollamaBaseUrl = "http://localhost:11434",
    ollamaModel = "mxbai-embed-large",
    chunkSize = 1024,        // Characters per chunk
    chunkOverlap = 256       // Overlap in characters
)
```

## Example Usage

See `RAGExample.kt` for a complete command-line example:

```bash
# Index documents
./gradlew :console-agent:run --args="rag index document.pdf /path/to/documents"

# Search
./gradlew :console-agent:run --args="rag search 'your query'"

# Show statistics
./gradlew :console-agent:run --args="rag stats"

# Clear index
./gradlew :console-agent:run --args="rag clear"
```

## Error Handling

All components use Kotlin's `Result` type for error handling:

```kotlin
val result = pipeline.processDocuments(paths)

result.onSuccess { chunksStored ->
    println("Success: $chunksStored chunks indexed")
}.onFailure { error ->
    println("Error: ${error.message}")
    error.printStackTrace()
}
```

## Performance Considerations

1. **Batch Processing**: The pipeline processes documents sequentially but generates embeddings in batches
2. **Cancellation**: Supports cancellation via `isCancelled` callback
3. **Progress Tracking**: Real-time progress updates via `onProgress` callback
4. **Memory**: Large documents are processed in chunks to minimize memory usage

## Limitations

- SQLite doesn't have native vector similarity search (uses cosine similarity in-memory)
- For very large datasets (>100k chunks), consider using a dedicated vector database
- Ollama API doesn't support true batch embedding generation (processed sequentially)

## Dependencies

- `org.apache.tika:tika-core` - Document parsing
- `org.apache.tika:tika-parser-standard-package` - PDF and standard parsers
- `org.xerial:sqlite-jdbc` - SQLite database
- `com.squareup.okhttp3:okhttp` - HTTP client for Ollama API
- `kotlinx.serialization` - JSON serialization
- `kotlinx.coroutines` - Async operations

## License

Part of the AIChat project.


