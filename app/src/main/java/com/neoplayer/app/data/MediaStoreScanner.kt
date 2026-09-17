package com.neoplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val context: Context) {
    suspend fun scan(minDurationMs: Long = 10_000L): List<SongEntity> = withContext(Dispatchers.IO) {
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 30) {
            projection += MediaStore.Audio.Media.ALBUM_ARTIST
            projection += MediaStore.Audio.Media.GENRE
            projection += MediaStore.Audio.Media.BITRATE
        }

        val resolver = context.contentResolver
        val cursor = resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
            arrayOf(minDurationMs.coerceAtLeast(0L).toString()),
            null
        ) ?: return@withContext emptyList()

        cursor.use { c ->
            // Resolve indexes once. getColumnIndex() inside the row loop is surprisingly expensive
            // on large libraries and was previously repeated for almost every field of every song.
            val idIndex = c.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleIndex = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIndex = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumIndex = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistIndex = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
            val genreIndex = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            val yearIndex = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val durationIndex = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackIndex = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val composerIndex = c.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
            val bitrateIndex = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1
            val mimeIndex = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeIndex = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val relativePathIndex = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            val dateAddedIndex = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedIndex = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

            fun text(index: Int): String = if (index >= 0 && !c.isNull(index)) c.getString(index).orEmpty() else ""
            fun long(index: Int): Long = if (index >= 0 && !c.isNull(index)) c.getLong(index) else 0L

            val result = ArrayList<SongEntity>(c.count.coerceAtLeast(16))
            while (c.moveToNext()) {
                val id = long(idIndex)
                if (id <= 0L) continue
                val rawTrack = long(trackIndex).coerceAtLeast(0L).toInt()
                result += SongEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    title = text(titleIndex).ifBlank { "Unknown title" },
                    artist = text(artistIndex).ifBlank { "Unknown artist" },
                    album = text(albumIndex).ifBlank { "Unknown album" },
                    albumId = long(albumIdIndex),
                    albumArtist = text(albumArtistIndex),
                    genre = text(genreIndex),
                    year = long(yearIndex).toInt(),
                    durationMs = long(durationIndex).coerceAtLeast(0L),
                    trackNumber = rawTrack % 1000,
                    discNumber = rawTrack / 1000,
                    composer = text(composerIndex),
                    bitrate = long(bitrateIndex).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    mimeType = text(mimeIndex),
                    sizeBytes = long(sizeIndex).coerceAtLeast(0L),
                    relativePath = text(relativePathIndex).trimEnd('/').ifBlank { "Storage" },
                    dateAdded = long(dateAddedIndex),
                    dateModified = long(dateModifiedIndex)
                )
            }
            result
        }
    }
}
