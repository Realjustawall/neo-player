package com.neoplayer.app.analysis

import android.content.Context
import android.net.Uri
import com.neoplayer.app.data.ReplayGainEntity
import com.neoplayer.app.data.SongEntity
import java.io.FileInputStream
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort local ReplayGain/R128 metadata reader. It scans both the head and tail of the media
 * object and interprets common ASCII/UTF-8/UTF-16 tag representations. Audio is never uploaded.
 */
class ReplayGainScanner(private val context: Context) {
    suspend fun scan(song: SongEntity): ReplayGainEntity = withContext(Dispatchers.IO) {
        val bytes = readTagWindows(Uri.parse(song.uri))
        if (bytes.isEmpty()) return@withContext ReplayGainEntity(song.id)
        val texts = buildList {
            add(bytes.toString(Charsets.ISO_8859_1))
            add(bytes.toString(Charsets.UTF_8))
            if (bytes.size >= 4) {
                add(runCatching { bytes.toString(Charset.forName("UTF-16LE")) }.getOrDefault(""))
                add(runCatching { bytes.toString(Charset.forName("UTF-16BE")) }.getOrDefault(""))
            }
        }
        val track = firstGain(texts, "REPLAYGAIN_TRACK_GAIN")
        val album = firstGain(texts, "REPLAYGAIN_ALBUM_GAIN")
        val peak = firstNumber(texts, "REPLAYGAIN_TRACK_PEAK")?.coerceAtLeast(0f)
        val r128Raw = firstNumber(texts, "R128_TRACK_GAIN")
        val r128 = r128Raw?.let { raw ->
            // Opus/Vorbis R128 gain is normally Q7.8 fixed-point. Decimal values are treated as dB.
            if (raw == kotlin.math.round(raw) && kotlin.math.abs(raw) > 24f) raw / 256f else raw
        }?.coerceIn(-24f, 24f)
        val source = when {
            r128 != null -> "r128-tag"
            track != null -> "replaygain-track"
            album != null -> "replaygain-album"
            else -> "none"
        }
        ReplayGainEntity(
            songId = song.id,
            trackGainDb = track?.coerceIn(-24f, 24f),
            albumGainDb = album?.coerceIn(-24f, 24f),
            trackPeak = peak,
            r128TrackGainDb = r128,
            source = source
        )
    }

    private fun firstGain(texts: List<String>, key: String): Float? = firstNumber(texts, key)

    private fun firstNumber(texts: List<String>, key: String): Float? {
        val expression = Regex("(?i)" + Regex.escape(key) + "[\\u0000\\s:=]+([+-]?[0-9]+(?:\\.[0-9]+)?)")
        texts.forEach { text ->
            expression.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun readTagWindows(uri: Uri): ByteArray = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            FileInputStream(afd.fileDescriptor).use { input ->
                val channel = input.channel
                val start = afd.startOffset.coerceAtLeast(0L)
                val length = afd.length.takeIf { it > 0L } ?: runCatching { channel.size() - start }.getOrDefault(0L)
                val headSize = minOf(MAX_WINDOW_BYTES.toLong(), length.takeIf { it > 0 } ?: MAX_WINDOW_BYTES.toLong()).toInt()
                val head = ByteArray(headSize)
                channel.position(start)
                val headRead = input.read(head).coerceAtLeast(0)
                val tail = if (length > MAX_WINDOW_BYTES) {
                    val tailSize = minOf(MAX_WINDOW_BYTES.toLong(), length).toInt()
                    val value = ByteArray(tailSize)
                    channel.position(start + length - tailSize)
                    val read = input.read(value).coerceAtLeast(0)
                    value.copyOf(read)
                } else ByteArray(0)
                head.copyOf(headRead) + byteArrayOf(0, 10, 0) + tail
            }
        } ?: ByteArray(0)
    }.getOrDefault(ByteArray(0))

    private companion object {
        const val MAX_WINDOW_BYTES = 2 * 1024 * 1024
    }
}
