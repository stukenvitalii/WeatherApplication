package com.example.weatherapplication.data.model

import com.example.weatherapplication.location.LocationProvider

data class City(
    val name: String,
    val country: String?,
    val latitude: Double,
    val longitude: Double
)

data class DailyForecast(
    val dateIso: String,
    val tempMaxC: Double,
    val tempMinC: Double,
    val code: Int
)

data class WeatherInfo(
    val city: City,
    val temperatureC: Double,
    val windSpeed: Double?,
    val description: String,
    val code: Int,
    val isNight: Boolean,
    val daily: List<DailyForecast> = emptyList(),
    // --- New indices ---
    val feelsLikeC: Double? = null,
    val uvIndex: Double? = null,
    val cloudCoverPct: Int? = null,
    val visibilityKm: Double? = null,
    val pressureHpa: Double? = null,
    val dewPointC: Double? = null,
    // Timestamp when fetched (epoch millis)
    val fetchedAt: Long = System.currentTimeMillis()
)

fun LocationProvider.Coordinates.toCityNameFallback(): String = "Текущее местоположение"
