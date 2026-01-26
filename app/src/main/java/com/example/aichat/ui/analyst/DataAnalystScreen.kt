package com.example.aichat.ui.analyst

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Экран для анализа данных с помощью локальной LLM
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAnalystScreen(
    viewModel: DataAnalystViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val currentFileName by viewModel.currentFileName.collectAsStateWithLifecycle()
    val dataSummary by viewModel.dataSummary.collectAsStateWithLifecycle()
    val conversationHistory by viewModel.conversationHistory.collectAsStateWithLifecycle()
    val useAppLogs by viewModel.useAppLogs.collectAsStateWithLifecycle()
    val appLogsCount by viewModel.appLogsCount.collectAsStateWithLifecycle()
    
    var questionText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Launcher для выбора файла
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadData(it) }
    }
    
    // Прокручиваем к последнему сообщению при добавлении нового
    LaunchedEffect(conversationHistory.size) {
        if (conversationHistory.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(conversationHistory.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Локальный аналитик данных") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (currentFileName != null) {
                        TextButton(onClick = { viewModel.clearData() }) {
                            Text("Очистить")
                        }
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
            // Информация о загруженном файле
            if (currentFileName != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Загружен файл:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentFileName ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (dataSummary != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Сводка:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dataSummary ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Кнопка загрузки файла и использования логов приложения
            if (currentFileName == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Использование логов приложения
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (useAppLogs) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Использовать логи приложения",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Логов в базе: $appLogsCount",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Switch(
                                    checked = useAppLogs,
                                    onCheckedChange = { viewModel.toggleUseAppLogs() },
                                    enabled = !isLoading
                                )
                            }
                            if (useAppLogs) {
                                Text(
                                    text = "Логи приложения будут автоматически загружены для анализа",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    
                    // Загрузка файла
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Или загрузите файл для анализа",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Поддерживаются форматы: CSV, JSON, TXT (логи)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                enabled = !isLoading
                            ) {
                                Text("Выбрать файл")
                            }
                        }
                    }
                }
            }
            
            // История разговора
            if (conversationHistory.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversationHistory) { message ->
                        AnalystMessageItem(message = message)
                    }
                }
            } else if (currentFileName != null || useAppLogs) {
                // Подсказки для вопросов
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Примеры вопросов:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val exampleQuestions = listOf(
                            "Какая ошибка встречается чаще всего?",
                            "Сколько всего записей в данных?",
                            "Какие есть числовые колонки?",
                            "Покажи статистику по данным",
                            "Где больше всего пользователей теряется?"
                        )
                        exampleQuestions.forEach { question ->
                            TextButton(
                                onClick = { questionText = question },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "• $question",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            // Поле ввода вопроса
            if (currentFileName != null || useAppLogs) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            label = { Text("Задайте вопрос о данных") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.analyze(questionText)
                                    questionText = ""
                                },
                                enabled = questionText.isNotBlank() && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Анализировать")
                            }
                        }
                    }
                }
            }
            
            // Сообщение об ошибке
            errorMessage?.let { error ->
                LaunchedEffect(error) {
                    scope.launch {
                        // Автоматически скрываем ошибку через 5 секунд
                        kotlinx.coroutines.delay(5000)
                        viewModel.clearError()
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Text("✕")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalystMessageItem(message: AnalystMessage) {
    val (backgroundColor, textColor) = when (message) {
        is AnalystMessage.User -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        is AnalystMessage.Assistant -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        is AnalystMessage.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = when (message) {
                    is AnalystMessage.User -> "Вы:"
                    is AnalystMessage.Assistant -> "Аналитик:"
                    is AnalystMessage.Error -> "Ошибка:"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}
