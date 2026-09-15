package com.neoplayer.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("neo_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT, AMOLED }
enum class Accent { ORANGE, GREEN, RED, BLUE, CUSTARD, CUSTOM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.ORANGE,
    val customColor: Int = 0xFFFF7A1A.toInt(),
    val language: String = "system",
    val dynamicArtwork: Boolean = true,
    val reduceMotion: Boolean = false,
    val rememberQueue: Boolean = true,
    val resumeLastSong: Boolean = true,
    val lyricsMode: String = "auto"
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val customColor = intPreferencesKey("custom_color")
        val language = stringPreferencesKey("language")
        val dynamicArtwork = booleanPreferencesKey("dynamic_artwork")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val rememberQueue = booleanPreferencesKey("remember_queue")
        val resumeLast = booleanPreferencesKey("resume_last")
        val lyricsMode = stringPreferencesKey("lyrics_mode")
    }

    val values: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            accent = runCatching { Accent.valueOf(p[Keys.accent] ?: "ORANGE") }.getOrDefault(Accent.ORANGE),
            customColor = p[Keys.customColor] ?: 0xFFFF7A1A.toInt(),
            language = p[Keys.language] ?: "system",
            dynamicArtwork = p[Keys.dynamicArtwork] ?: true,
            reduceMotion = p[Keys.reduceMotion] ?: false,
            rememberQueue = p[Keys.rememberQueue] ?: true,
            resumeLastSong = p[Keys.resumeLast] ?: true,
            lyricsMode = p[Keys.lyricsMode] ?: "auto"
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setAccent(value: Accent) = context.dataStore.edit { it[Keys.accent] = value.name }
    suspend fun setCustomColor(value: Int) = context.dataStore.edit { it[Keys.customColor] = value; it[Keys.accent] = Accent.CUSTOM.name }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.language] = value }
    suspend fun setReduceMotion(value: Boolean) = context.dataStore.edit { it[Keys.reduceMotion] = value }
    suspend fun setRememberQueue(value: Boolean) = context.dataStore.edit { it[Keys.rememberQueue] = value }
    suspend fun setLyricsMode(value: String) = context.dataStore.edit { it[Keys.lyricsMode] = value }
}
