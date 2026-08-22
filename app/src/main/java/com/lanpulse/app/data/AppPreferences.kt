package com.lanpulse.app.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent { DYNAMIC, TEAL, RASPBERRY, INDIGO, AMBER, FOREST }

data class AppSettings(
    val languageTag: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.TEAL,
)

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lanpulse_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        languageTag = prefs.getString(KEY_LANGUAGE, "") ?: "",
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
        accent = runCatching {
            Accent.valueOf(prefs.getString(KEY_ACCENT, Accent.TEAL.name) ?: Accent.TEAL.name)
        }.getOrDefault(Accent.TEAL),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_LANGUAGE, settings.languageTag)
            .putString(KEY_THEME, settings.themeMode.name)
            .putString(KEY_ACCENT, settings.accent.name)
            .apply()
    }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
    }
}
