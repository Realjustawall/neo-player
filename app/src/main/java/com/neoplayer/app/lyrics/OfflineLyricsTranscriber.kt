package com.neoplayer.app.lyrics

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.neoplayer.app.data.OfflineLyricsTranscriptEntity
import com.neoplayer.app.data.OfflineSpeechModelEntity
import com.neoplayer.app.data.SongEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService


data class TimedWord(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float
)

data class OfflineTranscriptionProgress(
    val fraction: Float = 0f,
    val decodedMs: Long = 0L,
    val totalMs: Long = 0L,
    val partialText: String = ""
)

/**
 * Fully local lyric generation from an existing audio file. The bundled English Vosk model works
 * without network access; users can additionally import any compatible Vosk model zip (including
 * Persian or other languages) and the model is copied into app-private storage.
 */
class OfflineLyricsTranscriber(private val context: Context) : AutoCloseable {
    private var bundledModelId: String? = null
    private var bundledModel: Model? = null

    suspend fun transcribe(
        song: SongEntity,
        model: OfflineSpeechModelEntity? = null,
        bundledModelId: String = BUNDLED_ENGLISH_MODEL_ID,
        language: String = model?.language ?: if (bundledModelId == BUNDLED_PERSIAN_MODEL_ID) "fa" else "en",
        onProgress: (OfflineTranscriptionProgress) -> Unit = {}
    ): OfflineLyricsTranscriptEntity = withContext(Dispatchers.IO) {
        val ownedModel = if (model == null) bundledModel(bundledModelId) else Model(model.localPath)
        try {
            decode(song, ownedModel, model?.sampleRate, model?.id ?: bundledModelId, language, onProgress)
        } finally {
            if (model != null) runCatching { ownedModel.close() }
        }
    }

    private suspend fun bundledModel(id: String): Model {
        if (bundledModelId == id) bundledModel?.let { return it }
        bundledModel?.let { runCatching { it.close() } }
        bundledModel = null
        bundledModelId = null
        val assetDir = when (id) {
            BUNDLED_PERSIAN_MODEL_ID -> BUNDLED_PERSIAN_ASSET_DIR
            else -> BUNDLED_ENGLISH_ASSET_DIR
        }
        val targetDir = when (id) {
            BUNDLED_PERSIAN_MODEL_ID -> BUNDLED_PERSIAN_TARGET_DIR
            else -> BUNDLED_ENGLISH_TARGET_DIR
        }
        return suspendCancellableCoroutine { continuation ->
            StorageService.unpack(
                context,
                assetDir,
                targetDir,
                { loaded ->
                    if (continuation.isActive) {
                        bundledModelId = id
                        bundledModel = loaded
                        continuation.resume(loaded)
                    } else {
                        runCatching { loaded.close() }
                    }
                },
                { error -> if (continuation.isActive) continuation.resumeWithException(error) }
            )
        }
    }

