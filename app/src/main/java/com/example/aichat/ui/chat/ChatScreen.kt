package com.example.aichat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.data.auth.AuthManager
import com.example.aichat.data.local.UserRepository
import com.example.aichat.service.WeatherService
import com.example.aichat.ui.model.UiMessage
import com.example.aichat.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Main chat screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSettingsClick: () -> Unit = {},
    onComparisonClick: () -> Unit = {},
    onTokenComparisonClick: () -> Unit = {},
    onChatListClick: () -> Unit = {},
    onSearchAgentClick: () -> Unit = {},
    onDataAnalystClick: () -> Unit = {},
    onVoiceAgentClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val modelName by viewModel.modelName.collectAsStateWithLifecycle()
    val supportMode by viewModel.supportMode.collectAsStateWithLifecycle()
    
    // Weather service for summary snackbars
    val weatherService: WeatherService = koinInject()
    val shouldShowSummary by weatherService.shouldShowSummary.collectAsStateWithLifecycle()
    val summaryText by weatherService.summaryText.collectAsStateWithLifecycle()
    
    // Auth and user info
    val authManager: AuthManager = koinInject()
    val userRepository: UserRepository = koinInject()
    val scope = rememberCoroutineScope()
    var currentUsername by remember { mutableStateOf<String?>(null) }
    
    // Load user info
    LaunchedEffect(Unit) {
        val userId = authManager.getUserId()
        if (userId != null) {
            scope.launch {
                val user = userRepository.getUserById(userId)
                currentUsername = user?.username
            }
        }
    }
    
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var showModelSelector by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Scroll to bottom when new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Show error snackbar
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Error will be shown via SnackbarHost
        }
    }
    
    // Show weather summary snackbar
    LaunchedEffect(shouldShowSummary, summaryText) {
        if (shouldShowSummary && summaryText != null) {
            snackbarHostState.showSnackbar(
                message = summaryText!!,
                duration = SnackbarDuration.Long
            )
            weatherService.clearSummaryFlag()
        }
    }
    
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("AI Chat")
                        Text(
                            text = viewModel.getModelDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show user info and support mode
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            currentUsername?.let { username ->
                                Text(
                                    text = "@$username",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (supportMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "Support Mode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Settings button (keep in top bar)
                    IconButton(onClick = onSettingsClick) {
                        Text("⚙️", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scrollable action buttons bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Chat list button
                    FilterChip(
                        selected = false,
                        onClick = onChatListClick,
                        label = { Text("Chats") }
                    )
                    // Model selector button
                    FilterChip(
                        selected = showModelSelector,
                        onClick = { showModelSelector = !showModelSelector },
                        label = { Text("Model") }
                    )
                    // Token comparison button
                    FilterChip(
                        selected = false,
                        onClick = onTokenComparisonClick,
                        label = { Text("Tokens") }
                    )
                    // Comparison button
                    FilterChip(
                        selected = false,
                        onClick = onComparisonClick,
                        label = { Text("Compare") }
                    )
                    // New Chat button
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.startNewChat() },
                        enabled = !isLoading,
                        label = { Text("New") }
                    )
                    // Search Agent button
                    FilterChip(
                        selected = false,
                        onClick = onSearchAgentClick,
                        label = { Text("Search") }
                    )
                    // Data Analyst button
                    FilterChip(
                        selected = false,
                        onClick = onDataAnalystClick,
                        label = { Text("Analyst") }
                    )
                    // Voice Agent button
                    FilterChip(
                        selected = false,
                        onClick = onVoiceAgentClick,
                        label = { Text("🎤 Voice") }
                    )
                    // Support mode toggle
                    FilterChip(
                        selected = supportMode,
                        onClick = { viewModel.setSupportMode(!supportMode) },
                        label = { Text(if (supportMode) "🛟 Support" else "💬 Chat") }
                    )
                }
            }
            
            // Model selector dropdown
            if (showModelSelector) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Выберите модель:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.availableModels.forEach { (modelId, displayName) ->
                                FilterChip(
                                    selected = modelName == modelId,
                                    onClick = { 
                                        viewModel.setModel(modelId)
                                        showModelSelector = false
                                    },
                                    label = { Text(displayName) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isLoading
                                )
                            }
                        }
                    }
                }
            }
            
            // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start a conversation",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            items(messages) { message ->
                MessageItem(message = message)
            }
            
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Thinking...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Error message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }
        
        // Input field and send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = !isLoading,
                singleLine = false,
                maxLines = 4
            )
            
            Button(
                onClick = {
                    if (inputText.text.isNotBlank()) {
                        viewModel.sendMessage(inputText.text)
                        inputText = TextFieldValue("")
                    }
                },
                enabled = !isLoading && inputText.text.isNotBlank()
            ) {
                Text("Send")
            }
        }
        }
    }
}

