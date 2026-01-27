package com.example.aichat.console.offlinechat

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Service for speech recognition in console application.
 * 
 * Uses system commands to record audio and optionally Vosk for offline recognition.
 * Falls back to system speech recognition if available.
 * 
 * Supported platforms:
 * - Linux: uses arecord (ALSA) or sox
 * - macOS: uses sox or say (for TTS)
 * - Windows: uses PowerShell commands (basic support)
 */
class SpeechRecognitionService(
    private val modelPath: String? = null // Path to Vosk model (optional)
) {
    
    private val tempDir = System.getProperty("java.io.tmpdir")
    private val audioFile = File(tempDir, "speech_recognition_${System.currentTimeMillis()}.wav")
    
    // Vosk model instance (lazy initialization)
    private var voskModel: Model? = null
    
    /**
     * Check if speech recognition is available on this system
     */
    fun isAvailable(): Boolean {
        return try {
            when {
                isLinux() -> checkCommand("arecord") || checkCommand("sox")
                isMacOS() -> checkCommand("sox") || checkCommand("rec")
                isWindows() -> true // PowerShell available
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Record audio from microphone
     * 
     * @param durationSeconds Duration to record (default: 5 seconds)
     * @return Path to recorded audio file
     */
    suspend fun recordAudio(durationSeconds: Int = 5): String = withContext(Dispatchers.IO) {
        try {
            when {
                isLinux() -> recordAudioLinux(durationSeconds)
                isMacOS() -> recordAudioMacOS(durationSeconds)
                isWindows() -> recordAudioWindows(durationSeconds)
                else -> throw UnsupportedOperationException("Speech recognition not supported on this platform")
            }
            audioFile.absolutePath
        } catch (e: Exception) {
            throw IOException("Failed to record audio: ${e.message}", e)
        }
    }
    
    /**
     * Recognize speech from audio file
     * 
     * @param audioFilePath Path to audio file
     * @return Recognized text
     */
    suspend fun recognizeSpeech(audioFilePath: String): String = withContext(Dispatchers.IO) {
        try {
            // Try Vosk first if model is available
            val effectiveModelPath = modelPath ?: System.getenv("VOSK_MODEL_PATH")
            if (effectiveModelPath != null && File(effectiveModelPath).exists()) {
                return@withContext recognizeWithVosk(audioFilePath, effectiveModelPath)
            }
            
            // Fallback to system commands or simple approach
            // For now, return a message that user needs to install Vosk model
            // or use online service
            throw UnsupportedOperationException(
                "Offline speech recognition requires Vosk model. " +
                "Please download a model from https://alphacephei.com/vosk/models " +
                "and set VOSK_MODEL_PATH environment variable, " +
                "or use online speech recognition service."
            )
        } catch (e: Exception) {
            throw IOException("Failed to recognize speech: ${e.message}", e)
        }
    }
    
    /**
     * Record and recognize speech in one call
     * 
     * @param durationSeconds Duration to record
     * @return Recognized text
     */
    suspend fun recordAndRecognize(durationSeconds: Int = 5): String {
        val audioPath = recordAudio(durationSeconds)
        return try {
            recognizeSpeech(audioPath)
        } finally {
            // Clean up audio file
            try {
                File(audioPath).delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    // Platform detection
    private fun isLinux(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true
    private fun isMacOS(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
    private fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
    
    // Check if command is available
    private fun checkCommand(command: String): Boolean {
        return try {
            val process = ProcessBuilder("which", command).start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    // Record audio on Linux using arecord
    private suspend fun recordAudioLinux(durationSeconds: Int) {
        val command = if (checkCommand("arecord")) {
            listOf(
                "arecord",
                "-f", "S16_LE",
                "-r", "16000",
                "-c", "1",
                "-d", durationSeconds.toString(),
                audioFile.absolutePath
            )
        } else if (checkCommand("sox")) {
            listOf(
                "sox",
                "-d",
                "-r", "16000",
                "-c", "1",
                "-b", "16",
                audioFile.absolutePath,
                "trim", "0", durationSeconds.toString()
            )
        } else {
            throw IOException("No audio recording tool found (arecord or sox)")
        }
        
        val process = ProcessBuilder(command).start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0 || !audioFile.exists()) {
            throw IOException("Failed to record audio: process exited with code $exitCode")
        }
    }
    
    // Record audio on macOS using sox
    private suspend fun recordAudioMacOS(durationSeconds: Int) {
        val command = if (checkCommand("sox")) {
            listOf(
                "sox",
                "-d",
                "-r", "16000",
                "-c", "1",
                "-b", "16",
                audioFile.absolutePath,
                "trim", "0", durationSeconds.toString()
            )
        } else if (checkCommand("rec")) {
            listOf(
                "rec",
                "-r", "16000",
                "-c", "1",
                audioFile.absolutePath,
                "trim", "0", durationSeconds.toString()
            )
        } else {
            throw IOException("No audio recording tool found (sox or rec). Install with: brew install sox")
        }
        
        val process = ProcessBuilder(command).start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0 || !audioFile.exists()) {
            throw IOException("Failed to record audio: process exited with code $exitCode")
        }
    }
    
    // Record audio on Windows using PowerShell
    @Suppress("UNUSED_PARAMETER")
    private suspend fun recordAudioWindows(durationSeconds: Int) {
        // Windows recording is more complex and requires .NET Speech API
        // For now, throw unsupported - Windows speech recognition requires .NET
        throw UnsupportedOperationException(
            "Windows speech recognition requires .NET Speech API. " +
            "Please use Linux or macOS, or install Vosk model for offline recognition. " +
            "Alternatively, you can use online speech recognition services."
        )
    }
    
    // Recognize with Vosk (if model is available)
    private suspend fun recognizeWithVosk(audioFilePath: String, modelPath: String): String {
        // Load model (reuse if already loaded)
        val model = voskModel ?: run {
            val newModel = Model(modelPath)
            voskModel = newModel
            newModel
        }
        
        // Create recognizer with 16000 Hz sample rate (standard for speech recognition)
        val recognizer = Recognizer(model, 16000f)
        var audioStream: javax.sound.sampled.AudioInputStream? = null
        
        try {
            // Load audio file
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                throw IOException("Audio file not found: $audioFilePath")
            }
            
            val stream = AudioSystem.getAudioInputStream(
                BufferedInputStream(FileInputStream(audioFile))
            )
            audioStream = stream
            
            // Process audio in chunks
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var finalResult: String? = null
            
            while (stream.read(buffer).also { bytesRead = it } >= 0) {
                if (bytesRead > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        // Final result
                        finalResult = recognizer.result
                    } else {
                        // Partial result (can be used for real-time feedback)
                        recognizer.partialResult
                    }
                }
            }
            
            // Get final result if not already obtained
            if (finalResult == null) {
                finalResult = recognizer.finalResult
            }
            
            // Parse JSON result to extract text
            // Vosk returns JSON like: {"text": "recognized text"}
            val resultJson = finalResult ?: throw IOException("No recognition result")
            
            // Simple JSON parsing (extract text field)
            val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(resultJson)
            val recognizedText = textMatch?.groupValues?.get(1)?.trim() ?: ""
            
            if (recognizedText.isEmpty()) {
                throw IOException("No text recognized from audio")
            }
            
            return recognizedText
            
        } catch (e: UnsupportedAudioFileException) {
            throw IOException("Unsupported audio format: ${e.message}", e)
        } catch (e: UnsatisfiedLinkError) {
            // Known issue with Vosk on macOS - provide helpful error message
            throw IOException(
                "Vosk native library error on macOS. " +
                "This is a known issue with Vosk Java bindings. " +
                "Solutions:\n" +
                "1. Try cleaning and rebuilding: ./gradlew clean build --refresh-dependencies\n" +
                "2. Use Python Vosk instead (see VOSK_TROUBLESHOOTING.md)\n" +
                "3. Use online speech recognition services\n" +
                "Error: ${e.message}", e
            )
        } catch (e: Exception) {
            throw IOException("Vosk recognition failed: ${e.message}", e)
        } finally {
            // Clean up resources (but keep model loaded for reuse)
            try {
                audioStream?.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            try {
                recognizer.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            // Model is kept in memory for reuse
        }
    }
    
    /**
     * Clean up temporary files
     */
    fun cleanup() {
        try {
            if (audioFile.exists()) {
                audioFile.delete()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
