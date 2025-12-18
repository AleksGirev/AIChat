package com.example.aichat.di

import android.app.Application
import com.example.aichat.data.Config
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.local.ChatSessionRepository
import com.example.aichat.data.local.ExternalMemoryRepository
import com.example.aichat.data.local.WeatherRepository
import com.example.aichat.data.mcp.McpConfig
import com.example.aichat.data.mcp.McpFactory
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.search.McpSearchClient
import com.example.aichat.data.search.SearchAgentRepository
import com.example.aichat.service.WeatherService
import com.example.aichat.ui.searchagent.SearchAgentViewModel
import com.example.aichat.ui.viewmodel.ChatViewModel
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin Dependency Injection Module
 * 
 * Architecture Decisions:
 * 1. Single module approach for simplicity in this app size
 * 2. Network dependencies as singletons (OkHttpClient, Gson)
 * 3. Database as singleton, DAOs provided through database
 * 4. Repositories as singletons for consistent state
 * 5. Workers injected via Koin WorkManager integration
 * 
 * Testability:
 * - All dependencies can be mocked via Koin's test modules
 * - Repositories depend on interfaces (DAOs) allowing easy substitution
 * - Network components can be replaced with mock implementations
 */
val appModule = module {
    
    // ==================== Network Dependencies ====================
    
    /**
     * Gson for JSON serialization/deserialization
     * Singleton: Stateless, thread-safe, reusable
     */
    single<Gson> { NetworkModule.provideGson() }
    
    /**
     * OkHttpClient for all HTTP operations
     * Singleton: Connection pooling, caching, interceptors
     */
    single<OkHttpClient> { NetworkModule.provideOkHttpClient() }
    
    /**
     * OpenAI API service
     * Singleton: Retrofit service interface
     */
    single { 
        val okHttpClient: OkHttpClient = get()
        val gson: Gson = get()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, gson)
        NetworkModule.provideOpenAiApiService(retrofit)
    }
    
    /**
     * Yandex API service
     * Singleton: Retrofit service interface
     */
    single { 
        val okHttpClient: OkHttpClient = get()
        val gson: Gson = get()
        val retrofit = NetworkModule.provideYandexRetrofit(okHttpClient, gson)
        NetworkModule.provideYandexApiService(retrofit)
    }
    
    // ==================== Database Dependencies ====================
    
    /**
     * Room Database
     * Singleton: Database instance with all DAOs
     */
    single { ChatDatabase.getDatabase(androidContext()) }
    
    /**
     * DAOs - accessed through database singleton
     */
    single { get<ChatDatabase>().chatMessageDao() }
    single { get<ChatDatabase>().chatSessionDao() }
    single { get<ChatDatabase>().externalMemoryDao() }
    single { get<ChatDatabase>().weatherDao() }
    
    // ==================== MCP Dependencies ====================
    
    /**
     * MCP Configuration manager
     * Uses EncryptedSharedPreferences for secure storage
     */
    single { McpConfig(androidContext()) }
    
    /**
     * MCP Repository - nullable, may fail to create
     * Singleton: Maintains connection state
     * 
     * Used for: Chat functionality (Context7 MCP server)
     */
    single<McpRepository?> {
        try {
            McpFactory.createMcpRepository(
                context = androidContext(),
                httpClient = get(),
                gson = get()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * MCP Search Client for LobeHub DuckDuckGo MCP server
     * Used for article search functionality
     */
    single {
        McpSearchClient(
            httpClient = get(),
            gson = get()
        )
    }
    
    // ==================== Repositories ====================
    
    /**
     * Chat History Repository
     * Singleton: Manages chat message persistence
     */
    single { ChatHistoryRepository(get()) }
    
    /**
     * Chat Session Repository
     * Singleton: Manages chat session persistence
     */
    single { ChatSessionRepository(get()) }
    
    /**
     * External Memory Repository
     * Singleton: Manages external memory/context persistence
     */
    single { ExternalMemoryRepository(get()) }
    
    /**
     * Weather Repository
     * Singleton: Manages weather data persistence for Belarusian cities
     */
    single { WeatherRepository(get()) }
    
    /**
     * Search Agent Repository
     * Singleton: Orchestrates MCP search and LLM calls for article search
     */
    single {
        SearchAgentRepository(
            mcpSearchClient = get(),
            yandexApiService = get(),
            gson = get()
        )
    }
    
    /**
     * Chat Repository
     * Singleton: Orchestrates API calls and tool handling
     */
    single {
        ChatRepository(
            apiService = get(),
            apiKey = Config.OPENAI_API_KEY,
            yandexApiService = get(),
            mcpRepository = get(),
            gson = get()
        )
    }
    
    /**
     * Weather Service
     * Singleton: Manages periodic weather fetching and summary generation
     * Uses the main MCP repository for weather data
     */
    single {
        WeatherService(
            weatherRepository = get(),
            mcpRepository = get(), // Use main MCP repository
            chatRepository = get()
        )
    }
    
    // ==================== ViewModels ====================
    
    /**
     * Chat ViewModel
     * Scoped to Activity lifecycle
     * 
     * Uses injected dependencies for testability.
     * All repositories and services are provided via Koin.
     */
    viewModel { 
        ChatViewModel(
            application = androidApplication(),
            injectedRepository = get(),
            injectedMcpRepository = get(),
            injectedHistoryRepository = get(),
            injectedSessionRepository = get(),
            injectedExternalMemoryRepository = get()
        )
    }
    
    /**
     * Search Agent ViewModel
     * Scoped to Activity lifecycle
     * 
     * Manages UI state for article search functionality
     */
    viewModel {
        SearchAgentViewModel(
            repository = get()
        )
    }
}

/**
 * List of all Koin modules
 * Used in Application.onCreate() for initialization
 */
val allModules = listOf(appModule)