    private suspend fun decode(
        song: SongEntity,
        model: Model,
        requestedSampleRate: Int?,
        modelId: String,
        language: String,
        onProgress: (OfflineTranscriptionProgress) -> Unit
    ): OfflineLyricsTranscriptEntity {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var recognizer: Recognizer? = null
        try {
            extractor.setDataSource(context, Uri.parse(song.uri), null)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No decodable audio track")
            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw IOException("Missing audio MIME")
            var sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, requestedSampleRate ?: 16_000)
            var channels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            // Recognizer is recreated if the decoder announces a different sample rate before audio.
            recognizer = Recognizer(model, sampleRate.toFloat()).apply {
                setWords(true)
                setPartialWords(true)
            }

            val words = ArrayList<TimedWord>(2048)
            var inputDone = false
            var outputDone = false
            var lastProgressAt = 0L
            val info = MediaCodec.BufferInfo()
            val totalMs = song.durationMs.coerceAtLeast(0L)

            while (!outputDone) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
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

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        val newRate = outputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = outputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        pcmEncoding = outputFormat.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        if (newRate != sampleRate && words.isEmpty()) {
                            sampleRate = newRate
                            runCatching { recognizer?.close() }
                            recognizer = Recognizer(model, sampleRate.toFloat()).apply {
                                setWords(true)
                                setPartialWords(true)
                            }
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val mono = downmixTo16Bit(output.slice(), pcmEncoding, channels)
                            if (mono.isNotEmpty()) {
                                val accepted = recognizer?.acceptWaveForm(mono, mono.size) == true
                                if (accepted) parseResult(recognizer?.result.orEmpty(), words)
                            }
                        }

                        val decodedMs = (info.presentationTimeUs / 1000L).coerceAtLeast(0L)
                        if (decodedMs - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                            lastProgressAt = decodedMs
                            val partial = runCatching {
                                JSONObject(recognizer?.partialResult.orEmpty()).optString("partial")
                            }.getOrDefault("")
                            onProgress(
                                OfflineTranscriptionProgress(
                                    fraction = if (totalMs > 0L) (decodedMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f,
                                    decodedMs = decodedMs,
                                    totalMs = totalMs,
                                    partialText = partial
                                )
                            )
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            parseResult(recognizer.finalResult.orEmpty(), words)
            val normalizedWords = words
                .asSequence()
                .filter { it.word.isNotBlank() }
                .distinctBy { Triple(it.startMs, it.endMs, it.word) }
                .sortedBy { it.startMs }
                .toList()
            val lines = groupLyrics(normalizedWords)
            val plain = lines.joinToString("\n") { it.words.joinToString(" ") { word -> word.word } }
            val lrc = lines.joinToString("\n") { line ->
                "[${formatLrcTime(line.startMs)}]${line.words.joinToString(" ") { it.word }}"
            }
            val json = JSONArray().apply {
                normalizedWords.forEach { word ->
                    put(JSONObject().apply {
                        put("word", word.word)
                        put("startMs", word.startMs)
                        put("endMs", word.endMs)
                        put("confidence", word.confidence.toDouble())
                    })
                }
            }.toString()
            val confidence = normalizedWords.map { it.confidence }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
            onProgress(OfflineTranscriptionProgress(1f, totalMs, totalMs, plain.takeLast(160)))
            return OfflineLyricsTranscriptEntity(
                songId = song.id,
                engine = "vosk-0.3.75",
                modelId = modelId,
                language = language,
                plainText = plain,
                lrcText = lrc,
                wordTimedJson = json,
                averageConfidence = confidence,
                generatedAt = System.currentTimeMillis()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            runCatching { recognizer?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun downmixTo16Bit(buffer: java.nio.ByteBuffer, encoding: Int, channels: Int): ShortArray = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> downmixFloat(buffer, channels)
        AudioFormat.ENCODING_PCM_8BIT -> downmix8(buffer, channels)
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> downmix24(buffer, channels)
        AudioFormat.ENCODING_PCM_32BIT -> downmix32(buffer, channels)
        else -> downmix16(buffer, channels)
    }

    private fun downmix8(buffer: java.nio.ByteBuffer, channels: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = buffer.remaining() / ch
        return ShortArray(frames) {
            var sum = 0L
            repeat(ch) { sum += (((buffer.get().toInt() and 0xFF) - 128) shl 8) }
            (sum / ch).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
    }

    private fun downmix24(buffer: java.nio.ByteBuffer, channels: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = buffer.remaining() / (3 * ch)
        return ShortArray(frames) {
            var sum = 0L
            repeat(ch) {
                val b0 = buffer.get().toInt() and 0xFF
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt()
                val signed24 = b0 or (b1 shl 8) or (b2 shl 16)
                sum += (signed24 shr 8).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            }
            (sum / ch).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
    }

    private fun downmix32(buffer: java.nio.ByteBuffer, channels: Int): ShortArray {
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val input = buffer.asIntBuffer()
        val ch = channels.coerceAtLeast(1)
        val frames = input.remaining() / ch
        return ShortArray(frames) {
            var sum = 0L
            repeat(ch) { sum += (input.get() shr 16) }
            (sum / ch).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
    }

    private fun downmix16(buffer: java.nio.ByteBuffer, channels: Int): ShortArray {
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val input = buffer.asShortBuffer()
        if (!input.hasRemaining()) return ShortArray(0)
        val frames = input.remaining() / channels.coerceAtLeast(1)
        if (frames <= 0) return ShortArray(0)
        val mono = ShortArray(frames)
        for (frame in 0 until frames) {
            var sum = 0L
            repeat(channels) { sum += input.get().toLong() }
            mono[frame] = (sum / channels).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
        return mono
    }

    private fun downmixFloat(buffer: java.nio.ByteBuffer, channels: Int): ShortArray {
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val input = buffer.asFloatBuffer()
        val frames = input.remaining() / channels.coerceAtLeast(1)
        if (frames <= 0) return ShortArray(0)
        val mono = ShortArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            repeat(channels) { sum += input.get() }
            val value = (sum / channels).coerceIn(-1f, 1f)
            mono[frame] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return mono
    }

    private fun parseResult(json: String, destination: MutableList<TimedWord>) {
        if (json.isBlank()) return
        runCatching {
            val result = JSONObject(json).optJSONArray("result") ?: return@runCatching
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                val word = item.optString("word").trim()
                if (word.isBlank()) continue
                destination += TimedWord(
                    word = word,
                    startMs = (item.optDouble("start", 0.0) * 1000.0).toLong().coerceAtLeast(0L),
                    endMs = (item.optDouble("end", 0.0) * 1000.0).toLong().coerceAtLeast(0L),
                    confidence = item.optDouble("conf", 0.0).toFloat().coerceIn(0f, 1f)
                )
            }
        }
    }

    private data class LyricLine(val startMs: Long, val words: List<TimedWord>)

    private fun groupLyrics(words: List<TimedWord>): List<LyricLine> {
        if (words.isEmpty()) return emptyList()
        val lines = ArrayList<LyricLine>()
        var current = ArrayList<TimedWord>()
        var lineStart = words.first().startMs
        var previousEnd = words.first().startMs
        fun flush() {
            if (current.isNotEmpty()) lines += LyricLine(lineStart, current.toList())
            current = ArrayList()
        }
        words.forEach { word ->
            val pause = word.startMs - previousEnd
            val lineDuration = word.endMs - lineStart
            if (current.isNotEmpty() && (pause > LINE_BREAK_SILENCE_MS || current.size >= MAX_WORDS_PER_LINE || lineDuration > MAX_LINE_MS)) {
                flush()
                lineStart = word.startMs
            }
            if (current.isEmpty()) lineStart = word.startMs
            current += word
            previousEnd = word.endMs
        }
        flush()
        return lines
    }

    private fun formatLrcTime(ms: Long): String {
        val totalCs = (ms.coerceAtLeast(0L) / 10L)
        val minutes = totalCs / 6000L
        val seconds = (totalCs / 100L) % 60L
        val centiseconds = totalCs % 100L
        return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    override fun close() {
        bundledModel?.let { runCatching { it.close() } }
        bundledModel = null
        bundledModelId = null
    }

    companion object {
        const val BUNDLED_ENGLISH_MODEL_ID = "bundled-en"
        const val BUNDLED_PERSIAN_MODEL_ID = "bundled-fa"
        /** Kept as an alias so older Track+ code/source references stay source-compatible. */
        const val BUNDLED_MODEL_ID = BUNDLED_ENGLISH_MODEL_ID
        const val BUNDLED_ENGLISH_ASSET_DIR = "model-en-us"
        const val BUNDLED_ENGLISH_TARGET_DIR = "vosk-model-en-us"
        const val BUNDLED_PERSIAN_ASSET_DIR = "model-fa"
        const val BUNDLED_PERSIAN_TARGET_DIR = "vosk-model-fa"
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val LINE_BREAK_SILENCE_MS = 1_100L
        private const val MAX_WORDS_PER_LINE = 8
        private const val MAX_LINE_MS = 6_500L
    }
}

/** Safe local model-pack importer; no download endpoint exists in NEO. */
class OfflineSpeechModelManager(private val context: Context) {
    suspend fun importZip(uri: Uri, displayName: String, language: String): OfflineSpeechModelEntity =
        withContext(Dispatchers.IO) {
            val id = stableId(displayName, language, uri.toString())
            val destination = File(context.filesDir, "offline_speech_models/$id")
            if (destination.exists()) destination.deleteRecursively()
            destination.mkdirs()
            var extractedBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val entry = zip.nextEntry ?: break
                        val target = File(destination, entry.name)
                        val rootPath = destination.canonicalPath + File.separator
                        if (!target.canonicalPath.startsWith(rootPath)) throw IOException("Unsafe model archive path")
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count <= 0) break
                                    extractedBytes += count
                                    if (extractedBytes > MAX_MODEL_BYTES) throw IOException("Model archive is too large")
                                    out.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: throw IOException("Unable to open model archive")

            val root = destination.walkTopDown()
                .filter { it.isDirectory }
                .firstOrNull { dir -> File(dir, "am/final.mdl").isFile && File(dir, "conf").isDirectory }
                ?: run {
                    destination.deleteRecursively()
                    throw IOException("Not a compatible Vosk model archive")
                }
            OfflineSpeechModelEntity(
                id = id,
                displayName = displayName.trim().ifBlank { "Offline model" },
                language = language.trim().ifBlank { "und" },
                localPath = root.absolutePath,
                sampleRate = 16_000
            )
        }

    fun delete(model: OfflineSpeechModelEntity) {
        val base = File(context.filesDir, "offline_speech_models").canonicalFile
        val target = File(model.localPath).canonicalFile
        if (target.path.startsWith(base.path + File.separator)) {
            // Remove the top model-id directory rather than a nested root inside the zip.
            target.toPath().let { path ->
                var current = path
                while (current.parent != null && current.parent != base.toPath()) current = current.parent
                runCatching { current.toFile().deleteRecursively() }
            }
        }
    }

    private fun stableId(name: String, language: String, uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$name|$language|$uri|${System.currentTimeMillis()}".toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private companion object {
        const val MAX_MODEL_BYTES = 1_500L * 1024L * 1024L
    }
}
