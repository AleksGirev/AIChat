package com.example.aichat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aichat.ui.chat.ChatScreen
import com.example.aichat.ui.settings.SettingsScreen
import com.example.aichat.ui.viewmodel.ChatViewModel

/**
 * Navigation state for the app
 */
sealed class Screen {
    object Chat : Screen()
    object Settings : Screen()
}

/**
 * Main navigation composable
 */
@Composable
fun AppNavigation(
    viewModel: ChatViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    
    when (currentScreen) {
        is Screen.Chat -> {
            ChatScreen(
                viewModel = viewModel,
                onSettingsClick = { currentScreen = Screen.Settings }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
    }
}

