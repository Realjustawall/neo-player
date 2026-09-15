package com.neoplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(private val context: Context) {
    suspend fun scan(minDurationMs: Long = 10_000): List<SongEntity> = withContext(Dispatchers.IO) {
        val base = mutableListOf(
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
        if (Build.VERSION.SDK_INT >= 29) base += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 30) {
            base += MediaStore.Audio.Media.ALBUM_ARTIST
            base += MediaStore.Audio.Media.GENRE
            base += MediaStore.Audio.Media.BITRATE
        }
        val result = ArrayList<SongEntity>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            base.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
            arrayOf(minDurationMs.toString()),
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            fun text(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getString).orEmpty()
            fun long(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
            while (cursor.moveToNext()) {
                val id = long(MediaStore.Audio.Media._ID)
                val rawTrack = long(MediaStore.Audio.Media.TRACK).toInt()
                result += SongEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    title = text(MediaStore.Audio.Media.TITLE).ifBlank { "Unknown title" },
                    artist = text(MediaStore.Audio.Media.ARTIST).ifBlank { "Unknown artist" },
                    album = text(MediaStore.Audio.Media.ALBUM).ifBlank { "Unknown album" },
                    albumId = long(MediaStore.Audio.Media.ALBUM_ID),
                    albumArtist = if (Build.VERSION.SDK_INT >= 30) text(MediaStore.Audio.Media.ALBUM_ARTIST) else "",
                    genre = if (Build.VERSION.SDK_INT >= 30) text(MediaStore.Audio.Media.GENRE) else "",
                    year = long(MediaStore.Audio.Media.YEAR).toInt(),
                    durationMs = long(MediaStore.Audio.Media.DURATION),
                    trackNumber = rawTrack % 1000,
                    discNumber = rawTrack / 1000,
                    composer = text(MediaStore.Audio.Media.COMPOSER),
                    bitrate = if (Build.VERSION.SDK_INT >= 30) long(MediaStore.Audio.Media.BITRATE).toInt() else 0,
                    mimeType = text(MediaStore.Audio.Media.MIME_TYPE),
                    sizeBytes = long(MediaStore.Audio.Media.SIZE),
                    relativePath = if (Build.VERSION.SDK_INT >= 29) text(MediaStore.Audio.Media.RELATIVE_PATH).trimEnd('/') else "Storage",
                    dateAdded = long(MediaStore.Audio.Media.DATE_ADDED),
                    dateModified = long(MediaStore.Audio.Media.DATE_MODIFIED)
                )
            }
        }
        result
    }
}
