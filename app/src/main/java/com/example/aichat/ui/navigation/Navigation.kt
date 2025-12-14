package com.example.aichat.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aichat.ui.chat.ChatScreen
import com.example.aichat.ui.chat.ChatListScreen
import com.example.aichat.ui.comparison.ModelComparisonScreen
import com.example.aichat.ui.comparison.TokenComparisonScreen
import com.example.aichat.ui.settings.SettingsScreen
import com.example.aichat.ui.viewmodel.ChatViewModel
import com.example.aichat.ui.viewmodel.ModelComparisonViewModel
import com.example.aichat.ui.viewmodel.TokenComparisonViewModel

/**
 * Navigation state for the app
 */
sealed class Screen {
    object Chat : Screen()
    object ChatList : Screen()
    object Settings : Screen()
    object ModelComparison : Screen()
    object TokenComparison : Screen()
}

/**
 * Main navigation composable
 */
@Composable
fun AppNavigation(
    viewModel: ChatViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
            .getInstance(LocalContext.current.applicationContext as Application)
    ),
    comparisonViewModel: ModelComparisonViewModel = viewModel(),
    tokenComparisonViewModel: TokenComparisonViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    
    when (currentScreen) {
        is Screen.Chat -> {
            ChatScreen(
                viewModel = viewModel,
                onSettingsClick = { currentScreen = Screen.Settings },
                onComparisonClick = { currentScreen = Screen.ModelComparison },
                onTokenComparisonClick = { currentScreen = Screen.TokenComparison },
                onChatListClick = { currentScreen = Screen.ChatList }
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
    }
}

