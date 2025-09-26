package com.example.weatherapplication.data

import android.content.Context
import android.content.SharedPreferences
import com.example.weatherapplication.data.model.City
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

class SavedCitiesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    private val key = "saved_cities"

    private val lastNameKey = "last_name"
    private val lastCountryKey = "last_country"
    private val lastLatKey = "last_lat"
    private val lastLonKey = "last_lon"

    fun getAll(): List<City> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val nameStr = o.optString("name", "")
                val name = nameStr.ifEmpty { null } ?: return@mapNotNull null
                val lat = o.optDouble("lat")
                val lon = o.optDouble("lon")
                val countryStr = o.optString("country", "")
                val country = countryStr.ifEmpty { null }
                if (lat.isNaN() || lon.isNaN()) null else City(name, country, lat, lon)
            }
        }.getOrElse { emptyList() }
    }

    fun add(city: City) {
        val list = getAll().toMutableList()
        if (list.any { it.latitude == city.latitude && it.longitude == city.longitude }) return
        list.add(city)
        save(list)
    }

    fun remove(city: City) {
        val list = getAll().toMutableList()
        list.removeAll { it.latitude == city.latitude && it.longitude == city.longitude }
        save(list)
    }

    private fun save(list: List<City>) {
        val arr = JSONArray()
        list.forEach { c ->
            val o = JSONObject()
            o.put("name", c.name)
            o.put("country", c.country ?: "")
            o.put("lat", c.latitude)
            o.put("lon", c.longitude)
            arr.put(o)
        }
        prefs.edit { putString(key, arr.toString()) }
    }

    // Last selected city
    fun setLast(city: City) {
        prefs.edit {
            putString(lastNameKey, city.name)
                .putString(lastCountryKey, city.country ?: "")
                .putFloat(lastLatKey, city.latitude.toFloat())
                .putFloat(lastLonKey, city.longitude.toFloat())
        }
    }

    fun getLast(): City? {
        val name = prefs.getString(lastNameKey, null) ?: return null
        val lat = prefs.getFloat(lastLatKey, Float.NaN).toDouble()
        val lon = prefs.getFloat(lastLonKey, Float.NaN).toDouble()
        if (lat.isNaN() || lon.isNaN()) return null
        val country = prefs.getString(lastCountryKey, null)
        return City(name = name, country = country?.ifEmpty { null }, latitude = lat, longitude = lon)
    }
}
