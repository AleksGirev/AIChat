package com.example.aichat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.example.aichat.data.auth.AuthManager
import com.example.aichat.ui.auth.AuthScreen
import com.example.aichat.ui.chat.ChatScreen
import com.example.aichat.ui.chat.ChatListScreen
import com.example.aichat.ui.comparison.ModelComparisonScreen
import com.example.aichat.ui.comparison.TokenComparisonScreen
import com.example.aichat.ui.searchagent.SearchAgentScreen
import com.example.aichat.ui.settings.SettingsScreen
import com.example.aichat.ui.summary.WeatherSummaryScreen
import com.example.aichat.ui.viewmodel.AuthViewModel
import com.example.aichat.ui.viewmodel.ChatViewModel
import com.example.aichat.ui.viewmodel.ModelComparisonViewModel
import com.example.aichat.ui.viewmodel.TokenComparisonViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Navigation state for the app
 */
sealed class Screen {
    object Auth : Screen()
    object Chat : Screen()
    object ChatList : Screen()
    object Settings : Screen()
    object ModelComparison : Screen()
    object TokenComparison : Screen()
    object WeatherSummary : Screen()
    object SearchAgent : Screen()
}

/**
 * Main navigation composable
 * 
 * Uses Koin for ViewModel injection to support dependency injection
 * of repositories and other components.
 */
@Composable
fun AppNavigation(
    viewModel: ChatViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
    comparisonViewModel: ModelComparisonViewModel = viewModel(),
    tokenComparisonViewModel: TokenComparisonViewModel = viewModel(),
    initialScreen: Screen? = null
) {
    val authManager: AuthManager = koinInject()
    val isAuthenticated = remember { mutableStateOf(authManager.isAuthenticated()) }
    
    // Check authentication state on startup
    LaunchedEffect(Unit) {
        isAuthenticated.value = authManager.isAuthenticated()
    }
    
    // Determine initial screen based on authentication
    var currentScreen by remember { 
        mutableStateOf<Screen>(
            initialScreen ?: if (isAuthenticated.value) Screen.Chat else Screen.Auth
        )
    }
    
    when (currentScreen) {
        is Screen.Auth -> {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    isAuthenticated.value = true
                    currentScreen = Screen.Chat
                }
            )
        }
        is Screen.Chat -> {
            ChatScreen(
                viewModel = viewModel,
                onSettingsClick = { currentScreen = Screen.Settings },
                onComparisonClick = { currentScreen = Screen.ModelComparison },
                onTokenComparisonClick = { currentScreen = Screen.TokenComparison },
                onChatListClick = { currentScreen = Screen.ChatList },
                onSearchAgentClick = { currentScreen = Screen.SearchAgent }
            )
        }
        is Screen.ChatList -> {
            ChatListScreen(
                viewModel = viewModel,
                onSessionClick = { sessionId ->
                    viewModel.loadSession(sessionId)
                    currentScreen = Screen.Chat
                },
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
        is Screen.ModelComparison -> {
            ModelComparisonScreen(
                viewModel = comparisonViewModel,
                onBackClick = { currentScreen = Screen.Chat },
                onTokenComparisonClick = { currentScreen = Screen.TokenComparison }
            )
        }
        is Screen.TokenComparison -> {
            TokenComparisonScreen(
                viewModel = tokenComparisonViewModel,
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
        is Screen.WeatherSummary -> {
            val weatherRepository: com.example.aichat.data.local.WeatherRepository = koinInject()
            WeatherSummaryScreen(
                weatherRepository = weatherRepository,
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
        is Screen.SearchAgent -> {
            val searchAgentViewModel: com.example.aichat.ui.searchagent.SearchAgentViewModel = koinViewModel()
            SearchAgentScreen(
                viewModel = searchAgentViewModel,
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
    }
}

