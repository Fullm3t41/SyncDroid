package com.syncdroid.app.ui.theme

import android.content.Context

class ThemePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /** Uses the phone appearance until the user explicitly chooses a theme. */
    fun load(systemDarkTheme: Boolean): Boolean = if (preferences.contains(KEY_DARK_THEME)) {
        preferences.getBoolean(KEY_DARK_THEME, systemDarkTheme)
    } else {
        systemDarkTheme
    }

    fun save(darkTheme: Boolean) {
        preferences.edit().putBoolean(KEY_DARK_THEME, darkTheme).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance_preferences"
        const val KEY_DARK_THEME = "dark_theme"
    }
}
