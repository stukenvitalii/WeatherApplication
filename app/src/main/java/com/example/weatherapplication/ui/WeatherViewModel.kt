package com.example.weatherapplication.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.weatherapplication.data.SavedCitiesStore
import com.example.weatherapplication.data.WeatherRepository
import com.example.weatherapplication.data.model.City
import com.example.weatherapplication.data.model.WeatherInfo
import java.util.concurrent.Executors

class WeatherViewModel(private val store: SavedCitiesStore? = null) {

    private val repo = WeatherRepository()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var uiState by mutableStateOf(WeatherUiState())
        private set

    private var pendingSearch: Runnable? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private fun firstPart(name: String?): String? = name?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }

    fun startSearch() {
        uiState = uiState.copy(isSearching = true)
    }

    fun stopSearch() {
        uiState = uiState.copy(isSearching = false, suggestions = emptyList())
    }

    fun refresh() {
        val lat = lastLat
        val lon = lastLon
        when {
            lat != null && lon != null -> loadByCoordinates(lat, lon)
            uiState.weather != null -> loadByCoordinates(uiState.weather!!.city.latitude, uiState.weather!!.city.longitude)
            uiState.query.isNotBlank() -> loadWeatherByCity(uiState.query)
            else -> Unit
        }
    }

    fun setQuery(query: String) {
        uiState = uiState.copy(query = query, isSearching = true)
        // debounce suggestions
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        if (query.length < 2) {
            uiState = uiState.copy(suggestions = emptyList())
            return
        }
        val task = Runnable {
            executor.execute {
                val list = runCatching { repo.searchCities(query) }.getOrDefault(emptyList())
                mainHandler.post { uiState = uiState.copy(suggestions = list) }
            }
        }
        pendingSearch = task
        mainHandler.postDelayed(task, 300)
    }

    fun selectSuggestion(city: City) {
        val title = firstPart(city.name)
        uiState = uiState.copy(query = "", suggestions = emptyList(), isSearching = false, topTitle = title)
        loadByCoordinates(city.latitude, city.longitude, displayName = title ?: city.name)
    }

    fun loadWeatherByCity(city: String) {
        if (city.isBlank()) return
        val title = firstPart(city)
        uiState = uiState.copy(
            isLoading = true,
            error = null,
            suggestions = emptyList(),
            topTitle = title ?: city,
            isSearching = false
        )
        executor.execute {
            val info = runCatching { repo.getWeatherByCity(city) }.getOrNull()
            mainHandler.post {
                if (info != null) {
                    lastLat = info.city.latitude
                    lastLon = info.city.longitude
                    store?.setLast(info.city)
                    uiState = uiState.copy(isLoading = false, weather = info, error = null)
                } else {
                    uiState = uiState.copy(isLoading = false, weather = null, error = "Город не найден или ошибка сети")
                }
            }
        }
    }

    fun loadByCoordinates(lat: Double, lon: Double, displayName: String? = null) {
        lastLat = lat
        lastLon = lon
        displayName?.let { dn ->
            firstPart(dn)?.let { uiTitle ->
                uiState = uiState.copy(topTitle = uiTitle)
            }
        }
        uiState = uiState.copy(isLoading = true, error = null, suggestions = emptyList())
        executor.execute {
            val info: WeatherInfo? = runCatching { repo.getWeatherByCoordinates(lat, lon) }.getOrNull()
            mainHandler.post {
                if (info != null) {
                    val resolvedName = firstPart(info.city.name)
                    val safeName = resolvedName?.takeIf { it != "Текущее местоположение" }
                    store?.setLast(info.city)
                    uiState = uiState.copy(
                        isLoading = false,
                        weather = info,
                        query = "",
                        error = null,
                        isSearching = false,
                        topTitle = safeName ?: uiState.topTitle
                    )
                } else {
                    uiState = uiState.copy(isLoading = false, weather = null, error = "Не удалось получить погоду")
                }
            }
        }
    }
}

data class WeatherUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val weather: WeatherInfo? = null,
    val error: String? = null,
    val suggestions: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val topTitle: String? = null,
)
