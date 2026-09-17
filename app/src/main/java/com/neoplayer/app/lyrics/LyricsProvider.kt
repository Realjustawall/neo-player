package com.neoplayer.app.lyrics

import com.neoplayer.app.data.SongEntity
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class LyricsPayload(
    val original: String,
    val translation: String = "",
    val romanization: String = "",
    val synchronized: Boolean = false,
    val source: String
)

interface LyricsProvider {
    val id: String
    suspend fun find(song: SongEntity): LyricsPayload?
}

/** Configurable JSON provider; no scraping endpoint or secret is embedded in source. */
class ConfiguredJsonLyricsProvider(private val baseUrl: String, private val apiKey: String) : LyricsProvider {
    override val id = "configured-json"
    override suspend fun find(song: SongEntity): LyricsPayload? = withContext(Dispatchers.IO) {
        val query = listOf("title" to song.title, "artist" to song.artist, "album" to song.album, "duration" to (song.durationMs / 1000).toString())
            .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}" }
        val connection = (URL("${baseUrl.trimEnd('/')}?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 7_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val original = json.optString("syncedLyrics").ifBlank { json.optString("plainLyrics") }.ifBlank { json.optString("original") }
            if (original.isBlank()) null else LyricsPayload(original, json.optString("translation"), json.optString("romanization"), original.contains(Regex("\\[\\d+:\\d+")), id)
        } finally { connection.disconnect() }
    }
}

/** Runtime network kill-switch used by Strict Offline Mode. Provider configuration is retained. */
class LyricsProviderRegistry(private val providers: List<LyricsProvider>) {
    @Volatile private var networkEnabled: Boolean = true
    val available: Boolean get() = networkEnabled && providers.isNotEmpty()
    val configured: Boolean get() = providers.isNotEmpty()
    fun setNetworkEnabled(value: Boolean) { networkEnabled = value }

    suspend fun find(song: SongEntity): LyricsPayload? {
        if (!networkEnabled) return null
        providers.forEach { provider -> runCatching { provider.find(song) }.getOrNull()?.let { return it } }
        return null
    }
}
