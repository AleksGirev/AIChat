package com.example.aichat.console.mcp.repo.fs

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Service for file system operations
 */
class FileSystemService {
    
    /**
     * Reads the contents of a file
     * @param path File path (relative or absolute)
     * @return File contents as string
     * @throws Exception if file cannot be read
     */
    fun readFile(path: String): String {
        val file = if (Paths.get(path).isAbsolute) {
            File(path)
        } else {
            File(System.getProperty("user.dir"), path)
        }
        
        if (!file.exists()) {
            throw Exception("File does not exist: ${file.absolutePath}")
        }
        
        if (!file.isFile) {
            throw Exception("Path is not a file: ${file.absolutePath}")
        }
        
        if (!file.canRead()) {
            throw Exception("File is not readable: ${file.absolutePath}")
        }
        
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            throw Exception("Failed to read file: ${e.message}")
        }
    }
    
    /**
     * Lists files in a directory
     * @param dir Directory path (default: current directory)
     * @param recursive Whether to list recursively
     * @return List of file paths
     */
    fun listFiles(dir: String = ".", recursive: Boolean = false): List<String> {
        val directory = if (Paths.get(dir).isAbsolute) {
            File(dir)
        } else {
            File(System.getProperty("user.dir"), dir)
        }
        
        if (!directory.exists()) {
            throw Exception("Directory does not exist: ${directory.absolutePath}")
        }
        
        if (!directory.isDirectory) {
            throw Exception("Path is not a directory: ${directory.absolutePath}")
        }
        
        return if (recursive) {
            directory.walkTopDown()
                .filter { it.isFile }
                .map { it.absolutePath }
                .toList()
        } else {
            directory.listFiles()
                ?.filter { it.isFile }
                ?.map { it.absolutePath }
                ?: emptyList()
        }
    }
    
    /**
     * Checks if a path exists
     */
    fun exists(path: String): Boolean {
        val file = if (Paths.get(path).isAbsolute) {
            File(path)
        } else {
            File(System.getProperty("user.dir"), path)
        }
        return file.exists()
    }
    
    /**
     * Checks if a path is a directory
     */
    fun isDirectory(path: String): Boolean {
        val file = if (Paths.get(path).isAbsolute) {
            File(path)
        } else {
            File(System.getProperty("user.dir"), path)
        }
        return file.isDirectory
    }
}

