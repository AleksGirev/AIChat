package com.example.aichat.data.local

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for managing weather data.
 * Provides a clean API for accessing weather data from Workers and UI.
 */
class WeatherRepository(
    private val weatherDao: WeatherDao
) {
    
    companion object {
        // Belarusian cities to track
        val BELARUSIAN_CITIES = listOf(
            "Gomel",
            "Minsk",
            "Mogilev",
            "Grodno",
            "Brest",
            "Vitebsk"
        )
        
        // Cleanup: delete weather data older than 7 days
        private const val CLEANUP_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L
    }
    
    /**
     * Get all weather entries as Flow
     */
    fun getAllWeather(): Flow<List<WeatherEntity>> = weatherDao.getAllWeather()
    
    /**
     * Get latest weather data for a specific city
     */
    suspend fun getLatestByCity(city: String): WeatherEntity? =
        withContext(Dispatchers.IO) {
            weatherDao.getLatestByCity(city)
        }
    
    /**
     * Get latest weather data for all cities
     */
    suspend fun getLatestForAllCities(): List<WeatherEntity> =
        withContext(Dispatchers.IO) {
            // First try the optimized query
            val latest = weatherDao.getLatestForAllCities()
            Log.d("WeatherRepository", "Latest query returned ${latest.size} entries")
            
            // If no data found with the latest timestamp query, try getting all and filtering
            if (latest.isEmpty()) {
                val all = weatherDao.getAllWeatherEntries()
                Log.d("WeatherRepository", "All entries query returned ${all.size} entries")
                if (all.isNotEmpty()) {
                    // Group by city and get latest for each
                    val latestByCity = all.groupBy { it.city }
                        .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp } }
                        .values
                        .filterNotNull()
                        .toList()
                    Log.d("WeatherRepository", "After grouping: ${latestByCity.size} cities")
                    latestByCity.sortedBy { it.city }
                } else {
                    emptyList()
                }
            } else {
                latest
            }
        }
    
    /**
     * Get average temperature from latest data for all cities
     */
    suspend fun getAverageTemperature(): Double? =
        withContext(Dispatchers.IO) {
            weatherDao.getAverageTemperature()
        }
    
    /**
     * Get all weather entries for a specific city
     */
    suspend fun getAllByCity(city: String): List<WeatherEntity> =
        withContext(Dispatchers.IO) {
            weatherDao.getAllByCity(city)
        }
    
    /**
     * Save weather data for a city
     */
    suspend fun saveWeather(city: String, temperature: Double, additionalData: String? = null) =
        withContext(Dispatchers.IO) {
            val entity = WeatherEntity(
                id = UUID.randomUUID().toString(),
                city = city,
                temperature = temperature,
                timestamp = System.currentTimeMillis(),
                additionalData = additionalData
            )
            weatherDao.insertWeather(entity)
        }
    
    /**
     * Save weather data for multiple cities
     */
    suspend fun saveWeatherBatch(weatherData: Map<String, Double>) =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val entities = weatherData.map { (city, temperature) ->
                WeatherEntity(
                    id = UUID.randomUUID().toString(),
                    city = city,
                    temperature = temperature,
                    timestamp = timestamp
                )
            }
            Log.d("WeatherRepository", "Saving ${entities.size} weather entries with timestamp $timestamp")
            weatherDao.insertAllWeather(entities)
            Log.d("WeatherRepository", "Successfully saved weather data")
            
            // Verify data was saved
            val all = weatherDao.getAllWeatherEntries()
            Log.d("WeatherRepository", "Total entries in DB after save: ${all.size}")
        }
    
    /**
     * Clean up old weather data (call periodically from Worker)
     */
    suspend fun cleanupOldData() =
        withContext(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - CLEANUP_THRESHOLD_MS
            weatherDao.deleteOlderThan(threshold)
        }
}

