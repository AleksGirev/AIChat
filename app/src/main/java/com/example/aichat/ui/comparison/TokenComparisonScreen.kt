package com.example.aichat.ui.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.data.model.RequestType
import com.example.aichat.data.model.TokenComparisonResult
import com.example.aichat.ui.viewmodel.TokenComparisonViewModel

/**
 * Screen for comparing token usage with different request lengths
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenComparisonScreen(
    viewModel: TokenComparisonViewModel,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val modelName by viewModel.modelName.collectAsStateWithLifecycle()
    val comparisonResults by viewModel.comparisonResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сравнение токенов") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.clearResults() },
                        enabled = !isLoading && comparisonResults.isNotEmpty()
                    ) {
                        Text("Очистить")
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
                .verticalScroll(rememberScrollState())
        ) {
            // Model selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Выберите модель:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Model selection buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.availableModels.forEach { (modelId, displayName) ->
                            FilterChip(
                                selected = modelName == modelId,
                                onClick = { viewModel.setModel(modelId) },
                                label = { Text(displayName) },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Текущая модель: ${viewModel.getModelDisplayName()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Этот тест сравнивает поведение модели при разных длинах запросов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Run comparison button
            Button(
                onClick = { viewModel.runComparison() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выполняется сравнение...")
                } else {
                    Text("Запустить сравнение")
                }
            }
            
            // Error message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Results
            if (comparisonResults.isNotEmpty()) {
                Text(
                    text = "Результаты сравнения",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                comparisonResults.forEach { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    TokenComparisonResultCard(result = result)
                }
            } else if (!isLoading) {
                Text(
                    text = "Нажмите кнопку выше, чтобы запустить сравнение",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        }
    }
}

/**
 * Card displaying a single token comparison result
 */
@Composable
fun TokenComparisonResultCard(result: TokenComparisonResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.exceedsLimit) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Request type header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = result.requestType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.requestType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (result.isSuccess) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "✓",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "✗",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Token information
            TokenInfoRow(
                label = "Оценка токенов запроса",
                value = "${result.estimatedRequestTokens}",
                isWarning = result.exceedsLimit
            )
            
            result.actualRequestTokens?.let {
                TokenInfoRow(
                    label = "Фактические токены запроса",
                    value = "$it"
                )
            }
            
            result.actualResponseTokens?.let {
                TokenInfoRow(
                    label = "Токены ответа",
                    value = "$it"
                )
            }
            
            result.actualTotalTokens?.let {
                TokenInfoRow(
                    label = "Всего токенов",
                    value = "$it",
                    isHighlight = true
                )
            }
            
            TokenInfoRow(
                label = "Лимит модели",
                value = "${result.modelLimit}",
                isWarning = result.exceedsLimit
            )
            
            if (result.exceedsLimit) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "⚠ Запрос превышает лимит модели",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Response time
            if (result.responseTimeMs > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                TokenInfoRow(
                    label = "Время ответа",
                    value = formatResponseTime(result.responseTimeMs)
                )
            }
            
            // Finish reason (may indicate truncation)
            result.finishReason?.let { reason ->
                Spacer(modifier = Modifier.height(8.dp))
                TokenInfoRow(
                    label = "Причина завершения",
                    value = reason,
                    isWarning = reason == "length" || reason == "max_tokens"
                )
            }
            
            // Error message
            result.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Ошибка: $error",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Response content preview
            result.responseContent?.let { content ->
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ответ модели:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (content.length > 200) {
                        content.take(200) + "..."
                    } else {
                        content
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // User message preview
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Запрос пользователя:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (result.userMessage.length > 200) {
                    result.userMessage.take(200) + "..."
                } else {
                    result.userMessage
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun TokenInfoRow(
    label: String,
    value: String,
    isWarning: Boolean = false,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isWarning -> MaterialTheme.colorScheme.error
                isHighlight -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

fun formatResponseTime(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}мс"
        else -> String.format("%.2fс", ms / 1000.0)
    }
}

