package com.neoplayer.app.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.neoplayer.app.data.AudioAnalysisEntity
import com.neoplayer.app.data.SongEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** Local loudness/BPM analysis with PCM-8/16/24/32/float decoding and cooperative cancellation. */
class LocalAudioAnalyzer(private val context: Context) {
    suspend fun analyze(song: SongEntity, targetLufs: Float = DEFAULT_TARGET_LUFS): AudioAnalysisEntity = withContext(Dispatchers.IO) {
        try { decode(song, targetLufs) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { AudioAnalysisEntity(songId = song.id, integratedLufs = targetLufs, peakDb = 0f, bpm = 0f, gainMb = 0) }
    }

    private suspend fun decode(song: SongEntity, targetLufs: Float): AudioAnalysisEntity {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(song.uri), null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index -> extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return AudioAnalysisEntity(song.id)
            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return AudioAnalysisEntity(song.id)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var totalSquares = 0.0
            var totalSamples = 0L
            var peak = 0.0
            var inputDone = false
            var outputDone = false
            var decodedBuffers = 0
            val info = MediaCodec.BufferInfo()
            val energy = ArrayList<Double>(4096)
            var windowSquares = 0.0
            var windowSamples = 0
            var targetWindowSamples = max(1, sampleRate * ENERGY_WINDOW_MS / 1000)
            val analysisLimitUs = minOf(song.durationMs.takeIf { it > 0L }?.times(1000L) ?: MAX_ANALYSIS_US, MAX_ANALYSIS_US)

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input == null) -1 else extractor.readSampleData(input, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time < 0 || time > analysisLimitUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else { codec.queueInputBuffer(inputIndex, 0, size, time, 0); extractor.advance() }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = out.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        encoding = out.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        targetWindowSamples = max(1, sampleRate * ENERGY_WINDOW_MS / 1000)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> yield()
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset); output.limit(info.offset + info.size)
                            val samples = decodeMono(output.slice().order(ByteOrder.LITTLE_ENDIAN), encoding, channels)
                            for (value in samples) {
                                val x = value.toDouble().coerceIn(-1.0, 1.0)
                                val square = x * x
                                totalSquares += square; totalSamples++; peak = max(peak, abs(x)); windowSquares += square; windowSamples++
                                if (windowSamples >= targetWindowSamples) { energy += windowSquares / windowSamples; windowSquares = 0.0; windowSamples = 0 }
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (++decodedBuffers % YIELD_EVERY_BUFFERS == 0) yield()
                    }
                }
            }
            if (windowSamples > 0) energy += windowSquares / windowSamples
            val rms = if (totalSamples > 0) sqrt(totalSquares / totalSamples) else 0.0
            val integrated = if (rms > 0) (20.0 * log10(rms) - RMS_TO_LUFS_OFFSET).toFloat() else targetLufs
            val peakDb = if (peak > 0) (20.0 * log10(peak)).toFloat() else -90f
            val gain = (targetLufs - integrated).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            return AudioAnalysisEntity(song.id, integrated.coerceIn(-70f, 0f), peakDb.coerceIn(-90f, 0f), estimateBpm(energy), (gain * 100).toInt())
        } finally { runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { extractor.release() } }
    }

    private fun decodeMono(buffer: ByteBuffer, encoding: Int, channels: Int): FloatArray {
        val ch = channels.coerceAtLeast(1)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.asFloatBuffer().let { src -> FloatArray(src.remaining()/ch) { var s=0f; repeat(ch){s += src.get().coerceIn(-1f,1f)}; s/ch } }
            AudioFormat.ENCODING_PCM_8BIT -> FloatArray(buffer.remaining()/ch) { var s=0f; repeat(ch){s += ((buffer.get().toInt() and 255)-128)/128f};s/ch }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> FloatArray(buffer.remaining()/(3*ch)) { var s=0f; repeat(ch){val b0=buffer.get().toInt() and 255;val b1=buffer.get().toInt() and 255;val b2=buffer.get().toInt();s += (b0 or (b1 shl 8) or (b2 shl 16))/8_388_608f};s/ch }
            AudioFormat.ENCODING_PCM_32BIT -> buffer.asIntBuffer().let { src -> FloatArray(src.remaining()/ch){var s=0.0;repeat(ch){s += src.get().toDouble()/Int.MAX_VALUE};(s/ch).toFloat()} }
            else -> buffer.asShortBuffer().let { src -> FloatArray(src.remaining()/ch){var s=0f;repeat(ch){s += src.get()/32768f};s/ch} }
        }
    }

    private fun estimateBpm(values: List<Double>): Float {
        if (values.size < MIN_ENERGY_WINDOWS) return 0f
        val mean = values.average(); val centered = DoubleArray(values.size) { values[it]-mean }; val windowsPerSecond = 1000.0/ENERGY_WINDOW_MS
        val minLag=(windowsPerSecond*60/MAX_BPM).toInt().coerceAtLeast(1); val maxLag=(windowsPerSecond*60/MIN_BPM).toInt().coerceAtMost(centered.size/2)
        var bestLag=0;var best=Double.NEGATIVE_INFINITY
        for(lag in minLag..maxLag){var c=0.0;for(i in lag until centered.size)c+=centered[i]*centered[i-lag];if(c>best){best=c;bestLag=lag}}
        return if(bestLag>0&&best>0)(60*windowsPerSecond/bestLag).toFloat().coerceIn(MIN_BPM.toFloat(),MAX_BPM.toFloat()) else 0f
    }

    private fun MediaFormat.intOr(key:String,fallback:Int)=if(containsKey(key))runCatching{getInteger(key)}.getOrDefault(fallback)else fallback
    private fun log10(value:Double)=ln(value)/ln(10.0)
    private companion object { const val DEFAULT_TARGET_LUFS=-14f;const val RMS_TO_LUFS_OFFSET=.691;const val MIN_GAIN_DB=-12f;const val MAX_GAIN_DB=12f;const val ENERGY_WINDOW_MS=50;const val MIN_ENERGY_WINDOWS=80;const val MIN_BPM=60;const val MAX_BPM=200;const val CODEC_TIMEOUT_US=10_000L;const val MAX_ANALYSIS_US=120_000_000L;const val YIELD_EVERY_BUFFERS=8 }
}
