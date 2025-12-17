package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for weather data.
 * Provides database operations for weather temperature storage.
 */
@Dao
interface WeatherDao {
    
    /**
     * Get all weather entries ordered by timestamp (most recent first)
     */
    @Query("SELECT * FROM weather_data ORDER BY timestamp DESC")
    fun getAllWeather(): Flow<List<WeatherEntity>>
    
    /**
     * Get weather entry by city name (most recent)
     */
    @Query("SELECT * FROM weather_data WHERE city = :city ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByCity(city: String): WeatherEntity?
    
    /**
     * Get latest weather data for all Belarusian cities
     * Uses a simpler approach: get the most recent timestamp, then get all entries with that timestamp
     */
    @Query("""
        SELECT * FROM weather_data 
        WHERE timestamp = (SELECT MAX(timestamp) FROM weather_data)
        ORDER BY city
    """)
    suspend fun getLatestForAllCities(): List<WeatherEntity>
    
    /**
     * Get average temperature from latest data for all cities
     * Uses a simpler approach: get all entries with the most recent timestamp
     */
    @Query("""
        SELECT AVG(temperature) 
        FROM weather_data 
        WHERE timestamp = (SELECT MAX(timestamp) FROM weather_data)
    """)
    suspend fun getAverageTemperature(): Double?
    
    /**
     * Get all weather entries (for debugging)
     */
    @Query("SELECT * FROM weather_data ORDER BY timestamp DESC")
    suspend fun getAllWeatherEntries(): List<WeatherEntity>
    
    /**
     * Get all weather entries for a specific city ordered by timestamp (most recent first)
     */
    @Query("SELECT * FROM weather_data WHERE city = :city ORDER BY timestamp DESC")
    suspend fun getAllByCity(city: String): List<WeatherEntity>
    
    /**
     * Insert a weather entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherEntity)
    
    /**
     * Insert multiple weather entries
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWeather(weatherList: List<WeatherEntity>)
    
    /**
     * Delete weather entries older than timestamp
     */
    @Query("DELETE FROM weather_data WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    /**
     * Delete all weather entries
     */
    @Query("DELETE FROM weather_data")
    suspend fun deleteAll()
}

