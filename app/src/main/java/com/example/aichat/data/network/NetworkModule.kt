package com.example.aichat.data.network

import com.example.aichat.data.Config
import com.example.aichat.data.api.LocalLLMApiService
import com.example.aichat.data.api.OpenAiApiService
import com.example.aichat.data.api.YandexApiService
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
    
    // YandexGPT API base URL
    private const val YANDEX_BASE_URL = Config.YANDEX_API_BASE_URL
    
    // Local LLM API base URL
    private const val LOCAL_LLM_BASE_URL = Config.LOCAL_LLM_BASE_URL
    
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
     * Creates and provides Retrofit instance for YandexGPT API
     */
    fun provideYandexRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(YANDEX_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Creates and provides YandexApiService instance
     */
    fun provideYandexApiService(retrofit: Retrofit): YandexApiService {
        return retrofit.create(YandexApiService::class.java)
    }
    
    /**
     * Creates and provides Retrofit instance for Local LLM API
     */
    fun provideLocalLLMRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LOCAL_LLM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Creates and provides LocalLLMApiService instance
     */
    fun provideLocalLLMApiService(retrofit: Retrofit): LocalLLMApiService {
        return retrofit.create(LocalLLMApiService::class.java)
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
        
        val yandexRetrofit = provideYandexRetrofit(okHttpClient, gson)
        val yandexApiService = provideYandexApiService(yandexRetrofit)
        
        val localLLMRetrofit = provideLocalLLMRetrofit(okHttpClient, gson)
        val localLLMApiService = provideLocalLLMApiService(localLLMRetrofit)
        
        return NetworkDependencies(
            gson = gson,
            okHttpClient = okHttpClient,
            retrofit = retrofit,
            apiService = apiService,
            yandexApiService = yandexApiService,
            localLLMApiService = localLLMApiService
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
    val apiService: OpenAiApiService,
    val yandexApiService: YandexApiService,
    val localLLMApiService: LocalLLMApiService
)

