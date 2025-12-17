package com.example.aichat.ui.summary

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aichat.data.local.WeatherEntity
import com.example.aichat.data.local.WeatherRepository
import kotlinx.coroutines.launch

/**
 * Weather summary screen showing Belarusian cities temperature data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSummaryScreen(
    weatherRepository: WeatherRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var weatherData by remember { mutableStateOf<List<WeatherEntity>>(emptyList()) }
    var averageTemperature by remember { mutableStateOf<Double?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    fun loadWeatherData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val latest = weatherRepository.getLatestForAllCities()
                val average = weatherRepository.getAverageTemperature()
                Log.d("WeatherSummary", "Loaded ${latest.size} cities, average: $average")
                weatherData = latest
                averageTemperature = average
                if (latest.isEmpty() && average == null) {
                    errorMessage = "Нет данных о погоде. Запустите синхронизацию в настройках."
                }
            } catch (e: Exception) {
                Log.e("WeatherSummary", "Error loading weather data", e)
                errorMessage = "Ошибка загрузки данных: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadWeatherData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Summary") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadWeatherData() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Average temperature card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Средняя температура в Беларуси",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (averageTemperature != null) {
                                Text(
                                    text = "${String.format("%.1f", averageTemperature)}°C",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Text(
                                    text = "Нет данных",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // City temperatures
                if (weatherData.isNotEmpty()) {
                    item {
                        Text(
                            text = "Температура по городам:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(weatherData) { weather ->
                        CityTemperatureCard(weather)
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage ?: "Нет данных о погоде. Запустите синхронизацию в настройках.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { loadWeatherData() }) {
                                    Text("Обновить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityTemperatureCard(weather: WeatherEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = weather.city,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${String.format("%.1f", weather.temperature)}°C",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

