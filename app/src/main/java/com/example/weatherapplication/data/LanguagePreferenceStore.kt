package com.example.weatherapplication.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import androidx.core.content.edit

class LanguagePreferenceStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    private val key = "app_language"

    fun getLanguage(): String {
        val stored = prefs.getString(key, null)
        return stored ?: Locale.getDefault().language
    }

    fun setLanguage(lang: String) {
        prefs.edit { putString(key, lang) }
    }
}

