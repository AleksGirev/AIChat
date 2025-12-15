package com.example.aichat

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
import com.example.aichat.ui.theme.AIChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val config = McpConfig(this)
        config.setEnabled(true)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}