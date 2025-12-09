package com.example.aichat.data.network

import com.example.aichat.data.api.OpenAiApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network configuration module for providing API dependencies
 */
object NetworkModule {
    
    // OpenRouter API base URL
    private const val BASE_URL = "https://openrouter.ai/api/"
    
    /**
     * Creates and provides Gson instance for JSON serialization/deserialization
     */
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    /**
     * Creates and provides OkHttpClient with logging interceptor
     */
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Creates and provides Retrofit instance
     */
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Creates and provides OpenAiApiService instance
     */
    fun provideOpenAiApiService(retrofit: Retrofit): OpenAiApiService {
        return retrofit.create(OpenAiApiService::class.java)
    }
    
    /**
     * Convenience method to create all network dependencies
     * Returns a NetworkDependencies object containing all required instances
     */
    fun createNetworkDependencies(): NetworkDependencies {
        val gson = provideGson()
        val okHttpClient = provideOkHttpClient()
        val retrofit = provideRetrofit(okHttpClient, gson)
        val apiService = provideOpenAiApiService(retrofit)
        
        return NetworkDependencies(
            gson = gson,
            okHttpClient = okHttpClient,
            retrofit = retrofit,
            apiService = apiService
        )
    }
}

/**
 * Data class to hold all network dependencies
 */
data class NetworkDependencies(
    val gson: Gson,
    val okHttpClient: OkHttpClient,
    val retrofit: Retrofit,
    val apiService: OpenAiApiService
)

