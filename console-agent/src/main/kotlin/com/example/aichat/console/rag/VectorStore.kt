package com.example.aichat.console.rag

import com.example.aichat.console.rag.model.Embedding
import com.example.aichat.console.rag.model.SearchResult
import com.example.aichat.console.rag.model.TextChunk
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Vector store implementation using SQLite
 * Stores chunks and their embeddings for similarity search
 */
class VectorStore(private val dbPath: String) {
    private val connection: Connection by lazy {
        // Ensure parent directory exists
        File(dbPath).parentFile?.mkdirs()
        
        // Load SQLite JDBC driver explicitly
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            throw Exception("SQLite JDBC driver not found. Make sure sqlite-jdbc is in the classpath.", e)
        }
        
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        
        // Enable foreign keys
        conn.createStatement().execute("PRAGMA foreign_keys = ON")
        
        // Create schema
        createSchema(conn)
        
        conn
    }
    
    /**
     * Creates the database schema
     */
    private fun createSchema(conn: Connection = connection) {
        val createChunksTable = """
            CREATE TABLE IF NOT EXISTS chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                content TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                start_char INTEGER NOT NULL,
                end_char INTEGER NOT NULL,
                token_count INTEGER,
                metadata TEXT,  -- JSON string
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        
        val createEmbeddingsTable = """
            CREATE TABLE IF NOT EXISTS embeddings (
                chunk_id INTEGER PRIMARY KEY,
                embedding BLOB NOT NULL,  -- FloatArray serialized as byte array
                model TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE
            )
        """.trimIndent()
        
        // Create indexes for faster queries
        val createIndexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_chunks_source ON chunks(source)",
            "CREATE INDEX IF NOT EXISTS idx_chunks_chunk_index ON chunks(chunk_index)",
            "CREATE INDEX IF NOT EXISTS idx_chunks_created_at ON chunks(created_at DESC)"
        )
        
        conn.createStatement().use { stmt ->
            stmt.execute(createChunksTable)
            stmt.execute(createEmbeddingsTable)
            createIndexes.forEach { stmt.execute(it) }
        }
    }
    
    /**
     * Stores a chunk and its embedding
     * @param chunk Text chunk
     * @param embedding Embedding vector
     * @param model Model name used to generate the embedding
     * @return ID of the stored chunk
     */
    fun storeChunk(chunk: TextChunk, embedding: FloatArray, model: String): Long {
        connection.autoCommit = false
        try {
            // Insert chunk
            val chunkId = insertChunk(chunk)
            
            // Insert embedding
            insertEmbedding(chunkId, embedding, model)
            
            connection.commit()
            return chunkId
        } catch (e: Exception) {
            connection.rollback()
            throw Exception("Failed to store chunk: ${e.message}", e)
        } finally {
            connection.autoCommit = true
        }
    }
    
    /**
     * Stores multiple chunks with their embeddings in a batch
     * @param replaceExisting If true, deletes existing chunks from the same sources before inserting new ones
     */
    fun storeChunks(
        chunks: List<TextChunk>, 
        embeddings: Map<Int, FloatArray>, 
        model: String,
        replaceExisting: Boolean = false
    ) {
        connection.autoCommit = false
        try {
            // If replaceExisting is true, delete old chunks from the same sources
            if (replaceExisting && chunks.isNotEmpty()) {
                val sources = chunks.map { it.source }.distinct()
                deleteChunksBySources(sources)
            }
            
            chunks.forEachIndexed { index, chunk ->
                val embedding = embeddings[index] ?: return@forEachIndexed
                val chunkId = insertChunk(chunk)
                insertEmbedding(chunkId, embedding, model)
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw Exception("Failed to store chunks: ${e.message}", e)
        } finally {
            connection.autoCommit = true
        }
    }
    
    /**
     * Deletes chunks by their source paths
     * This is used when reindexing documents to remove old versions
     */
    fun deleteChunksBySources(sources: List<String>) {
        if (sources.isEmpty()) return
        
        connection.autoCommit = false
        try {
            // Use parameterized query to prevent SQL injection
            val placeholders = sources.map { "?" }.joinToString(",")
            val sql = "DELETE FROM chunks WHERE source IN ($placeholders)"
            
            connection.prepareStatement(sql).use { stmt ->
                sources.forEachIndexed { index, source ->
                    stmt.setString(index + 1, source)
                }
                val deletedCount = stmt.executeUpdate()
                println("[VectorStore]: Deleted $deletedCount old chunks from ${sources.size} source(s)")
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw Exception("Failed to delete chunks by sources: ${e.message}", e)
        } finally {
            connection.autoCommit = true
        }
    }
    
    /**
     * Inserts a chunk into the database
     */
    private fun insertChunk(chunk: TextChunk): Long {
        val sql = """
            INSERT INTO chunks (source, content, chunk_index, start_char, end_char, token_count, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, chunk.source)
            stmt.setString(2, chunk.content)
            stmt.setInt(3, chunk.chunkIndex)
            stmt.setInt(4, chunk.startChar)
            stmt.setInt(5, chunk.endChar)
            chunk.tokenCount?.let { stmt.setInt(6, it) } ?: stmt.setNull(6, java.sql.Types.INTEGER)
            
            // Serialize metadata as JSON
            val metadataJson = if (chunk.metadata.isEmpty()) {
                null
            } else {
                Json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        chunk.metadata.forEach { (key, value) ->
                            put(key, value)
                        }
                    }
                )
            }
            stmt.setString(7, metadataJson)
            
            stmt.executeUpdate()
        }
        
        // SQLite doesn't support getGeneratedKeys(), use last_insert_rowid() instead
        return getLastInsertId()
    }
    
    /**
     * Gets the last inserted ID
     */
    private fun getLastInsertId(): Long {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                if (rs.next()) {
                    return rs.getLong(1)
                }
            }
        }
        throw Exception("Failed to get last insert ID")
    }
    
    /**
     * Inserts an embedding into the database
     */
    private fun insertEmbedding(chunkId: Long, embedding: FloatArray, model: String) {
        val sql = """
            INSERT INTO embeddings (chunk_id, embedding, model)
            VALUES (?, ?, ?)
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chunkId)
            stmt.setBytes(2, floatArrayToBytes(embedding))
            stmt.setString(3, model)
            stmt.executeUpdate()
        }
    }
    
    /**
     * Searches for similar chunks using cosine similarity
     * @param queryEmbedding Query embedding vector
     * @param limit Maximum number of results
     * @param minSimilarity Minimum similarity threshold (0.0 to 1.0)
     * @return List of search results sorted by similarity (descending)
     */
    fun search(
        queryEmbedding: FloatArray,
        limit: Int = 10,
        minSimilarity: Float = 0.0f
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // Load all chunks with embeddings, ordered by created_at DESC to prefer newer documents
        val sql = """
            SELECT c.id, c.source, c.content, c.chunk_index, c.start_char, c.end_char, 
                   c.token_count, c.metadata, c.created_at, e.embedding, e.model
            FROM chunks c
            INNER JOIN embeddings e ON c.id = e.chunk_id
            ORDER BY c.created_at DESC
        """.trimIndent()
        
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    val chunk = resultSetToChunk(rs)
                    val embeddingBytes = rs.getBytes("embedding")
                    val embeddingVector = bytesToFloatArray(embeddingBytes)
                    val model = rs.getString("model")
                    
                    val similarity = cosineSimilarity(queryEmbedding, embeddingVector)
                    
                    if (similarity >= minSimilarity) {
                        results.add(
                            SearchResult(
                                chunk = chunk,
                                embedding = Embedding(
                                    chunkId = chunk.id ?: 0,
                                    vector = embeddingVector,
                                    model = model
                                ),
                                similarity = similarity
                            )
                        )
                    }
                }
            }
        }
        
        // Deduplicate by (source, chunk_index) to handle cases where the same document
        // was indexed multiple times - keep only the newest version
        // This ensures we don't show old and new chunks from the same document together
        val deduplicatedResults = mutableMapOf<String, SearchResult>()
        results.forEach { result ->
            val chunk = result.chunk
            // Create a unique key from source and chunk_index
            val dedupeKey = "${chunk.source}:${chunk.chunkIndex}"
            // Keep the first (newest) occurrence for each (source, chunk_index) pair
            // Since results are ordered by created_at DESC, first occurrence is newest
            if (!deduplicatedResults.containsKey(dedupeKey)) {
                deduplicatedResults[dedupeKey] = result
            }
        }
        
        // Sort by similarity (descending) and limit results
        return deduplicatedResults.values
            .sortedByDescending { it.similarity }
            .take(limit)
    }
    
    /**
     * Converts a ResultSet row to a TextChunk
     */
    private fun resultSetToChunk(rs: ResultSet): TextChunk {
        val metadataJson = rs.getString("metadata")
        val metadata = if (metadataJson != null) {
            try {
                val jsonObj = Json.parseToJsonElement(metadataJson).jsonObject
                jsonObj.entries.associate { it.key to it.value.jsonPrimitive.content }
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        
        return TextChunk(
            id = rs.getLong("id"),
            source = rs.getString("source"),
            content = rs.getString("content"),
            chunkIndex = rs.getInt("chunk_index"),
            startChar = rs.getInt("start_char"),
            endChar = rs.getInt("end_char"),
            tokenCount = rs.getInt("token_count").takeIf { !rs.wasNull() },
            metadata = metadata
        )
    }
    
    /**
     * Gets the creation timestamp for a chunk (for debugging)
     */
    fun getChunkCreatedAt(chunkId: Long): String? {
        val sql = "SELECT created_at FROM chunks WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chunkId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getString("created_at")
                }
            }
        }
        return null
    }
    
    /**
     * Calculates cosine similarity between two vectors
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same size" }
        
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator == 0.0f) 0.0f else dotProduct / denominator
    }
    
    /**
     * Converts FloatArray to byte array for storage
     */
    private fun floatArrayToBytes(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        array.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
    
    /**
     * Converts byte array back to FloatArray
     */
    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val array = FloatArray(bytes.size / 4)
        for (i in array.indices) {
            array[i] = buffer.float
        }
        return array
    }
    
    /**
     * Gets statistics about the vector store
     */
    fun getStats(): VectorStoreStats {
        val chunksCount = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM chunks").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
        
        val embeddingsCount = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM embeddings").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
        
        val sources = connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(DISTINCT source) FROM chunks").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
        
        return VectorStoreStats(
            totalChunks = chunksCount,
            totalEmbeddings = embeddingsCount,
            uniqueSources = sources
        )
    }
    
    /**
     * Deletes all data from the store
     */
    fun clear() {
        connection.createStatement().use { stmt ->
            stmt.execute("DELETE FROM embeddings")
            stmt.execute("DELETE FROM chunks")
        }
    }
    
    /**
     * Closes the database connection
     */
    fun close() {
        connection.close()
    }
}

/**
 * Statistics about the vector store
 */
data class VectorStoreStats(
    val totalChunks: Int,
    val totalEmbeddings: Int,
    val uniqueSources: Int
)

