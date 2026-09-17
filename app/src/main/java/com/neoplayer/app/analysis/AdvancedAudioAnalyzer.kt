package com.neoplayer.app.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.neoplayer.app.data.AdvancedAudioAnalysisEntity
import com.neoplayer.app.data.SongEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray

/**
 * Deep local analysis for professional offline AutoMix and mood filters.
 *
 * This intentionally does not pretend to be a studio/DJ cloud analyzer. It derives a beat grid,
 * phrase grid, musical-key estimate, spectral descriptors and mood from decoded PCM on-device,
 * caches the result in Room, and never uploads audio or fingerprints.
 */
class AdvancedAudioAnalyzer(private val context: Context) {
    suspend fun analyze(
        song: SongEntity,
        onProgress: (Float) -> Unit = {}
    ): AdvancedAudioAnalysisEntity = withContext(Dispatchers.Default) {
        try {
            decode(song, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            AdvancedAudioAnalysisEntity(songId = song.id)
        }
    }

    private suspend fun decode(song: SongEntity, onProgress: (Float) -> Unit): AdvancedAudioAnalysisEntity {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(song.uri), null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return AdvancedAudioAnalysisEntity(song.id)
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return AdvancedAudioAnalysisEntity(song.id)
            var sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100).coerceAtLeast(8_000)
            var channels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val durationUs = (song.durationMs.takeIf { it > 0L }?.times(1_000L)
                ?: inputFormat.longOr(MediaFormat.KEY_DURATION, MAX_ANALYSIS_US)).coerceAtLeast(1L)
            val analysisUs = min(durationUs, MAX_ANALYSIS_US)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val energy = ArrayList<Double>(4096)
            val onset = ArrayList<Double>(4096)
            val chroma = DoubleArray(12)
            var spectralCentroidWeighted = 0.0
            var spectralWeight = 0.0
            var totalSquares = 0.0
            var totalSamples = 0L
            var windowSquares = 0.0
            var windowCount = 0
            var previousEnergy = 0.0
            var targetEnergySamples = max(1, sampleRate * ENERGY_WINDOW_MS / 1000)
            val fftWindow = DoubleArray(FFT_SIZE)
            var fftCount = 0
            var spectrumFrame = 0
            var processedSpectrumFrames = 0
            var decodedBuffers = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        val timeUs = extractor.sampleTime
                        if (size < 0 || timeUs < 0 || timeUs > analysisUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate).coerceAtLeast(8_000)
                        channels = out.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        pcmEncoding = out.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        targetEnergySamples = max(1, sampleRate * ENERGY_WINDOW_MS / 1000)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> yield()
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val mono = decodeMono(output.slice().order(ByteOrder.LITTLE_ENDIAN), pcmEncoding, channels)
                            for (sample in mono) {
                                val x = sample.toDouble().coerceIn(-1.0, 1.0)
                                val sq = x * x
                                totalSquares += sq
                                totalSamples++
                                windowSquares += sq
                                windowCount++

                                if (spectrumFrame % SPECTRUM_DECIMATION == 0 && processedSpectrumFrames < MAX_SPECTRUM_FRAMES) {
                                    fftWindow[fftCount++] = x
                                    if (fftCount == FFT_SIZE) {
                                        val spectrum = spectrumFeatures(fftWindow, sampleRate)
                                        for (i in chroma.indices) chroma[i] += spectrum.chroma[i]
                                        spectralCentroidWeighted += spectrum.centroidHz * spectrum.weight
                                        spectralWeight += spectrum.weight
                                        processedSpectrumFrames++
                                        fftCount = 0
                                        spectrumFrame++
                                    }
                                } else if (fftCount == 0) {
                                    spectrumFrame++
                                }

                                if (windowCount >= targetEnergySamples) {
                                    val e = windowSquares / windowCount.toDouble()
                                    energy += e
                                    onset += max(0.0, e - previousEnergy)
                                    previousEnergy = e
                                    windowSquares = 0.0
                                    windowCount = 0
                                }
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        decodedBuffers++
                        if (decodedBuffers % 8 == 0) {
                            val progress = (info.presentationTimeUs.toDouble() / analysisUs.toDouble()).toFloat().coerceIn(0f, 1f)
                            onProgress(progress)
                            yield()
                        }
                    }
                }
            }
            if (windowCount > 0) {
                val e = windowSquares / windowCount.toDouble()
                energy += e
                onset += max(0.0, e - previousEnergy)
            }

            val bpm = estimateBpm(onset.ifEmpty { energy })
            val beatIntervalMs = if (bpm > 0f) 60_000f / bpm else 0f
            val beatPhase = estimateBeatPhase(onset.ifEmpty { energy }, beatIntervalMs)
            val beatOffsetMs = beatPhase.first
            val beatConfidence = beatPhase.second
            val phraseLength = choosePhraseLength(beatConfidence, bpm)
            val phraseOffset = beatOffsetMs
            val phraseConfidence = (beatConfidence * .72f).coerceIn(0f, 1f)
            val key = estimateKey(chroma)
            val rms = if (totalSamples > 0L) sqrt(totalSquares / totalSamples.toDouble()) else 0.0
            val rmsDb = if (rms > 0) 20.0 * log10(rms) else -80.0
            val normalizedEnergy = ((rmsDb + 45.0) / 35.0).toFloat().coerceIn(0f, 1f)
            val centroid = if (spectralWeight > 0.0) (spectralCentroidWeighted / spectralWeight).toFloat() else 0f
            val dynamicRange = dynamicRangeDb(energy)
            val brightness = (centroid / 4_500f).coerceIn(0f, 1f)
            val majorBias = if (key.isMajor) .70f else .35f
            val valence = (majorBias * .65f + brightness * .20f + normalizedEnergy * .15f).coerceIn(0f, 1f)
            val tempoFit = if (bpm > 0f) (1f - abs(bpm - 122f) / 90f).coerceIn(0f, 1f) else 0f
            val danceability = (beatConfidence * .60f + tempoFit * .25f + normalizedEnergy * .15f).coerceIn(0f, 1f)
            val mood = classifyMood(normalizedEnergy, valence, danceability, bpm, key.isMajor)
            val beatGrid = buildBeatGrid(beatOffsetMs, beatIntervalMs, song.durationMs)
            onProgress(1f)

            return AdvancedAudioAnalysisEntity(
                songId = song.id,
                bpm = bpm,
                beatIntervalMs = beatIntervalMs,
                beatOffsetMs = beatOffsetMs,
                beatConfidence = beatConfidence,
                phraseLengthBeats = phraseLength,
                phraseOffsetMs = phraseOffset,
                phraseConfidence = phraseConfidence,
                musicalKey = key.name,
                camelotKey = key.camelot,
                keyConfidence = key.confidence,
                energy = normalizedEnergy,
                valence = valence,
                danceability = danceability,
                spectralCentroidHz = centroid,
                dynamicRangeDb = dynamicRange,
                mood = mood,
                beatGridJson = beatGrid,
                analyzedAt = System.currentTimeMillis()
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun decodeMono(buffer: ByteBuffer, encoding: Int, channels: Int): FloatArray {
        val safeChannels = channels.coerceAtLeast(1)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val values = buffer.asFloatBuffer()
                val frames = values.remaining() / safeChannels
                FloatArray(frames) {
                    var sum = 0f
                    repeat(safeChannels) { sum += values.get().coerceIn(-1f, 1f) }
                    sum / safeChannels
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val frames = buffer.remaining() / safeChannels
                FloatArray(frames) {
                    var sum = 0f
                    repeat(safeChannels) { sum += ((buffer.get().toInt() and 0xFF) - 128) / 128f }
                    sum / safeChannels
                }
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val frames = buffer.remaining() / (3 * safeChannels)
                FloatArray(frames) {
                    var sum = 0f
                    repeat(safeChannels) {
                        val b0 = buffer.get().toInt() and 0xFF
                        val b1 = buffer.get().toInt() and 0xFF
                        val b2 = buffer.get().toInt()
                        val raw = b0 or (b1 shl 8) or (b2 shl 16)
                        sum += raw / 8_388_608f
                    }
                    sum / safeChannels
                }
            }
            AudioFormat.ENCODING_PCM_32BIT -> {
                val ints = buffer.asIntBuffer()
                val frames = ints.remaining() / safeChannels
                FloatArray(frames) {
                    var sum = 0.0
                    repeat(safeChannels) { sum += ints.get().toDouble() / Int.MAX_VALUE.toDouble() }
                    (sum / safeChannels).toFloat()
                }
            }
            else -> {
                val shorts = buffer.asShortBuffer()
                val frames = shorts.remaining() / safeChannels
                FloatArray(frames) {
                    var sum = 0f
                    repeat(safeChannels) { sum += shorts.get().toFloat() / 32768f }
                    sum / safeChannels
                }
            }
        }
    }

    private data class SpectrumFeatures(val chroma: DoubleArray, val centroidHz: Double, val weight: Double)

    private fun spectrumFeatures(samples: DoubleArray, sampleRate: Int): SpectrumFeatures {
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (i in samples.indices) {
            val hann = .5 - .5 * cos(2.0 * PI * i / (FFT_SIZE - 1).toDouble())
            real[i] = samples[i] * hann
        }
        fft(real, imag)
        val chroma = DoubleArray(12)
        var weightedFreq = 0.0
        var weight = 0.0
        val binHz = sampleRate.toDouble() / FFT_SIZE.toDouble()
        for (i in 1 until FFT_SIZE / 2) {
            val freq = i * binHz
            if (freq < 45.0 || freq > 6_000.0) continue
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            if (mag <= 1e-9) continue
            weightedFreq += freq * mag
            weight += mag
            if (freq in 55.0..4_500.0) {
                val midi = (69.0 + 12.0 * log2(freq / 440.0))
                val pitchClass = ((kotlin.math.round(midi).toInt() % 12) + 12) % 12
                chroma[pitchClass] += sqrt(mag)
            }
        }
        return SpectrumFeatures(chroma, if (weight > 0) weightedFreq / weight else 0.0, weight)
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= real.size) {
            val angle = -2.0 * PI / len.toDouble()
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < real.size) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wr - imag[i + k + len / 2] * wi
                    val vI = real[i + k + len / 2] * wi + imag[i + k + len / 2] * wr
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun estimateBpm(signal: List<Double>): Float {
        if (signal.size < 80) return 0f
        val mean = signal.average()
        val centered = DoubleArray(signal.size) { signal[it] - mean }
        val windowsPerSecond = 1000.0 / ENERGY_WINDOW_MS
        val minLag = (windowsPerSecond * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (windowsPerSecond * 60.0 / MIN_BPM).toInt().coerceAtMost(centered.size / 2)
        var bestLag = 0
        var best = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var c = 0.0
            for (i in lag until centered.size) c += centered[i] * centered[i - lag]
            if (c > best) { best = c; bestLag = lag }
        }
        return if (bestLag > 0 && best > 0) (60.0 * windowsPerSecond / bestLag).toFloat().coerceIn(MIN_BPM.toFloat(), MAX_BPM.toFloat()) else 0f
    }

    private fun estimateBeatPhase(onset: List<Double>, beatIntervalMs: Float): Pair<Float, Float> {
        if (onset.isEmpty() || beatIntervalMs <= 0f) return 0f to 0f
        val beatWindows = (beatIntervalMs / ENERGY_WINDOW_MS).toInt().coerceAtLeast(1)
        var bestPhase = 0
        var best = Double.NEGATIVE_INFINITY
        var total = 0.0
        for (v in onset) total += max(0.0, v)
        for (phase in 0 until beatWindows) {
            var score = 0.0
            var i = phase
            while (i < onset.size) { score += max(0.0, onset[i]); i += beatWindows }
            if (score > best) { best = score; bestPhase = phase }
        }
        val expectedBeats = max(1.0, onset.size.toDouble() / beatWindows.toDouble())
        val meanOnset = total / max(1, onset.size)
        val normalized = if (meanOnset > 1e-12) (best / expectedBeats / (meanOnset * 3.0)).toFloat() else 0f
        return bestPhase * ENERGY_WINDOW_MS.toFloat() to normalized.coerceIn(0f, 1f)
    }

    private fun choosePhraseLength(confidence: Float, bpm: Float): Int = when {
        confidence < .25f -> 8
        bpm in 70f..100f -> 8
        else -> 16
    }

    private data class KeyEstimate(val name: String, val camelot: String, val confidence: Float, val isMajor: Boolean)

    private fun estimateKey(chroma: DoubleArray): KeyEstimate {
        val total = chroma.sum()
        if (total <= 1e-9) return KeyEstimate("", "", 0f, true)
        val normalized = DoubleArray(12) { chroma[it] / total }
        var bestScore = Double.NEGATIVE_INFINITY
        var second = Double.NEGATIVE_INFINITY
        var bestRoot = 0
        var bestMajor = true
        for (root in 0 until 12) {
            for (major in listOf(true, false)) {
                val profile = if (major) MAJOR_PROFILE else MINOR_PROFILE
                var score = 0.0
                for (pc in 0 until 12) score += normalized[(pc + root) % 12] * profile[pc]
                if (score > bestScore) {
                    second = bestScore; bestScore = score; bestRoot = root; bestMajor = major
                } else if (score > second) second = score
            }
        }
        val confidence = if (bestScore > 0) ((bestScore - second) / bestScore * 4.0).toFloat().coerceIn(0f, 1f) else 0f
        val name = NOTE_NAMES[bestRoot] + if (bestMajor) " major" else " minor"
        val camelot = if (bestMajor) CAMELOT_MAJOR[bestRoot] else CAMELOT_MINOR[bestRoot]
        return KeyEstimate(name, camelot, confidence, bestMajor)
    }

    private fun dynamicRangeDb(energy: List<Double>): Float {
        val db = energy.asSequence().filter { it > 1e-12 }.map { 10.0 * log10(it) }.sorted().toList()
        if (db.size < 8) return 0f
        val lo = db[(db.lastIndex * .10).toInt()]
        val hi = db[(db.lastIndex * .90).toInt()]
        return (hi - lo).toFloat().coerceIn(0f, 40f)
    }

    private fun classifyMood(energy: Float, valence: Float, danceability: Float, bpm: Float, major: Boolean): String = when {
        energy > .70f && danceability > .60f -> "workout"
        valence > .68f && energy > .45f -> "happy"
        valence < .32f && energy < .55f -> "sad"
        !major && energy > .55f && valence < .48f -> "dark"
        energy < .32f && bpm in 1f..105f -> "chill"
        danceability > .62f -> "dance"
        energy < .48f -> "focus"
        else -> "balanced"
    }

    private fun buildBeatGrid(offsetMs: Float, intervalMs: Float, durationMs: Long): String {
        if (intervalMs <= 0f || durationMs <= 0L) return "[]"
        val values = JSONArray()
        var time = offsetMs.coerceAtLeast(0f)
        var count = 0
        while (time <= durationMs && count < MAX_GRID_BEATS) {
            values.put(time.toLong())
            time += intervalMs
            count++
        }
        return values.toString()
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int = if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback
    private fun MediaFormat.longOr(key: String, fallback: Long): Long = if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(fallback) else fallback
    private fun log10(value: Double): Double = ln(value) / ln(10.0)

    private companion object {
        const val ENERGY_WINDOW_MS = 50
        const val FFT_SIZE = 2048
        const val SPECTRUM_DECIMATION = 6
        const val MAX_SPECTRUM_FRAMES = 520
        const val MIN_BPM = 60
        const val MAX_BPM = 200
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_ANALYSIS_US = 180_000_000L
        const val MAX_GRID_BEATS = 4096
        val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
        val CAMELOT_MAJOR = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
        val CAMELOT_MINOR = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")
    }
}
