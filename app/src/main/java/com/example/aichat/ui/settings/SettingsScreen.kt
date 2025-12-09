package com.example.aichat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.ui.viewmodel.ChatViewModel

/**
 * Settings screen for configuring chat parameters
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    
    var systemPromptText by remember { mutableStateOf(TextFieldValue(systemPrompt)) }
    
    // Update local state when system prompt changes externally
    LaunchedEffect(systemPrompt) {
        if (systemPromptText.text != systemPrompt) {
            systemPromptText = TextFieldValue(systemPrompt)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Temperature Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Temperature",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Controls randomness in responses. Lower values make responses more focused and deterministic, while higher values make them more creative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Temperature value display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Value: ${String.format("%.2f", temperature)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Range: 0.0 - 2.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Temperature slider
                    Slider(
                        value = temperature.toFloat(),
                        onValueChange = { viewModel.setTemperature(it.toDouble()) },
                        valueRange = 0f..2f,
                        steps = 19, // 0.1 increments
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Quick preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetButton(
                            label = "Creative",
                            value = 1.0,
                            currentValue = temperature,
                            onClick = { viewModel.setTemperature(1.0) }
                        )
                        PresetButton(
                            label = "Balanced",
                            value = 0.7,
                            currentValue = temperature,
                            onClick = { viewModel.setTemperature(0.7) }
                        )
                        PresetButton(
                            label = "Focused",
                            value = 0.3,
                            currentValue = temperature,
                            onClick = { viewModel.setTemperature(0.3) }
                        )
                    }
                }
            }
            
            // System Prompt Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "System Prompt",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Set a system message that defines the assistant's behavior and personality. This will be included at the beginning of every conversation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = systemPromptText,
                        onValueChange = { 
                            systemPromptText = it
                            viewModel.setSystemPrompt(it.text)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("You are a helpful assistant...") },
                        minLines = 4,
                        maxLines = 8,
                        label = { Text("System Prompt") }
                    )
                    
                    // Clear button
                    if (systemPrompt.isNotBlank()) {
                        TextButton(
                            onClick = {
                                systemPromptText = TextFieldValue("")
                                viewModel.setSystemPrompt("")
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
            
            // Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About Settings",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "These settings will apply to all new conversations. Changes take effect immediately for new messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    value: Double,
    currentValue: Double,
    onClick: () -> Unit
) {
    val isSelected = currentValue == value
    
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) }
    )
}

