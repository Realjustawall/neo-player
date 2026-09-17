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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** Playback-aligned FFT generated from the source file, so no microphone/output-capture permission is needed. */
data class SpectrumData(
    val songId: Long,
    val durationMs: Long,
    val bandCount: Int,
    val frames: List<FloatArray>
) {
    fun frameAt(positionMs: Long): FloatArray {
        if (frames.isEmpty()) return FloatArray(bandCount)
        val index = if (durationMs <= 0L) 0 else ((positionMs.coerceIn(0L, durationMs).toDouble() / durationMs) * (frames.size - 1)).toInt()
        return frames[index.coerceIn(frames.indices)]
    }
}

class LocalSpectrumAnalyzer(private val context: Context) {
    suspend fun loadCached(song: SongEntity): SpectrumData? = withContext(Dispatchers.IO) { read(cacheFile(song)) }

    suspend fun analyzeAndCache(song: SongEntity): SpectrumData = withContext(Dispatchers.Default) {
        val file = cacheFile(song)
        read(file)?.takeIf { it.songId == song.id } ?: analyze(song).also { runCatching { write(file, it) } }
    }

    private suspend fun analyze(song: SongEntity): SpectrumData {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(song.uri), null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return SpectrumData(song.id, song.durationMs, BAND_COUNT, emptyList())
            extractor.selectTrack(track)
            val input = extractor.getTrackFormat(track)
            val mime = input.getString(MediaFormat.KEY_MIME) ?: return SpectrumData(song.id, song.durationMs, BAND_COUNT, emptyList())
            var sampleRate = input.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100).coerceAtLeast(8_000)
            var channels = input.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val durationMs = song.durationMs.takeIf { it > 0L }
                ?: (input.longOr(MediaFormat.KEY_DURATION, 1_000_000L) / 1000L).coerceAtLeast(1L)
            val durationUs = min(durationMs * 1000L, MAX_DURATION_US)
            val accum = Array(TIME_FRAMES) { FloatArray(BAND_COUNT) }
            val hits = IntArray(TIME_FRAMES)
            val fftWindow = DoubleArray(FFT_SIZE)
            var fftCount = 0
            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(input, null, null, 0)
            codec.start()

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time < 0 || time > durationUs) {
                            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate).coerceAtLeast(8_000)
                        channels = out.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        encoding = out.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> yield()
                    else -> if (outIndex >= 0) {
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset); out.limit(info.offset + info.size)
                            val mono = decodeMono(out.slice().order(ByteOrder.LITTLE_ENDIAN), encoding, channels)
                            var sampleOffset = 0
                            while (sampleOffset < mono.size) {
                                fftWindow[fftCount++] = mono[sampleOffset].toDouble()
                                sampleOffset++
                                if (fftCount == FFT_SIZE) {
                                    val centerUs = info.presentationTimeUs + (sampleOffset.toLong() * 1_000_000L / sampleRate)
                                    val timeIndex = ((centerUs.toDouble() / durationUs.coerceAtLeast(1L)) * TIME_FRAMES)
                                        .toInt().coerceIn(0, TIME_FRAMES - 1)
                                    val values = spectrum(fftWindow, sampleRate)
                                    for (b in values.indices) accum[timeIndex][b] = max(accum[timeIndex][b], values[b])
                                    hits[timeIndex]++
                                    fftCount = 0
                                }
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
            fillSparse(accum, hits)
            val globalMax = accum.maxOfOrNull { frame -> frame.maxOrNull() ?: 0f }?.coerceAtLeast(.0001f) ?: 1f
            accum.forEach { frame -> for (i in frame.indices) frame[i] = (frame[i] / globalMax).coerceIn(0f, 1f) }
            return SpectrumData(song.id, durationMs, BAND_COUNT, accum.toList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { extractor.release() }
        }
    }

    private fun spectrum(samples: DoubleArray, sampleRate: Int): FloatArray {
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (i in samples.indices) real[i] = samples[i] * (.5 - .5 * cos(2.0 * PI * i / (FFT_SIZE - 1)))
        fft(real, imag)
        val bands = FloatArray(BAND_COUNT)
        val minHz = 55.0
        val maxHz = min(16_000.0, sampleRate / 2.0)
        val logMin = ln(minHz)
        val logSpan = (ln(maxHz) - logMin).coerceAtLeast(.1)
        for (i in 1 until FFT_SIZE / 2) {
            val hz = i * sampleRate.toDouble() / FFT_SIZE
            if (hz !in minHz..maxHz) continue
            val band = (((ln(hz) - logMin) / logSpan) * BAND_COUNT).toInt().coerceIn(0, BAND_COUNT - 1)
            val magnitude = sqrt(real[i] * real[i] + imag[i] * imag[i]).toFloat()
            bands[band] = max(bands[band], sqrt(magnitude.coerceAtLeast(0f)))
        }
        return bands
    }

    private fun fillSparse(values: Array<FloatArray>, hits: IntArray) {
        var last = -1
        for (i in values.indices) {
            if (hits[i] > 0) {
                if (last >= 0 && i - last > 1) {
                    for (j in last + 1 until i) {
                        val t = (j - last).toFloat() / (i - last)
                        for (b in 0 until BAND_COUNT) values[j][b] = values[last][b] + (values[i][b] - values[last][b]) * t
                    }
                }
                last = i
            }
        }
        if (last >= 0) for (i in last + 1 until values.size) values[last].copyInto(values[i])
    }

    private fun decodeMono(buffer: ByteBuffer, encoding: Int, channels: Int): FloatArray {
        val ch = channels.coerceAtLeast(1)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.asFloatBuffer().let { src -> FloatArray(src.remaining() / ch) { var s = 0f; repeat(ch) { s += src.get() }; s / ch } }
            AudioFormat.ENCODING_PCM_8BIT -> FloatArray(buffer.remaining() / ch) { var s = 0f; repeat(ch) { s += ((buffer.get().toInt() and 255) - 128) / 128f }; s / ch }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> FloatArray(buffer.remaining() / (3 * ch)) { var s = 0f; repeat(ch) { val b0=buffer.get().toInt() and 255; val b1=buffer.get().toInt() and 255; val b2=buffer.get().toInt(); s += (b0 or (b1 shl 8) or (b2 shl 16)) / 8_388_608f }; s / ch }
            AudioFormat.ENCODING_PCM_32BIT -> buffer.asIntBuffer().let { src -> FloatArray(src.remaining() / ch) { var s=0.0; repeat(ch){s += src.get().toDouble()/Int.MAX_VALUE}; (s/ch).toFloat() } }
            else -> buffer.asShortBuffer().let { src -> FloatArray(src.remaining() / ch) { var s=0f; repeat(ch){s += src.get()/32768f}; s/ch } }
        }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        var j=0
        for(i in 1 until real.size){var bit=real.size shr 1; while(j and bit != 0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val tr=real[i];real[i]=real[j];real[j]=tr}}
        var len=2
        while(len<=real.size){val angle=-2*PI/len;val lr=cos(angle);val li=sin(angle);var i=0;while(i<real.size){var wr=1.0;var wi=0.0;for(k in 0 until len/2){val ur=real[i+k];val ui=imag[i+k];val vr=real[i+k+len/2]*wr-imag[i+k+len/2]*wi;val vi=real[i+k+len/2]*wi+imag[i+k+len/2]*wr;real[i+k]=ur+vr;imag[i+k]=ui+vi;real[i+k+len/2]=ur-vr;imag[i+k+len/2]=ui-vi;val nwr=wr*lr-wi*li;wi=wr*li+wi*lr;wr=nwr};i+=len};len=len shl 1}
    }

    private fun cacheFile(song: SongEntity) = File(File(context.cacheDir, "neo_spectrum").apply { mkdirs() }, "${song.id}_${song.dateModified}.sp")
    private fun write(file: File, value: SpectrumData) = DataOutputStream(file.outputStream().buffered()).use { out -> out.writeInt(CACHE_VERSION); out.writeLong(value.songId); out.writeLong(value.durationMs); out.writeInt(value.bandCount); out.writeInt(value.frames.size); value.frames.forEach { f -> f.forEach(out::writeFloat) } }
    private fun read(file: File): SpectrumData? = runCatching { if(!file.isFile)return@runCatching null; DataInputStream(file.inputStream().buffered()).use { input -> if(input.readInt()!=CACHE_VERSION)return@use null;val id=input.readLong();val d=input.readLong();val bands=input.readInt().coerceIn(8,128);val frames=input.readInt().coerceIn(1,1024);SpectrumData(id,d,bands,List(frames){FloatArray(bands){input.readFloat().coerceIn(0f,1f)}}) } }.getOrNull()
    private fun MediaFormat.intOr(key:String,fallback:Int)=if(containsKey(key))runCatching{getInteger(key)}.getOrDefault(fallback) else fallback
    private fun MediaFormat.longOr(key:String,fallback:Long)=if(containsKey(key))runCatching{getLong(key)}.getOrDefault(fallback) else fallback

    private companion object { const val CACHE_VERSION=1; const val FFT_SIZE=1024; const val BAND_COUNT=32; const val TIME_FRAMES=256; const val TIMEOUT_US=10_000L; const val MAX_DURATION_US=1_800_000_000L }
}
