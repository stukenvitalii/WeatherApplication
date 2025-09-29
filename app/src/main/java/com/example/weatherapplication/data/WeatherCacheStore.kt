package com.example.weatherapplication.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.weatherapplication.data.model.City
import com.example.weatherapplication.data.model.DailyForecast
import com.example.weatherapplication.data.model.WeatherInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Простейший кеш последней полученной погоды по координатам.
 * Используется ключ формата: weather_{lat}_{lon} (округление до 4 знаков).
 * Хранение в SharedPreferences, JSON сериализация.
 */
class WeatherCacheStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    private fun key(lat: Double, lon: Double): String = "weather_${"%.4f".format(lat)}_${"%.4f".format(lon)}"

    fun save(info: WeatherInfo) {
        val obj = JSONObject().apply {
            put("name", info.city.name)
            put("country", info.city.country ?: "")
            put("lat", info.city.latitude)
            put("lon", info.city.longitude)
            put("temp", info.temperatureC)
            put("wind", info.windSpeed ?: JSONObject.NULL)
            put("desc", info.description)
            put("code", info.code)
            put("isNight", info.isNight)
            put("feels", info.feelsLikeC ?: JSONObject.NULL)
            put("uv", info.uvIndex ?: JSONObject.NULL)
            put("cloud", info.cloudCoverPct ?: JSONObject.NULL)
            put("vis", info.visibilityKm ?: JSONObject.NULL)
            put("press", info.pressureHpa ?: JSONObject.NULL)
            put("dew", info.dewPointC ?: JSONObject.NULL)
            put("fetched", info.fetchedAt)
            val dailyArr = JSONArray()
            info.daily.forEach { d ->
                dailyArr.put(JSONObject().apply {
                    put("date", d.dateIso)
                    put("tmax", d.tempMaxC)
                    put("tmin", d.tempMinC)
                    put("code", d.code)
                })
            }
            put("daily", dailyArr)
        }
        prefs.edit { putString(key(info.city.latitude, info.city.longitude), obj.toString()) }
    }

    fun get(lat: Double, lon: Double): WeatherInfo? {
        val str = prefs.getString(key(lat, lon), null) ?: return null
        return runCatching {
            val o = JSONObject(str)
            val city = City(
                name = o.optString("name", ""),
                country = o.optString("country", "").ifEmpty { null },
                latitude = o.optDouble("lat"),
                longitude = o.optDouble("lon")
            )
            val dailyArr = o.optJSONArray("daily")
            val daily = mutableListOf<DailyForecast>()
            if (dailyArr != null) {
                for (i in 0 until dailyArr.length()) {
                    val d = dailyArr.getJSONObject(i)
                    val dateIso = d.optString("date", "")
                    val tmax = d.optDouble("tmax")
                    val tmin = d.optDouble("tmin")
                    val code = d.optInt("code")
                    if (!tmax.isNaN() && !tmin.isNaN() && dateIso.isNotBlank()) {
                        daily.add(DailyForecast(dateIso, tmax, tmin, code))
                    }
                }
            }
            WeatherInfo(
                city = city,
                temperatureC = o.optDouble("temp"),
                windSpeed = o.optDouble("wind").let { if (it.isNaN()) null else it },
                description = o.optString("desc", ""),
                code = o.optInt("code"),
                isNight = o.optBoolean("isNight", false),
                daily = daily,
                feelsLikeC = o.optDouble("feels").let { if (it.isNaN()) null else it },
                uvIndex = o.optDouble("uv").let { if (it.isNaN()) null else it },
                cloudCoverPct = if (o.has("cloud") && !o.isNull("cloud")) o.optInt("cloud") else null,
                visibilityKm = o.optDouble("vis").let { if (it.isNaN()) null else it },
                pressureHpa = o.optDouble("press").let { if (it.isNaN()) null else it },
                dewPointC = o.optDouble("dew").let { if (it.isNaN()) null else it },
                fetchedAt = o.optLong("fetched", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    fun clear(lat: Double, lon: Double) {
        prefs.edit { remove(key(lat, lon)) }
    }
}

