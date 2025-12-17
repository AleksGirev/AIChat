package com.example.aichat.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.data.local.SyncStatus
import com.example.aichat.data.local.SyncUpdateRepository
import com.example.aichat.service.WeatherService
import com.example.aichat.sync.SyncNotificationManager
import com.example.aichat.sync.WorkManagerScheduler
import com.example.aichat.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Settings screen for configuring chat parameters and background sync
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Inject dependencies via Koin
    val workManagerScheduler: WorkManagerScheduler = koinInject()
    val syncUpdateRepository: SyncUpdateRepository = koinInject()
    val notificationManager: SyncNotificationManager = koinInject()
    val weatherService: WeatherService = koinInject()
    
    // Coroutine scope for async operations
    val scope = rememberCoroutineScope()
    
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    
    var systemPromptText by remember { mutableStateOf(TextFieldValue(systemPrompt)) }
    
    // Sync state
    var isSyncEnabled by remember { mutableStateOf(syncUpdateRepository.isSyncEnabled()) }
    var syncStatus by remember { mutableStateOf(syncUpdateRepository.getSyncStatus()) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // Notification permission state
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }
    
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
            // Background Sync Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Background Sync",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Automatically sync data from MCP server every 30 minutes and receive hourly summary notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Enable/Disable sync toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Background Sync",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = isSyncEnabled,
                            onCheckedChange = { enabled ->
                                isSyncEnabled = enabled
                                syncUpdateRepository.setSyncEnabled(enabled)
                                if (enabled) {
                                    workManagerScheduler.scheduleAllPeriodicWork()
                                } else {
                                    workManagerScheduler.cancelAllWork()
                                }
                            }
                        )
                    }
                    
                    // Last sync status
                    if (syncStatus.lastSyncTimestamp > 0) {
                        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        val lastSyncDate = dateFormat.format(Date(syncStatus.lastSyncTimestamp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last sync: $lastSyncDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (syncStatus.lastSyncSuccess) "✓ Success" else "✗ Failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (syncStatus.lastSyncSuccess) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                        }
                        
                        if (!syncStatus.lastSyncSuccess && syncStatus.errorMessage != null) {
                            Text(
                                text = syncStatus.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Manual sync buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sync Now button
                        Button(
                            onClick = {
                                isSyncing = true
                                workManagerScheduler.triggerSyncThenNotify()
                                // Refresh status after delay
                                // Note: In production, observe WorkManager LiveData for completion
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isSyncing) "Syncing..." else "Sync Now")
                        }
                        
                        // Show Summary button
                        OutlinedButton(
                            onClick = {
                                workManagerScheduler.triggerImmediateNotificationSummary()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Show Summary")
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Weather Summary Section
                    Text(
                        text = "Weather Summary (Gomel)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Text(
                        text = "Generate a summary of all collected weather data for Gomel using AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Weather Summary button
                    var isGeneratingSummary by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            isGeneratingSummary = true
                            scope.launch {
                                try {
                                    weatherService.triggerSummary()
                                } catch (e: Exception) {
                                    // Error handled by service
                                } finally {
                                    isGeneratingSummary = false
                                }
                            }
                        },
                        enabled = !isGeneratingSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isGeneratingSummary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isGeneratingSummary) "Generating..." else "Generate Weather Summary")
                    }
                    
                    // Reset syncing state after a delay (simplified - in production use WorkManager observer)
                    LaunchedEffect(isSyncing) {
                        if (isSyncing) {
                            kotlinx.coroutines.delay(30000) // 30 second timeout
                            isSyncing = false
                            syncStatus = syncUpdateRepository.getSyncStatus()
                        }
                    }
                }
            }
            
            // Notification Permission Section (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Permission",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (hasNotificationPermission) 
                                        "Granted - you will receive sync updates" 
                                    else 
                                        "Required for sync notifications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasNotificationPermission) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (!hasNotificationPermission) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                ) {
                                    Text("Grant")
                                }
                            } else {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
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

