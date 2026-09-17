package com.neoplayer.app.visual

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.neoplayer.app.data.SongEntity
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext


data class WaveformData(
    val songId: Long,
    val durationMs: Long,
    val amplitudes: FloatArray
)

/**
 * Decode-once local waveform generator. Results live in cacheDir and are therefore safe to remove
 * through the existing cache manager. No microphone permission or audio-capture API is used.
 */
class WaveformAnalyzer(private val context: Context) {
    suspend fun loadCached(song: SongEntity, bins: Int = DEFAULT_BINS): WaveformData? = withContext(Dispatchers.IO) {
        val safeBins = bins.coerceIn(MIN_BINS, MAX_BINS)
        read(cacheFile(song, safeBins))?.takeIf { it.songId == song.id && it.amplitudes.size == safeBins }
    }

    suspend fun analyzeAndCache(song: SongEntity, bins: Int = DEFAULT_BINS): WaveformData = withContext(Dispatchers.IO) {
        val safeBins = bins.coerceIn(MIN_BINS, MAX_BINS)
        val file = cacheFile(song, safeBins)
        read(file)?.takeIf { it.songId == song.id && it.amplitudes.size == safeBins } ?: analyze(song, safeBins).also {
            runCatching { write(file, it) }
        }
    }

    private suspend fun analyze(song: SongEntity, bins: Int): WaveformData {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(song.uri), null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return WaveformData(song.id, song.durationMs, FloatArray(bins))
            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return WaveformData(song.id, song.durationMs, FloatArray(bins))
            var channels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            var sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val peaks = FloatArray(bins)
            val durationUs = (song.durationMs.takeIf { it > 0L }?.times(1000L)
                ?: inputFormat.longOr(MediaFormat.KEY_DURATION, 1_000_000L)).coerceAtLeast(1L)
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            while (!outputDone) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        val sampleTime = extractor.sampleTime
                        if (size < 0 || sampleTime < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        channels = outputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        sampleRate = outputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate).coerceAtLeast(1)
                        pcmEncoding = outputFormat.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val peak = when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> peakFloat(output.slice(), channels)
                                else -> peak16(output.slice(), channels)
                            }
                            val centerUs = (info.presentationTimeUs + max(0L, estimateBufferDurationUs(info.size, channels, pcmEncoding, sampleRate))).coerceAtLeast(0L)
                            val index = ((centerUs.toDouble() / durationUs.toDouble()) * bins)
                                .toInt().coerceIn(0, bins - 1)
                            peaks[index] = max(peaks[index], peak)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            // Fill sparse bins by linear interpolation so short codec buffers do not leave holes.
            var lastKnown = -1
            for (i in peaks.indices) {
                if (peaks[i] > 0f) {
                    if (lastKnown >= 0 && i - lastKnown > 1) {
                        val a = peaks[lastKnown]
                        val b = peaks[i]
                        for (j in lastKnown + 1 until i) {
                            val t = (j - lastKnown).toFloat() / (i - lastKnown).toFloat()
                            peaks[j] = a + (b - a) * t
                        }
                    }
                    lastKnown = i
                }
            }
            if (lastKnown >= 0) {
                for (i in lastKnown + 1 until peaks.size) peaks[i] = peaks[lastKnown]
            }
            val maxPeak = peaks.maxOrNull()?.coerceAtLeast(0.0001f) ?: 1f
            for (i in peaks.indices) peaks[i] = (peaks[i] / maxPeak).coerceIn(0f, 1f)
            return WaveformData(song.id, song.durationMs, peaks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun peak16(buffer: java.nio.ByteBuffer, channels: Int): Float {
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val samples = buffer.asShortBuffer()
        var peak = 0f
        while (samples.remaining() >= channels) {
            var sum = 0f
            repeat(channels) { sum += abs(samples.get().toFloat() / Short.MAX_VALUE.toFloat()) }
            peak = max(peak, sum / channels)
        }
        return peak.coerceIn(0f, 1f)
    }

    private fun peakFloat(buffer: java.nio.ByteBuffer, channels: Int): Float {
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val samples = buffer.asFloatBuffer()
        var peak = 0f
        while (samples.remaining() >= channels) {
            var sum = 0f
            repeat(channels) { sum += abs(samples.get()) }
            peak = max(peak, sum / channels)
        }
        return peak.coerceIn(0f, 1f)
    }

    private fun estimateBufferDurationUs(size: Int, channels: Int, pcmEncoding: Int, sampleRate: Int): Long {
        val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        val frames = size / (channels.coerceAtLeast(1) * bytesPerSample)
        return frames.toLong() * 1_000_000L / sampleRate
    }

    private fun cacheFile(song: SongEntity, bins: Int): File {
        val folder = File(context.cacheDir, "neo_waveforms").apply { mkdirs() }
        return File(folder, "${song.id}_${song.dateModified}_$bins.wf")
    }

    private fun read(file: File): WaveformData? = runCatching {
        if (!file.isFile) return@runCatching null
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != CACHE_VERSION) return@use null
            val songId = input.readLong()
            val duration = input.readLong()
            val count = input.readInt().coerceIn(MIN_BINS, MAX_BINS)
            val values = FloatArray(count) { input.readFloat().coerceIn(0f, 1f) }
            WaveformData(songId, duration, values)
        }
    }.getOrNull()

    private fun write(file: File, value: WaveformData) {
        DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeInt(CACHE_VERSION)
            output.writeLong(value.songId)
            output.writeLong(value.durationMs)
            output.writeInt(value.amplitudes.size)
            value.amplitudes.forEach(output::writeFloat)
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    private fun MediaFormat.longOr(key: String, fallback: Long): Long =
        if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(fallback) else fallback

    private companion object {
        const val CACHE_VERSION = 1
        const val DEFAULT_BINS = 512
        const val MIN_BINS = 64
        const val MAX_BINS = 2048
        const val TIMEOUT_US = 10_000L
    }
}
