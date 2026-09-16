package com.neoplayer.app.lyrics

import android.content.Context
import android.provider.MediaStore
import com.neoplayer.app.data.SongEntity
import java.io.File

/** Loads local .lrc/.txt files next to a MediaStore track when Android exposes a filesystem path. */
class SidecarLyricsLoader(private val context: Context) {
    fun load(song: SongEntity): Pair<String, Boolean>? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME)
        val path = runCatching {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                MediaStore.Audio.Media._ID + "=?", arrayOf(song.id.toString()), null)?.use { c ->
                if (!c.moveToFirst()) null else c.getColumnIndex(MediaStore.MediaColumns.DATA).takeIf { it >= 0 }?.let(c::getString)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val filePath = path ?: return null
        val mediaFile = File(filePath)
        val parent = mediaFile.parentFile ?: return null
        val base = File(parent, mediaFile.nameWithoutExtension)
        val candidates = listOf(File(base.path + ".lrc"), File(base.path + ".LRC"), File(base.path + ".txt"), File(base.path + ".TXT"))
        val file = candidates.firstOrNull { it.isFile && it.length() > 0L } ?: return null
        return runCatching { file.readText(Charsets.UTF_8).trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { it to file.extension.equals("lrc", true) }
    }
}
