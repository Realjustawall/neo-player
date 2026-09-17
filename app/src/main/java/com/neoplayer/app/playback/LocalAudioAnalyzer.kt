package com.neoplayer.app.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.neoplayer.app.data.AudioAnalysisEntity
import com.neoplayer.app.data.SongEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Local-only audio analysis. Nothing leaves the device.
 *
 * Android's decoder is used to inspect PCM without maintaining a second codec stack. Loudness is
 * an RMS-derived integrated estimate suitable for consistent local playback gain; tempo is an
 * envelope autocorrelation estimate used only for gentle transition matching. Analysis is cached
 * in Room, so a track normally pays this cost once.
 */
class LocalAudioAnalyzer(private val context: Context) {
    suspend fun analyze(song: SongEntity, targetLufs: Float = DEFAULT_TARGET_LUFS): AudioAnalysisEntity =
        withContext(Dispatchers.IO) {
            try {
                decode(song, targetLufs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                AudioAnalysisEntity(songId = song.id, integratedLufs = targetLufs, peakDb = 0f, bpm = 0f, gainMb = 0)
            }
        }

    private suspend fun decode(song: SongEntity, targetLufs: Float): AudioAnalysisEntity {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(song.uri), null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return AudioAnalysisEntity(song.id)

            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return AudioAnalysisEntity(song.id)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = inputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var totalSquares = 0.0
            var totalSamples = 0L
            var peak = 0.0
            var inputDone = false
            var outputDone = false
            var decodedBuffers = 0
            val info = MediaCodec.BufferInfo()

            val energyWindows = ArrayList<Double>(4096)
            var windowSquares = 0.0
            var windowSamples = 0
            var targetWindowSamples = max(1, sampleRate * channels * ENERGY_WINDOW_MS / 1000)
            val analysisLimitUs = minOf(
                song.durationMs.takeIf { it > 0L }?.times(1000L) ?: MAX_ANALYSIS_US,
                MAX_ANALYSIS_US
            )

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        val sampleTime = extractor.sampleTime
                        if (size < 0 || sampleTime < 0 || sampleTime > analysisLimitUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = outputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        targetWindowSamples = max(1, sampleRate * channels * ENERGY_WINDOW_MS / 1000)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> yield()
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val samples = output.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (samples.hasRemaining()) {
                                val normalized = samples.get().toDouble() / Short.MAX_VALUE.toDouble()
                                val square = normalized * normalized
                                totalSquares += square
                                totalSamples++
                                peak = max(peak, abs(normalized))
                                windowSquares += square
                                windowSamples++
                                if (windowSamples >= targetWindowSamples) {
                                    energyWindows += windowSquares / windowSamples.toDouble()
                                    windowSquares = 0.0
                                    windowSamples = 0
                                }
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        decodedBuffers++
                        // Decoding is CPU-heavy even off-main. Cooperative yields keep audio/UI work responsive.
                        if (decodedBuffers % YIELD_EVERY_BUFFERS == 0) yield()
                    }
                }
            }

            if (windowSamples > 0) energyWindows += windowSquares / windowSamples.toDouble()
            val rms = if (totalSamples > 0L) sqrt(totalSquares / totalSamples.toDouble()) else 0.0
            val integratedLufs = if (rms > 0.0) (20.0 * log10(rms) - RMS_TO_LUFS_OFFSET).toFloat() else targetLufs
            val peakDb = if (peak > 0.0) (20.0 * log10(peak)).toFloat() else -90f
            val gainDb = (targetLufs - integratedLufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            val bpm = estimateBpm(energyWindows)

            return AudioAnalysisEntity(
                songId = song.id,
                integratedLufs = integratedLufs.coerceIn(-70f, 0f),
                peakDb = peakDb.coerceIn(-90f, 0f),
                bpm = bpm,
                gainMb = (gainDb * 100f).toInt(),
                analyzedAt = System.currentTimeMillis()
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun estimateBpm(energy: List<Double>): Float {
        if (energy.size < MIN_ENERGY_WINDOWS) return 0f
        val mean = energy.average()
        val centered = DoubleArray(energy.size) { index -> energy[index] - mean }
        val windowsPerSecond = 1000.0 / ENERGY_WINDOW_MS
        val minLag = (windowsPerSecond * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (windowsPerSecond * 60.0 / MIN_BPM).toInt().coerceAtMost(centered.size / 2)
        if (maxLag <= minLag) return 0f

        var bestLag = 0
        var bestCorrelation = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var i = lag
            while (i < centered.size) {
                correlation += centered[i] * centered[i - lag]
                i++
            }
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestCorrelation <= 0.0) return 0f
        return (60.0 * windowsPerSecond / bestLag.toDouble()).toFloat().coerceIn(MIN_BPM.toFloat(), MAX_BPM.toFloat())
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    private fun log10(value: Double): Double = ln(value) / ln(10.0)

    private companion object {
        const val DEFAULT_TARGET_LUFS = -14f
        const val RMS_TO_LUFS_OFFSET = 0.691
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        const val ENERGY_WINDOW_MS = 50
        const val MIN_ENERGY_WINDOWS = 80
        const val MIN_BPM = 60
        const val MAX_BPM = 200
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_ANALYSIS_US = 120_000_000L
        const val YIELD_EVERY_BUFFERS = 8
    }
}
