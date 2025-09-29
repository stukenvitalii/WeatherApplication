package com.example.weatherapplication.data

import androidx.core.net.toUri
import com.example.weatherapplication.data.model.City
import com.example.weatherapplication.data.model.DailyForecast
import com.example.weatherapplication.data.model.WeatherInfo
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherRepository(private val language: String = "ru") {

    private fun langOrDefault(): String = when (language.lowercase()) {
        "ru" -> "ru"
        "en" -> "en"
        else -> "en"
    }

    fun searchCities(query: String, limit: Int = 5): List<City> {
        if (query.length < 2) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val lang = langOrDefault()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=$limit&language=$lang&format=json"
        val resp = httpGet(url) ?: return emptyList()
        val json = JSONObject(resp)
        val results = json.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<City>()
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            val lat = o.optDouble("latitude")
            val lon = o.optDouble("longitude")
            val name = o.optString("name", "")
            val country = o.optString("country", "").ifEmpty { null }
            if (!lat.isNaN() && !lon.isNaN() && name.isNotBlank()) {
                list.add(City(name = listOfNotNull(name, country).joinToString(", "), country = country, latitude = lat, longitude = lon))
            }
        }
        return list
    }

    fun getWeatherByCity(name: String): WeatherInfo? {
        val encoded = URLEncoder.encode(name, "UTF-8")
        val lang = langOrDefault()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=$lang&format=json"
        val resp = httpGet(url) ?: return null
        val json = JSONObject(resp)
        val results = json.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)
        val lat = first.optDouble("latitude")
        val lon = first.optDouble("longitude")
        if (lat.isNaN() || lon.isNaN()) return null
        val nameStr = first.optString("name", "")
        val countryStr = first.optString("country", "")
        val city = City(
            name = listOfNotNull(nameStr.ifEmpty { null }, countryStr.ifEmpty { null }).joinToString(", "),
            country = countryStr.ifEmpty { null },
            latitude = lat,
            longitude = lon
        )
        return getWeatherInternal(city)
    }

    fun getWeatherByCoordinates(lat: Double, lon: Double): WeatherInfo? {
        val lang = langOrDefault()
        val reverseUrl = "https://geocoding-api.open-meteo.com/v1/reverse?latitude=$lat&longitude=$lon&count=1&language=$lang"
        val city: City = runCatching {
            val resp = httpGet(reverseUrl)
            if (resp != null) {
                val json = JSONObject(resp)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val o = results.getJSONObject(0)
                    val nameStr = o.optString("name", "")
                    val countryStr = o.optString("country", "")
                    City(
                        name = listOfNotNull(nameStr.ifEmpty { null }, countryStr.ifEmpty { null }).joinToString(", "),
                        country = countryStr.ifEmpty { null },
                        latitude = lat,
                        longitude = lon
                    )
                } else City(defaultCurrentLocationLabel(), null, lat, lon)
            } else City(defaultCurrentLocationLabel(), null, lat, lon)
        }.getOrElse { City(defaultCurrentLocationLabel(), null, lat, lon) }
        return getWeatherInternal(city)
    }

    private fun defaultCurrentLocationLabel(): String = when (langOrDefault()) {
        "ru" -> "Текущее местоположение"
        else -> "Current location"
    }

    private fun getWeatherInternal(city: City): WeatherInfo? {
        val uri = "https://api.open-meteo.com/v1/forecast".toUri().buildUpon()
            .appendQueryParameter("latitude", city.latitude.toString())
            .appendQueryParameter("longitude", city.longitude.toString())
            .appendQueryParameter(
                "current",
                // добавлены новые текущие параметры
                "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day,cloud_cover,visibility,pressure_msl,dew_point_2m,uv_index"
            )
            .appendQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min")
            .appendQueryParameter("forecast_days", "5")
            .appendQueryParameter("timezone", "auto")
            .build().toString()
        val resp = httpGet(uri) ?: return null
        val json = JSONObject(resp)
        val current = json.optJSONObject("current") ?: return null
        val temp = current.optDouble("temperature_2m")
        if (temp.isNaN()) return null
        val code = current.optInt("weather_code", 0)
        val wind = current.optDouble("wind_speed_10m")
        val isDayInt = current.optInt("is_day", 1)
        val isNight = isDayInt == 0
        val windVal = if (wind.isNaN()) null else wind

        // дополнительные индексы
        val feelsLike = current.optDouble("apparent_temperature").takeUnless { it.isNaN() }
        val uv = current.optDouble("uv_index").takeUnless { it.isNaN() }
        val cloudCover = current.optDouble("cloud_cover").takeUnless { it.isNaN() }?.roundToInt()
        val visibilityMeters = current.optDouble("visibility").takeUnless { it.isNaN() }
        val pressure = current.optDouble("pressure_msl").takeUnless { it.isNaN() }
        val dewPoint = current.optDouble("dew_point_2m").takeUnless { it.isNaN() }
        val visibilityKm = visibilityMeters?.div(1000.0)?.let { (it * 10).roundToInt() / 10.0 }

        val dailyJson = json.optJSONObject("daily")
        val dailyList = mutableListOf<DailyForecast>()
        if (dailyJson != null) {
            val times = dailyJson.optJSONArray("time")
            val codes = dailyJson.optJSONArray("weather_code")
            val tmax = dailyJson.optJSONArray("temperature_2m_max")
            val tmin = dailyJson.optJSONArray("temperature_2m_min")
            if (times != null && codes != null && tmax != null && tmin != null) {
                val n = listOf(times.length(), codes.length(), tmax.length(), tmin.length()).minOrNull() ?: 0
                for (i in 0 until n) {
                    val dateIso = times.optString(i, "")
                    val c = codes.optInt(i, 0)
                    val max = tmax.optDouble(i)
                    val min = tmin.optDouble(i)
                    if (!max.isNaN() && !min.isNaN() && dateIso.isNotBlank()) {
                        dailyList.add(
                            DailyForecast(
                                dateIso = dateIso,
                                tempMaxC = (max * 10).roundToInt() / 10.0,
                                tempMinC = (min * 10).roundToInt() / 10.0,
                                code = c
                            )
                        )
                    }
                }
            }
        }

        return WeatherInfo(
            city = city,
            temperatureC = (temp * 10).roundToInt() / 10.0,
            windSpeed = windVal,
            description = mapWeatherCode(code),
            code = code,
            isNight = isNight,
            daily = dailyList,
            feelsLikeC = feelsLike?.let { (it * 10).roundToInt() / 10.0 },
            uvIndex = uv?.let { (it * 10).roundToInt() / 10.0 },
            cloudCoverPct = cloudCover,
            visibilityKm = visibilityKm,
            pressureHpa = pressure?.let { (it * 10).roundToInt() / 10.0 },
            dewPointC = dewPoint?.let { (it * 10).roundToInt() / 10.0 },
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun mapWeatherCode(code: Int): String {
        val lang = langOrDefault()
        return when (lang) {
            "ru" -> when (code) {
                0 -> "Ясно"
                1, 2 -> "Малооблачно"
                3 -> "Пасмурно"
                45, 48 -> "Туман"
                51, 53, 55 -> "Моросящий дождь"
                61, 63, 65 -> "Дождь"
                66, 67 -> "Ледяной дождь"
                71, 73, 75 -> "Снег"
                77 -> "Снегопад"
                80, 81, 82 -> "Ливни"
                85, 86 -> "Снеговые ливни"
                95 -> "Гроза"
                96, 99 -> "Гроза с градом"
                else -> "Неизвестно"
            }
            else -> when (code) {
                0 -> "Clear"
                1, 2 -> "Partly cloudy"
                3 -> "Overcast"
                45, 48 -> "Fog"
                51, 53, 55 -> "Drizzle"
                61, 63, 65 -> "Rain"
                66, 67 -> "Freezing rain"
                71, 73, 75 -> "Snow"
                77 -> "Snowfall"
                80, 81, 82 -> "Showers"
                85, 86 -> "Snow showers"
                95 -> "Thunderstorm"
                96, 99 -> "Thunderstorm with hail"
                else -> "Unknown"
            }
        }
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
