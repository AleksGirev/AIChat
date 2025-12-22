package com.example.aichat.console.rag

import com.example.aichat.console.rag.model.Document
import org.apache.tika.Tika
import org.apache.tika.exception.TikaException
import org.apache.tika.metadata.Metadata
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Loads documents from various file formats (.md, .txt, .pdf, .kt, .java, etc.)
 * Uses Apache Tika for PDF and other complex formats
 */
class DocumentLoader {
    private val tika = Tika()
    
    /**
     * Loads a document from a file path
     * @param filePath Path to the file
     * @return Document with extracted content, or null if loading fails
     * @throws Exception with detailed error message if loading fails
     */
    fun loadDocument(filePath: String): Document? {
        return try {
            val file = File(filePath)
            
            if (!file.exists()) {
                throw Exception("File does not exist: $filePath")
            }
            
            if (!file.isFile) {
                throw Exception("Path is not a file: $filePath (it might be a directory)")
            }
            
            // Don't check canRead() - macOS extended attributes can cause false negatives
            // We'll catch the exception if the file is actually not readable
            val content = extractText(file)
            
            if (content.isBlank()) {
                throw Exception("File is empty or text extraction failed: $filePath")
            }
            
            val metadata = extractMetadata(file)
            
            Document(
                source = file.absolutePath,
                content = content,
                metadata = metadata
            )
        } catch (e: Exception) {
            throw Exception("Failed to load document from $filePath: ${e.message}", e)
        }
    }
    
    /**
     * Loads multiple documents from a directory or list of file paths
     * @param paths List of file paths or directory paths
     * @return List of successfully loaded documents
     * @throws Exception if no documents could be loaded, with details about failures
     */
    fun loadDocuments(paths: List<String>): List<Document> {
        val documents = mutableListOf<Document>()
        val errors = mutableListOf<String>()
        
        for (path in paths) {
            try {
                // Remove quotes and trim path
                val cleanPath = path.trim().removeSurrounding("\"").removeSurrounding("'")
                val file = File(cleanPath)
                
                if (!file.exists()) {
                    errors.add("Path does not exist: $path")
                    continue
                }
                
                when {
                    file.isFile -> {
                        try {
                            loadDocument(path)?.let { documents.add(it) }
                        } catch (e: Exception) {
                            errors.add("Failed to load file $path: ${e.message}")
                        }
                    }
                    file.isDirectory -> {
                        // Recursively load files from directory
                        val dirFiles = file.walkTopDown()
                            .filter { it.isFile && isSupportedFile(it) }
                            .toList()
                        
                        if (dirFiles.isEmpty()) {
                            errors.add("No supported files found in directory: $path")
                        } else {
                            dirFiles.forEach { file ->
                                try {
                                    loadDocument(file.absolutePath)?.let { documents.add(it) }
                                } catch (e: Exception) {
                                    errors.add("Failed to load file ${file.absolutePath}: ${e.message}")
                                }
                            }
                        }
                    }
                    else -> {
                        errors.add("Path is neither a file nor a directory: $path")
                    }
                }
            } catch (e: Exception) {
                errors.add("Error processing path $path: ${e.message}")
            }
        }
        
        if (documents.isEmpty() && errors.isNotEmpty()) {
            throw Exception("No documents loaded. Errors:\n${errors.joinToString("\n")}")
        }
        
        return documents
    }
    
    /**
     * Extracts text from a file using appropriate method based on file type
     */
    private fun extractText(file: File): String {
        val extension = file.extension.lowercase()
        
        return when {
            // Files without extensions or known text extensions - read as plain text
            extension.isEmpty() || extension in listOf("md", "txt", "kt", "java", "py", "js", "ts", "json", "xml", "yaml", "yml") -> {
                // Plain text files - read directly
                try {
                    file.readText(Charset.defaultCharset())
                } catch (e: java.nio.file.AccessDeniedException) {
                    throw Exception("Access denied. On macOS, you may need to grant Terminal/IDE full disk access in System Settings > Privacy & Security > Full Disk Access. File: ${file.absolutePath}", e)
                } catch (e: java.io.IOException) {
                    throw Exception("Failed to read file: ${e.message}. File: ${file.absolutePath}", e)
                }
            }
            extension == "pdf" -> {
                // PDF files - use Tika
                try {
                    // Try using NIO Files API first (may work better on macOS)
                    try {
                        val inputStream = Files.newInputStream(Paths.get(file.absolutePath))
                        inputStream.use { input ->
                            tika.parseToString(input)
                        }
                    } catch (e: java.nio.file.AccessDeniedException) {
                        // Fallback to FileInputStream
                        FileInputStream(file).use { input ->
                            tika.parseToString(input)
                        }
                    }
                } catch (e: java.nio.file.AccessDeniedException) {
                    throw Exception("Access denied. On macOS, try:\n1. Copy file to project folder\n2. Run: xattr -d com.apple.quarantine \"${file.absolutePath}\"\n3. Grant Terminal full disk access in System Settings > Privacy & Security\nFile: ${file.absolutePath}", e)
                } catch (e: java.io.FileNotFoundException) {
                    throw Exception("File not found or not accessible: ${file.absolutePath}. Error: ${e.message}", e)
                } catch (e: TikaException) {
                    throw Exception("Failed to parse PDF: ${e.message}", e)
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("Operation not permitted") == true || e.message?.contains("Permission denied") == true) {
                        throw Exception("Operation not permitted. On macOS, try:\n1. Copy file to project folder: cp \"${file.absolutePath}\" .\n2. Run: xattr -d com.apple.quarantine \"${file.absolutePath}\"\n3. Grant Terminal full disk access in System Settings\nFile: ${file.absolutePath}", e)
                    }
                    throw Exception("Failed to read PDF file: ${e.message}. File: ${file.absolutePath}", e)
                }
            }
            else -> {
                // Other formats - try Tika as fallback
                try {
                    FileInputStream(file).use { input ->
                        tika.parseToString(input)
                    }
                } catch (e: java.nio.file.AccessDeniedException) {
                    throw Exception("Access denied. On macOS, you may need to grant Terminal/IDE full disk access. File: ${file.absolutePath}", e)
                } catch (e: Exception) {
                    throw Exception("Unsupported file format or failed to parse: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Extracts metadata from a file
     */
    private fun extractMetadata(file: File): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        metadata["file_name"] = file.name
        metadata["file_extension"] = file.extension
        metadata["file_size"] = file.length().toString()
        metadata["file_path"] = file.absolutePath
        
        // Try to extract additional metadata using Tika
        try {
            FileInputStream(file).use { input ->
                val tikaMetadata = Metadata()
                tika.parse(input, tikaMetadata)
                
                // Add Tika metadata
                tikaMetadata.names().forEach { name: String ->
                    val value = tikaMetadata.get(name)
                    if (value != null && value.isNotBlank()) {
                        metadata[name] = value
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore metadata extraction errors
        }
        
        return metadata
    }
    
    /**
     * Checks if a file type is supported
     */
    private fun isSupportedFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        // Files without extensions are treated as plain text and are supported
        return extension.isEmpty() || extension in listOf(
            "md", "txt", "pdf", "kt", "java", "py", "js", "ts", 
            "json", "xml", "yaml", "yml", "html", "htm", "csv"
        )
    }
}

