package com.example.aichat.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Service for speech recognition using Android's built-in SpeechRecognizer
 * 
 * Converts speech to text using the device's default speech recognition engine.
 * Requires RECORD_AUDIO permission.
 */
class SpeechRecognizerService(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    /**
     * Check if speech recognition is available on the device
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Check if RECORD_AUDIO permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Start listening for speech and return a Flow of recognized text
     * 
     * @return Flow that emits recognized text strings
     */
    fun startListening(): Flow<String> = callbackFlow {
        if (!isAvailable()) {
            close(Exception("Speech recognition is not available on this device"))
            return@callbackFlow
        }
        
        if (!hasPermission()) {
            close(Exception("RECORD_AUDIO permission is not granted"))
            return@callbackFlow
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                // Ready to listen
            }
            
            override fun onBeginningOfSpeech() {
                // User started speaking
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed (can be used for visual feedback)
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }
            
            override fun onEndOfSpeech() {
                // User stopped speaking
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error: $error"
                }
                close(Exception(errorMessage))
            }
            
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Send the first (most confident) result
                    trySend(matches[0])
                }
                close()
            }
            
            override fun onPartialResults(partialResults: Bundle) {
                val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Send partial results for real-time feedback
                    trySend(matches[0])
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Additional events
            }
        }
        
        speechRecognizer?.setRecognitionListener(recognitionListener)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU") // Russian language
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        speechRecognizer?.startListening(intent)
        
        awaitClose {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
    
    /**
     * Stop listening and clean up resources
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
