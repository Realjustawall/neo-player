package com.neoplayer.app.domain

import android.content.Context
import android.net.Uri
import com.neoplayer.app.data.MusicDao
import com.neoplayer.app.data.PlaylistEntity
import com.neoplayer.app.data.SongEntity
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local M3U/M3U8 import/export. It never modifies or deletes the underlying audio files. */
class PlaylistTransferManager(private val context: Context, private val dao: MusicDao) {
    data class ImportResult(val playlistId: Long, val imported: Int, val unmatched: Int, val totalEntries: Int)

    suspend fun exportM3u8(uri: Uri, playlist: PlaylistEntity, songs: List<SongEntity>) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open export destination")
        OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { out ->
            out.appendLine("#EXTM3U")
            out.appendLine("#PLAYLIST:${sanitize(playlist.title)}")
            songs.forEach { song ->
                out.appendLine("#EXTINF:${(song.durationMs / 1000L).coerceAtLeast(0L)},${sanitize(song.artist)} - ${sanitize(song.title)}")
                // content:// is the most stable representation NEO can round-trip under scoped storage.
                out.appendLine(song.uri)
            }
        }
    }

    suspend fun importM3u8(uri: Uri, requestedTitle: String? = null): ImportResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open playlist file")
        val lines = BufferedReader(input.reader(Charsets.UTF_8)).use { it.readLines() }
        val library = dao.songSnapshot()
        val byUri = library.associateBy { normalizeUri(it.uri) }
        val byName = library.groupBy { nameKey(it.artist, it.title) }
        val byTitle = library.groupBy { normalizeText(it.title) }
        var embeddedTitle: String? = null
        var pendingExtInf: String? = null
        val matched = ArrayList<Long>()
        var total = 0
        var unmatched = 0

        lines.forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isBlank() -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> embeddedTitle = line.substringAfter(':').trim().takeIf(String::isNotBlank)
                line.startsWith("#EXTINF:", ignoreCase = true) -> pendingExtInf = line.substringAfter(',').trim().takeIf(String::isNotBlank)
                line.startsWith('#') -> Unit
                else -> {
                    total++
                    val direct = byUri[normalizeUri(line)]
                    val fromInfo = pendingExtInf?.let(::splitExtInf)?.let { (artist, title) ->
                        byName[nameKey(artist, title)]?.singleOrNull()
                            ?: byName[nameKey(artist, title)]?.firstOrNull()
                            ?: byTitle[normalizeText(title)]?.singleOrNull()
                    }
                    val candidate = direct ?: fromInfo
                    if (candidate == null) unmatched++ else matched += candidate.id
                    pendingExtInf = null
                }
            }
        }
        val title = requestedTitle?.trim().takeIf { !it.isNullOrBlank() }
            ?: embeddedTitle
            ?: "Imported playlist"
        val playlistId = dao.createPlaylist(PlaylistEntity(title = title))
        dao.addPlaylistSongs(playlistId, matched.distinct())
        ImportResult(playlistId, matched.distinct().size, unmatched, total)
    }

    private fun splitExtInf(value: String): Pair<String, String> {
        val separator = value.indexOf(" - ")
        return if (separator >= 0) value.substring(0, separator).trim() to value.substring(separator + 3).trim()
        else "" to value.trim()
    }

    private fun nameKey(artist: String, title: String) = "${normalizeText(artist)}\u001f${normalizeText(title)}"
    private fun normalizeText(value: String) = value.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    private fun normalizeUri(value: String) = value.trim().lowercase(Locale.ROOT)
    private fun sanitize(value: String) = value.replace('\n', ' ').replace('\r', ' ').trim()
}
