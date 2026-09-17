package com.neoplayer.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("neo_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT, AMOLED }
enum class Accent { ORANGE, GREEN, RED, BLUE, CUSTARD, PURPLE, CYAN, PINK, INDIGO, TEAL, GOLD, CUSTOM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.ORANGE,
    val customColor: Int = 0xFFFF7A1A.toInt(),
    val language: String = "system",
    val dynamicArtwork: Boolean = true,
    val reduceMotion: Boolean = false,
    val rememberQueue: Boolean = true,
    val resumeLastSong: Boolean = true,
    val lyricsMode: String = "auto",
    val minDurationMs: Long = 10_000,
    val defaultSpeed: Float = 1f,
    val translationEnabled: Boolean = true,
    val romanizationEnabled: Boolean = true,
    val lyricsFontSize: Int = 20,
    val lyricsAutoScroll: Boolean = true,
    val crossfadeMs: Long = 0L,
    val loudnessNormalization: Boolean = false,
    val normalizationTargetLufs: Float = -14f,
    val normalizationMode: String = "smart",
    val automixEnabled: Boolean = false,
    val advancedAutomixEnabled: Boolean = true,
    val gaplessEnabled: Boolean = true,
    val libraryViewMode: String = "list",
    val recentSearches: List<String> = emptyList(),
    val strictOfflineMode: Boolean = false,
    val offlineBackupEnabled: Boolean = true,
    val offlineBackupAutoRefresh: Boolean = true,
    val offlineBackupLimit: Int = 100,
    val offlineBackupMood: String = "all",
    val offlineBackupGenre: String = "all"
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
        val minDuration = longPreferencesKey("min_duration")
        val defaultSpeed = floatPreferencesKey("default_speed")
        val translation = booleanPreferencesKey("lyrics_translation")
        val romanization = booleanPreferencesKey("lyrics_romanization")
        val lyricsFontSize = intPreferencesKey("lyrics_font_size")
        val lyricsAutoScroll = booleanPreferencesKey("lyrics_auto_scroll")
        val crossfadeMs = longPreferencesKey("crossfade_ms")
        val loudnessNormalization = booleanPreferencesKey("loudness_normalization")
        val normalizationTargetLufs = floatPreferencesKey("normalization_target_lufs")
        val normalizationMode = stringPreferencesKey("normalization_mode")
        val automixEnabled = booleanPreferencesKey("automix_enabled")
        val advancedAutomixEnabled = booleanPreferencesKey("advanced_automix_enabled")
        val gaplessEnabled = booleanPreferencesKey("gapless_enabled")
        val libraryViewMode = stringPreferencesKey("library_view_mode")
        val recentSearches = stringPreferencesKey("recent_searches")
        val strictOfflineMode = booleanPreferencesKey("strict_offline_mode")
        val offlineBackupEnabled = booleanPreferencesKey("offline_backup_enabled")
        val offlineBackupAutoRefresh = booleanPreferencesKey("offline_backup_auto_refresh")
        val offlineBackupLimit = intPreferencesKey("offline_backup_limit")
        val offlineBackupMood = stringPreferencesKey("offline_backup_mood")
        val offlineBackupGenre = stringPreferencesKey("offline_backup_genre")
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
            lyricsMode = p[Keys.lyricsMode] ?: "auto",
            minDurationMs = p[Keys.minDuration] ?: 10_000,
            defaultSpeed = (p[Keys.defaultSpeed] ?: 1f).coerceIn(.25f, 3f),
            translationEnabled = p[Keys.translation] ?: true,
            romanizationEnabled = p[Keys.romanization] ?: true,
            lyricsFontSize = (p[Keys.lyricsFontSize] ?: 20).coerceIn(12, 42),
            lyricsAutoScroll = p[Keys.lyricsAutoScroll] ?: true,
            crossfadeMs = (p[Keys.crossfadeMs] ?: 0L).coerceIn(0L, 12_000L),
            loudnessNormalization = p[Keys.loudnessNormalization] ?: false,
            normalizationTargetLufs = (p[Keys.normalizationTargetLufs] ?: -14f).coerceIn(-23f, -8f),
            normalizationMode = p[Keys.normalizationMode]?.takeIf { it in setOf("smart", "replaygain", "analysis") } ?: "smart",
            automixEnabled = p[Keys.automixEnabled] ?: false,
            advancedAutomixEnabled = p[Keys.advancedAutomixEnabled] ?: true,
            gaplessEnabled = p[Keys.gaplessEnabled] ?: true,
            libraryViewMode = p[Keys.libraryViewMode]?.takeIf { it == "grid" || it == "list" } ?: "list",
            recentSearches = decodeRecentSearches(p[Keys.recentSearches].orEmpty()),
            strictOfflineMode = p[Keys.strictOfflineMode] ?: false,
            offlineBackupEnabled = p[Keys.offlineBackupEnabled] ?: true,
            offlineBackupAutoRefresh = p[Keys.offlineBackupAutoRefresh] ?: true,
            offlineBackupLimit = (p[Keys.offlineBackupLimit] ?: 100).coerceIn(20, 500),
            offlineBackupMood = p[Keys.offlineBackupMood]?.ifBlank { "all" } ?: "all",
            offlineBackupGenre = p[Keys.offlineBackupGenre]?.ifBlank { "all" } ?: "all"
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setAccent(value: Accent) = context.dataStore.edit { it[Keys.accent] = value.name }
    suspend fun setCustomColor(value: Int) = context.dataStore.edit { it[Keys.customColor] = value; it[Keys.accent] = Accent.CUSTOM.name }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.language] = value }
    suspend fun setReduceMotion(value: Boolean) = context.dataStore.edit { it[Keys.reduceMotion] = value }
    suspend fun setDynamicArtwork(value: Boolean) = context.dataStore.edit { it[Keys.dynamicArtwork] = value }
    suspend fun setRememberQueue(value: Boolean) = context.dataStore.edit { it[Keys.rememberQueue] = value }
    suspend fun setResumeLastSong(value: Boolean) = context.dataStore.edit { it[Keys.resumeLast] = value }
    suspend fun setLyricsMode(value: String) = context.dataStore.edit { it[Keys.lyricsMode] = value }
    suspend fun setMinDuration(value: Long) = context.dataStore.edit { it[Keys.minDuration] = value.coerceAtLeast(0L) }
    suspend fun setDefaultSpeed(value: Float) = context.dataStore.edit { it[Keys.defaultSpeed] = value.coerceIn(.25f, 3f) }
    suspend fun setTranslation(value: Boolean) = context.dataStore.edit { it[Keys.translation] = value }
    suspend fun setRomanization(value: Boolean) = context.dataStore.edit { it[Keys.romanization] = value }
    suspend fun setLyricsFontSize(value: Int) = context.dataStore.edit { it[Keys.lyricsFontSize] = value.coerceIn(12, 42) }
    suspend fun setLyricsAutoScroll(value: Boolean) = context.dataStore.edit { it[Keys.lyricsAutoScroll] = value }
    suspend fun setCrossfadeMs(value: Long) = context.dataStore.edit { it[Keys.crossfadeMs] = value.coerceIn(0L, 12_000L) }
    suspend fun setLoudnessNormalization(value: Boolean) = context.dataStore.edit { it[Keys.loudnessNormalization] = value }
    suspend fun setNormalizationTargetLufs(value: Float) = context.dataStore.edit { it[Keys.normalizationTargetLufs] = value.coerceIn(-23f, -8f) }
    suspend fun setNormalizationMode(value: String) = context.dataStore.edit { it[Keys.normalizationMode] = value.takeIf { v -> v in setOf("smart", "replaygain", "analysis") } ?: "smart" }
    suspend fun setAutomixEnabled(value: Boolean) = context.dataStore.edit { it[Keys.automixEnabled] = value }
    suspend fun setAdvancedAutomixEnabled(value: Boolean) = context.dataStore.edit { it[Keys.advancedAutomixEnabled] = value }
    suspend fun setGaplessEnabled(value: Boolean) = context.dataStore.edit { it[Keys.gaplessEnabled] = value }
    suspend fun setLibraryViewMode(value: String) = context.dataStore.edit { it[Keys.libraryViewMode] = if (value == "grid") "grid" else "list" }
    suspend fun setStrictOfflineMode(value: Boolean) = context.dataStore.edit { it[Keys.strictOfflineMode] = value }
    suspend fun setOfflineBackupEnabled(value: Boolean) = context.dataStore.edit { it[Keys.offlineBackupEnabled] = value }
    suspend fun setOfflineBackupAutoRefresh(value: Boolean) = context.dataStore.edit { it[Keys.offlineBackupAutoRefresh] = value }
    suspend fun setOfflineBackupLimit(value: Int) = context.dataStore.edit { it[Keys.offlineBackupLimit] = value.coerceIn(20, 500) }
    suspend fun setOfflineBackupMood(value: String) = context.dataStore.edit { it[Keys.offlineBackupMood] = value.ifBlank { "all" } }
    suspend fun setOfflineBackupGenre(value: String) = context.dataStore.edit { it[Keys.offlineBackupGenre] = value.ifBlank { "all" } }

    suspend fun addRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = decodeRecentSearches(prefs[Keys.recentSearches].orEmpty()).toMutableList()
            current.removeAll { it.equals(normalized, ignoreCase = true) }
            current.add(0, normalized)
            prefs[Keys.recentSearches] = encodeRecentSearches(current.take(MAX_RECENT_SEARCHES))
        }
    }

    suspend fun removeRecentSearch(query: String) = context.dataStore.edit { prefs ->
        prefs[Keys.recentSearches] = encodeRecentSearches(decodeRecentSearches(prefs[Keys.recentSearches].orEmpty()).filterNot { it.equals(query, ignoreCase = true) })
    }
    suspend fun clearRecentSearches() = context.dataStore.edit { it.remove(Keys.recentSearches) }

    private companion object {
        const val SEARCH_SEPARATOR = '\u001F'
        const val MAX_RECENT_SEARCHES = 12
        fun decodeRecentSearches(raw: String): List<String> = raw.split(SEARCH_SEPARATOR).map(String::trim).filter(String::isNotBlank).distinct().take(MAX_RECENT_SEARCHES)
        fun encodeRecentSearches(values: List<String>): String = values.map { it.replace(SEARCH_SEPARATOR.toString(), " ").trim() }.filter(String::isNotBlank).distinct().take(MAX_RECENT_SEARCHES).joinToString(SEARCH_SEPARATOR.toString())
    }
}
