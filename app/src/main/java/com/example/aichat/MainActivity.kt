package com.example.aichat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.aichat.data.mcp.McpConfig
import com.example.aichat.ui.navigation.AppNavigation
import com.example.aichat.ui.navigation.Screen
import com.example.aichat.ui.theme.AIChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val config = McpConfig(this)
        config.setEnabled(true)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if opened from notification
        val initialScreen = when {
            intent.getBooleanExtra("show_sync_updates", false) -> Screen.WeatherSummary
            intent.hasExtra("sync_update_id") -> Screen.WeatherSummary
            else -> null
        }
        
        setContent {
            AIChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(initialScreen = initialScreen)
                }
            }
        }
    }
    
}