package com.example.aichat.ui.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.data.model.ComparisonMessage
import com.example.aichat.data.model.ModelComparisonResult
import com.example.aichat.ui.viewmodel.ModelComparisonViewModel

/**
 * Main model comparison screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelComparisonScreen(
    viewModel: ModelComparisonViewModel,
    onModelSelectionClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedModels by viewModel.selectedModels.collectAsStateWithLifecycle()
    val comparisonMessages by viewModel.comparisonMessages.collectAsStateWithLifecycle()
    val comparisonResults by viewModel.comparisonResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingStates by viewModel.loadingStates.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var currentPrompt by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new messages are added
    LaunchedEffect(comparisonMessages.size) {
        if (comparisonMessages.isNotEmpty()) {
            listState.animateScrollToItem(comparisonMessages.size - 1)
        }
    }
    
    // Also scroll when current comparison completes and clear loading prompt
    LaunchedEffect(comparisonMessages.size, isLoading) {
        if (comparisonMessages.isNotEmpty() && !isLoading) {
            listState.animateScrollToItem(comparisonMessages.size - 1)
            // Clear current prompt if it matches the last message
            val lastMessage = comparisonMessages.lastOrNull()
            if (lastMessage?.userPrompt == currentPrompt) {
                currentPrompt = null
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Comparison") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    // Clear results button
                    TextButton(
                        onClick = { viewModel.clearResults() },
                        enabled = !isLoading && comparisonMessages.isNotEmpty()
                    ) {
                        Text("Clear")
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
            // Selected models info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Comparing Models:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedModels.forEachIndexed { index, model ->
                        Text(
                            text = "${index + 1}. ${model.name}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Messages list - chat-like format
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (comparisonMessages.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Enter a message to compare models",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                items(comparisonMessages) { message ->
                    // User prompt
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 280.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = message.userPrompt,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    // Comparison results
                    if (message.results.size == 2) {
                        // Side-by-side comparison
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            message.results.forEach { result ->
                                ComparisonResultCard(
                                    result = result,
                                    isLoading = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        // Show differences if both succeeded
                        if (message.results.all { it.isSuccess }) {
                            ComparisonDifferencesCard(
                                result1 = message.results[0],
                                result2 = message.results[1],
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Fallback to vertical list
                        message.results.forEach { result ->
                            ComparisonResultCard(
                                result = result,
                                isLoading = false
                            )
                        }
                    }
                }
                
                // Show loading state for current comparison
                if (isLoading && currentPrompt != null) {
                    item {
                        // User prompt
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Card(
                                modifier = Modifier.widthIn(max = 280.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = currentPrompt ?: "",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                        
                        // Loading comparison results
                        if (comparisonResults.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                comparisonResults.forEach { result ->
                                    ComparisonResultCard(
                                        result = result,
                                        isLoading = loadingStates[result.modelName] ?: true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            // Show loading indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedModels.forEach { _ ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
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
                    placeholder = { Text("Type a message to compare...") },
                    enabled = !isLoading,
                    singleLine = false,
                    maxLines = 4
                )
                
                Button(
                    onClick = {
                        if (inputText.text.isNotBlank()) {
                            currentPrompt = inputText.text
                            viewModel.compareModels(inputText.text)
                            inputText = TextFieldValue("")
                        }
                    },
                    enabled = !isLoading && inputText.text.isNotBlank()
                ) {
                    Text("Compare")
                }
            }
        }
    }
}

/**
 * Card displaying a single model comparison result
 */
@Composable
fun ComparisonResultCard(
    result: ModelComparisonResult,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val displayName = when {
        result.modelName.contains("nova") -> "Amazon Nova"
        result.modelName.lowercase().contains("yandex") -> "YandexGPT"
        else -> result.modelName.split("/").lastOrNull() ?: result.modelName
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.isSuccess) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Model name header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricChip(
                    label = "Time",
                    value = result.getFormattedResponseTime()
                )
                MetricChip(
                    label = "Tokens",
                    value = "${result.tokenCount}"
                )
                MetricChip(
                    label = "Cost",
                    value = result.getFormattedCost()
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Response content or error - scrollable
            if (result.isSuccess && result.responseContent != null) {
                val contentScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = result.responseContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(contentScrollState)
                    )
                }
            } else {
                Text(
                    text = result.error ?: "No response",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Small chip displaying a metric
 */
@Composable
fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Card displaying differences between two model results
 */
@Composable
fun ComparisonDifferencesCard(
    result1: ModelComparisonResult,
    result2: ModelComparisonResult,
    modifier: Modifier = Modifier
) {
    val timeDiff = result1.responseTimeMs - result2.responseTimeMs
    val tokenDiff = result1.tokenCount - result2.tokenCount
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Comparison Differences",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Response Time Difference
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Response Time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (timeDiff > 0) {
                            "${result2.modelName.split("/").lastOrNull() ?: result2.modelName} is ${String.format("%.2fs", timeDiff / 1000.0)} faster"
                        } else if (timeDiff < 0) {
                            "${result1.modelName.split("/").lastOrNull() ?: result1.modelName} is ${String.format("%.2fs", -timeDiff / 1000.0)} faster"
                        } else {
                            "Same response time"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Token Count Difference
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Token Count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (tokenDiff > 0) {
                            "${result2.modelName.split("/").lastOrNull() ?: result2.modelName} uses ${tokenDiff} fewer tokens"
                        } else if (tokenDiff < 0) {
                            "${result1.modelName.split("/").lastOrNull() ?: result1.modelName} uses ${-tokenDiff} fewer tokens"
                        } else {
                            "Same token count"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

