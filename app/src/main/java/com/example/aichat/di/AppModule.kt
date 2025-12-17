package com.example.aichat.di

import android.app.Application
import com.example.aichat.data.Config
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.local.ChatSessionRepository
import com.example.aichat.data.local.ExternalMemoryRepository
import com.example.aichat.data.local.SyncUpdateRepository
import com.example.aichat.data.local.WeatherRepository
import com.example.aichat.data.mcp.McpConfig
import com.example.aichat.data.mcp.McpFactory
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.service.WeatherService
import com.example.aichat.sync.BackgroundSyncMcpConfig
import com.example.aichat.sync.BackgroundSyncWorker
import com.example.aichat.sync.NotificationSummaryWorker
import com.example.aichat.sync.SyncNotificationManager
import com.example.aichat.sync.WorkManagerScheduler
import com.example.aichat.ui.viewmodel.ChatViewModel
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.qualifier.named
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
    single { get<ChatDatabase>().syncUpdateDao() }
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
     * Background Sync MCP Repository - dedicated for background sync tasks
     * Uses OpenWeather MCP server via STDIO transport
     * 
     * Named qualifier to differentiate from the chat MCP repository
     * 
     * Configuration:
     * - STDIO mode: Local Python process (development)
     * - HTTP mode: Remote MCP server (production)
     */
    single<McpRepository?>(named("syncMcp")) {
        try {
            BackgroundSyncMcpConfig.createSyncMcpRepository(
                context = androidContext(),
                okHttpClient = get(),
                gson = get()
            )
        } catch (e: Exception) {
            android.util.Log.e("AppModule", "Failed to create sync MCP repository", e)
            null
        }
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
     * Sync Update Repository
     * Singleton: Manages background sync data persistence
     */
    single { SyncUpdateRepository(get(), androidContext(), get()) }
    
    /**
     * Weather Repository
     * Singleton: Manages weather data persistence for Belarusian cities
     */
    single { WeatherRepository(get()) }
    
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
    
    // ==================== Sync & Notification ====================
    
    /**
     * Notification Manager for sync summaries
     * Singleton: Manages notification channels and display
     */
    single { SyncNotificationManager(androidContext()) }
    
    /**
     * WorkManager Scheduler
     * Singleton: Handles all WorkManager scheduling operations
     */
    single { WorkManagerScheduler(androidContext()) }
    
    /**
     * Weather Service
     * Singleton: Manages periodic weather fetching and summary generation
     * Uses the sync MCP repository for weather data
     */
    single {
        WeatherService(
            weatherRepository = get(),
            mcpRepository = get(named("syncMcp")), // Use sync MCP repository
            chatRepository = get()
        )
    }
    
    // ==================== Workers ====================
    
    /**
     * Background Sync Worker
     * Factory: New instance per work request
     * 
     * Dependencies injected via Koin WorkManager integration:
     * - McpRepository (named "syncMcp") for OpenWeather MCP server
     * - ChatRepository for LLM API calls
     * - SyncUpdateRepository for persistence
     * - SyncNotificationManager for high-priority notifications
     * 
     * Uses dedicated OpenWeather MCP server for background sync,
     * separate from the Context7 MCP used in chat.
     */
    worker { params ->
        BackgroundSyncWorker(
            context = androidContext(),
            workerParams = params.get(),
            mcpRepository = get(named("syncMcp")), // Use dedicated sync MCP repository
            chatRepository = get(),
            syncUpdateRepository = get(),
            weatherRepository = get(),
            notificationManager = get(),
            gson = get()
        )
    }
    
    /**
     * Notification Summary Worker
     * Factory: New instance per work request
     * 
     * Runs hourly to aggregate and display sync summaries
     */
    worker { params ->
        NotificationSummaryWorker(
            context = androidContext(),
            workerParams = params.get(),
            syncUpdateRepository = get(),
            weatherRepository = get(),
            notificationManager = get()
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
}

/**
 * List of all Koin modules
 * Used in Application.onCreate() for initialization
 */
val allModules = listOf(appModule)

