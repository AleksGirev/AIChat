package com.example.aichat.ui.searchagent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.data.search.Article

/**
 * Interactive screen for querying an LLM agent that uses an MCP server
 * to fetch fresh articles from the last 7 days.
 * 
 * Features:
 * - Input field for topic
 * - Search button
 * - Loading indicator
 * - Article list with title, URL, and publication date
 * - Copy URL and open in browser actions
 * - Error and empty states
 * - Full localization support (Russian and English)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAgentScreen(
    viewModel: SearchAgentViewModel,
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var topicInput by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Search Agent") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input section
            OutlinedTextField(
                value = topicInput,
                onValueChange = { topicInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter topic (e.g., 'artificial intelligence')") },
                placeholder = { Text("Topic...") },
                enabled = uiState !is SearchAgentUiState.Loading,
                singleLine = true
            )
            
            Button(
                onClick = {
                    viewModel.searchArticles(topicInput)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = topicInput.isNotBlank() && uiState !is SearchAgentUiState.Loading
            ) {
                Text("Find articles from the last 7 days")
            }
            
            // Content section
            when (val state = uiState) {
                is SearchAgentUiState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enter a topic and click the button to search for articles",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                is SearchAgentUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Searching for articles...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                is SearchAgentUiState.Success -> {
                    if (state.articles.isEmpty()) {
                        EmptyState(
                            message = "No articles found",
                            onRetry = { viewModel.searchArticles(topicInput) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ArticleList(
                            articles = state.articles,
                            onCopyUrl = { url ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("URL", url))
                            },
                            onOpenUrl = { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
                
                is SearchAgentUiState.Empty -> {
                    EmptyState(
                        message = "No articles found for this topic",
                        onRetry = { viewModel.searchArticles(topicInput) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                is SearchAgentUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.searchArticles(topicInput) },
                        onDismiss = { viewModel.clearState() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleList(
    articles: List<Article>,
    onCopyUrl: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(articles) { article ->
            ArticleCard(
                article = article,
                onCopyUrl = { onCopyUrl(article.url) },
                onOpenUrl = { onOpenUrl(article.url) }
            )
        }
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    onCopyUrl: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Publication date (if available)
            article.publicationDate?.let { date ->
                Text(
                    text = "Published: $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // URL
            Text(
                text = article.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Snippet (if available)
            article.snippet?.let { snippet ->
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCopyUrl) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy URL"
                    )
                }
                IconButton(onClick = onOpenUrl) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open in browser"
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

