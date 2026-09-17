package com.neoplayer.app.lyrics

import android.content.Context
import android.provider.MediaStore
import com.neoplayer.app.data.SongEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads local .lrc/.txt files next to a MediaStore track when Android exposes a filesystem path. */
class SidecarLyricsLoader(private val context: Context) {
    suspend fun load(song: SongEntity): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME)
        val path = runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media._ID + "=?",
                arrayOf(song.id.toString()),
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    .takeIf { it >= 0 }
                    ?.let(cursor::getString)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val mediaFile = path?.let(::File) ?: return@withContext null
        val parent = mediaFile.parentFile ?: return@withContext null
        val base = File(parent, mediaFile.nameWithoutExtension)
        val candidates = listOf(
            File(base.path + ".lrc"),
            File(base.path + ".LRC"),
            File(base.path + ".txt"),
            File(base.path + ".TXT")
        )
        val file = candidates.firstOrNull { it.isFile && it.length() > 0L } ?: return@withContext null
        runCatching { file.readText(Charsets.UTF_8).trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { it to file.extension.equals("lrc", ignoreCase = true) }
    }
}
