package com.example.aichat.di

import android.app.Application
import com.example.aichat.data.Config
import com.example.aichat.data.api.LocalLLMApiService
import com.example.aichat.data.auth.AuthManager
import com.example.aichat.data.crm.CrmMcpClient
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatHistoryRepository
import com.example.aichat.data.local.ChatSessionRepository
import com.example.aichat.data.local.ExternalMemoryRepository
import com.example.aichat.data.local.UserRepository
import com.example.aichat.data.local.WeatherRepository
import com.example.aichat.data.mcp.McpConfig
import com.example.aichat.data.mcp.McpFactory
import com.example.aichat.data.mcp.McpRepository
import com.example.aichat.data.network.NetworkModule
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.brightdata.BrightDataMcpClient
import com.example.aichat.data.rag.RagService
import com.example.aichat.data.search.McpSearchClient
import com.example.aichat.data.search.SearchAgentRepository
import com.example.aichat.data.support.SupportContextBuilder
import com.example.aichat.service.WeatherService
import com.example.aichat.ui.searchagent.SearchAgentViewModel
import com.example.aichat.ui.viewmodel.AuthViewModel
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
    
    /**
     * Local LLM API service
     * Singleton: Retrofit service interface for local LLM servers (Ollama, LM Studio, etc.)
     */
    single { 
        val okHttpClient: OkHttpClient = get()
        val gson: Gson = get()
        val retrofit = NetworkModule.provideLocalLLMRetrofit(okHttpClient, gson)
        NetworkModule.provideLocalLLMApiService(retrofit)
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
    single { get<ChatDatabase>().userDao() }
    
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
    
    /**
     * BrightData MCP Client for reading articles by URLs
     * Used for article reading functionality
     * 
     * Note: BrightData MCP uses stdio transport (npx -y @brightdata/mcp)
     * For Android, uses REST bridge mode to connect to a local HTTP bridge server
     * Bridge server should run on development machine and wrap the BrightData MCP stdio server
     */
    single {
        BrightDataMcpClient(
            gson = get(),
            httpClient = get(),
            useRestBridge = true // Use REST bridge for Android
        )
    }
    
    /**
     * CRM MCP Client for accessing user tickets and support information
     * Used for support chat functionality
     * 
     * Note: CRM MCP uses stdio transport (java -jar crm-mcp-server.jar)
     * For Android, uses REST bridge mode to connect to a local HTTP bridge server
     * Bridge server should run on development machine and wrap the CRM MCP stdio server
     */
    single {
        CrmMcpClient(
            gson = get(),
            httpClient = get(),
            useRestBridge = true, // Use REST bridge for Android
            bridgeUrl = Config.CRM_MCP_BRIDGE_URL
        )
    }
    
    /**
     * RAG Service
     * Searches product documentation for relevant context
     */
    single {
        RagService(
            context = androidContext()
        )
    }
    
    /**
     * Support Context Builder
     * Combines RAG documentation and CRM ticket context for support chat
     */
    single {
        SupportContextBuilder(
            authManager = get(),
            crmMcpClient = get(),
            ragService = get(),
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
     * User Repository
     * Singleton: Manages user data persistence
     */
    single { UserRepository(get()) }
    
    /**
     * Auth Manager
     * Singleton: Manages authentication state with EncryptedSharedPreferences
     */
    single { AuthManager(androidContext()) }
    
    /**
     * Search Agent Repository
     * Singleton: Orchestrates MCP search and LLM calls for article search
     * Also fetches article content and generates summaries using BrightData and LLM
     */
    single {
        SearchAgentRepository(
            mcpSearchClient = get(),
            brightDataMcpClient = get(),
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
            localLLMApiService = get(),
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
            injectedExternalMemoryRepository = get(),
            injectedSupportContextBuilder = get()
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
    
    /**
     * Auth ViewModel
     * Scoped to Activity lifecycle
     * 
     * Manages authentication UI state
     */
    viewModel {
        AuthViewModel(
            application = androidApplication(),
            userRepository = get(),
            authManager = get()
        )
    }
}

/**
 * List of all Koin modules
 * Used in Application.onCreate() for initialization
 */
val allModules = listOf(appModule)

