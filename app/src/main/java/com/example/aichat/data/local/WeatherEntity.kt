package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing weather data from Belarusian cities.
 * Stores temperature data fetched from MCP server for weather summary notifications.
 */
@Entity(tableName = "weather_data")
data class WeatherEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * City name (e.g., "Gomel", "Minsk", "Mogilev", "Grodno", "Brest", "Vitebsk")
     */
    val city: String,
    
    /**
     * Temperature in Celsius
     */
    val temperature: Double,
    
    /**
     * Timestamp when the weather data was fetched
     */
    val timestamp: Long,
    
    /**
     * Additional weather data as JSON string (optional, for future use)
     */
    val additionalData: String? = null
)

