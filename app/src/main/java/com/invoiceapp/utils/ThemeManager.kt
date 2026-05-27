package com.invoiceapp.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.invoiceapp.R

object ThemeManager {
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_BLUE = "blue"
    const val THEME_GREEN = "green"
    const val THEME_PURPLE = "purple"

    private const val PREF_THEME = "selected_theme"
    private const val DEFAULT_THEME = THEME_LIGHT

    fun applyTheme(theme: String) {
        val mode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getThemeResId(theme: String): Int {
        return when (theme) {
            THEME_LIGHT -> R.style.Theme_InvoiceApp_Light
            THEME_DARK -> R.style.Theme_InvoiceApp_Dark
            THEME_BLUE -> R.style.Theme_InvoiceApp_Blue
            THEME_GREEN -> R.style.Theme_InvoiceApp_Green
            THEME_PURPLE -> R.style.Theme_InvoiceApp_Purple
            else -> R.style.Theme_InvoiceApp_Light
        }
    }

    fun saveTheme(context: Context, theme: String) {
        val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_THEME, theme).apply()
        applyTheme(theme)
    }

    fun getSavedTheme(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getString(PREF_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }
}
