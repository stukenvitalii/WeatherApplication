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
    val daily: List<DailyForecast> = emptyList()
)

fun LocationProvider.Coordinates.toCityNameFallback(): String = "Текущее местоположение"
