package com.example.aichat.console.ollama

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Diagnostic tool for checking Ollama server configuration and availability
 */
class OllamaDiagnostics(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ModelInfo(
        val name: String,
        val size: Long? = null,
        @JsonProperty("modified_at") val modifiedAt: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ModelsResponse(
        val models: List<ModelInfo>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GenerateRequest(
        val model: String,
        val prompt: String,
        @JsonProperty("stream") val stream: Boolean = false
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GenerateResponse(
        val model: String? = null,
        val response: String? = null,
        val done: Boolean? = null,
        @JsonProperty("created_at") val createdAt: String? = null,
        val error: String? = null
    )

    data class DiagnosticResult(
        val endpoint: String,
        val status: String,
        val details: String,
        val success: Boolean
    )

    /**
     * Check if server is reachable
     */
    fun checkServerReachability(): DiagnosticResult {
        return try {
            val request = Request.Builder()
                .url(baseUrl)
                .head()
                .build()
            
            val response = client.newCall(request).execute()
            val success = response.isSuccessful || response.code in 200..499
            
            DiagnosticResult(
                endpoint = baseUrl,
                status = if (success) "✓ Reachable" else "✗ Not reachable",
                details = "HTTP ${response.code}: ${response.message}",
                success = success
            )
        } catch (e: Exception) {
            DiagnosticResult(
                endpoint = baseUrl,
                status = "✗ Connection failed",
                details = e.message ?: "Unknown error",
                success = false
            )
        }
    }

    /**
     * Check /api/tags endpoint (list available models)
     */
    fun checkTagsEndpoint(): DiagnosticResult {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return DiagnosticResult(
                    endpoint = "/api/tags",
                    status = "✗ Failed",
                    details = "HTTP ${response.code}: ${response.message}",
                    success = false
                )
            }
            
            val responseBody = response.body?.string() ?: ""
            val modelsResponse: ModelsResponse = objectMapper.readValue(responseBody)
            
            val modelNames = modelsResponse.models.joinToString(", ") { it.name }
            val modelCount = modelsResponse.models.size
            
            DiagnosticResult(
                endpoint = "/api/tags",
                status = "✓ Available",
                details = "Found $modelCount model(s): $modelNames",
                success = true
            )
        } catch (e: Exception) {
            DiagnosticResult(
                endpoint = "/api/tags",
                status = "✗ Error",
                details = e.message ?: "Unknown error",
                success = false
            )
        }
    }

    /**
     * Check /api/generate endpoint with a test prompt
     */
    fun checkGenerateEndpoint(model: String = "qwen2:7b-instruct"): DiagnosticResult {
        return try {
            val generateRequest = GenerateRequest(
                model = model,
                prompt = "Привет! Ответь одним словом: работает?",
                stream = false
            )
            
            val requestBody = objectMapper.writeValueAsString(generateRequest)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                return DiagnosticResult(
                    endpoint = "/api/generate",
                    status = "✗ Failed",
                    details = "HTTP ${response.code}: ${response.message}\nResponse: $errorBody",
                    success = false
                )
            }
            
            val responseBody = response.body?.string() ?: ""
            val generateResponse: GenerateResponse = objectMapper.readValue(responseBody)
            
            if (generateResponse.error != null) {
                DiagnosticResult(
                    endpoint = "/api/generate",
                    status = "✗ Error from server",
                    details = "Model: $model\nError: ${generateResponse.error}",
                    success = false
                )
            } else {
                val responseText = generateResponse.response?.take(50) ?: "No response"
                DiagnosticResult(
                    endpoint = "/api/generate",
                    status = "✓ Working",
                    details = "Model: $model\nResponse preview: $responseText...",
                    success = true
                )
            }
        } catch (e: Exception) {
            DiagnosticResult(
                endpoint = "/api/generate",
                status = "✗ Error",
                details = "Model: $model\nError: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Check /api/version endpoint
     */
    fun checkVersionEndpoint(): DiagnosticResult {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/version")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return DiagnosticResult(
                    endpoint = "/api/version",
                    status = "✗ Failed",
                    details = "HTTP ${response.code}: ${response.message}",
                    success = false
                )
            }
            
            val responseBody = response.body?.string() ?: ""
            
            DiagnosticResult(
                endpoint = "/api/version",
                status = "✓ Available",
                details = "Version info: $responseBody",
                success = true
            )
        } catch (e: Exception) {
            DiagnosticResult(
                endpoint = "/api/version",
                status = "✗ Error",
                details = e.message ?: "Unknown error",
                success = false
            )
        }
    }

    /**
     * Run all diagnostic checks
     */
    fun runFullDiagnostics(model: String = "qwen2:7b-instruct"): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        
        println("🔍 Running Ollama Diagnostics")
        println("=" .repeat(60))
        println("Server: $baseUrl")
        println("=" .repeat(60))
        println()
        
        // 1. Check server reachability
        println("1. Checking server reachability...")
        val reachability = checkServerReachability()
        results.add(reachability)
        println("   ${reachability.status}: ${reachability.details}")
        println()
        
        if (!reachability.success) {
            println("⚠️  Server is not reachable. Skipping further checks.")
            return results
        }
        
        // 2. Check version
        println("2. Checking /api/version endpoint...")
        val version = checkVersionEndpoint()
        results.add(version)
        println("   ${version.status}: ${version.details}")
        println()
        
        // 3. Check tags (list models)
        println("3. Checking /api/tags endpoint (list models)...")
        val tags = checkTagsEndpoint()
        results.add(tags)
        println("   ${tags.status}: ${tags.details}")
        println()
        
        // 4. Check generate endpoint
        println("4. Checking /api/generate endpoint...")
        println("   Testing with model: $model")
        val generate = checkGenerateEndpoint(model)
        results.add(generate)
        println("   ${generate.status}: ${generate.details}")
        println()
        
        // Summary
        println("=" .repeat(60))
        println("Summary:")
        val successCount = results.count { it.success }
        val totalCount = results.size
        println("   Passed: $successCount/$totalCount checks")
        
        if (successCount == totalCount) {
            println("   ✅ All checks passed! Server is fully configured.")
        } else {
            println("   ⚠️  Some checks failed. See details above.")
        }
        println("=" .repeat(60))
        
        return results
    }
}

fun main(args: Array<String>) {
    val baseUrl = args.getOrNull(0) ?: "http://193.42.127.171:11434"
    val model = args.getOrNull(1) ?: "qwen2:7b-instruct"
    
    val diagnostics = OllamaDiagnostics(baseUrl)
    diagnostics.runFullDiagnostics(model)
}
