package com.neoplayer.app.lyrics

import android.content.Context
import com.neoplayer.app.data.OfflineSpeechModelEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Keeps the Play Store APK reasonably sized without removing offline lyrics.
 *
 * Official English/Persian Vosk packs are downloaded only when first needed, checksum verified,
 * extracted into app-private storage, and then work fully offline. Strict Offline mode can prevent
 * the initial network fetch; users can always use the existing local ZIP importer instead.
 */
class OfflineBuiltInModelStore(private val context: Context) {
    data class PackProgress(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) {
        val fraction: Float
            get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    suspend fun resolve(
        modelId: String,
        allowNetwork: Boolean,
        onProgress: (PackProgress) -> Unit = {}
    ): OfflineSpeechModelEntity = withContext(Dispatchers.IO) {
        val spec = specFor(modelId)
        installed(spec)?.let { return@withContext it }

        if (!allowNetwork) {
            throw IOException(
                "Offline speech model is not installed. Disable Strict Offline once to prepare it, " +
                    "or import a compatible Vosk ZIP from local storage."
            )
        }

        val downloads = File(context.cacheDir, "neo-offline-model-downloads").apply { mkdirs() }
        val archive = File(downloads, spec.archiveName)
        val partial = File(downloads, "${spec.archiveName}.part")

        if (!archive.isFile || sha256(archive) != spec.sha256) {
            archive.delete()
            partial.delete()
            download(spec, partial, onProgress)
            val actual = sha256(partial)
            if (actual != spec.sha256) {
                partial.delete()
                throw IOException("Offline model checksum mismatch: $actual")
            }
            if (!partial.renameTo(archive)) {
                partial.copyTo(archive, overwrite = true)
                partial.delete()
            }
        }

        extractVerified(spec, archive)
        installed(spec) ?: throw IOException("Offline model installation did not produce a compatible Vosk model")
    }

    fun isInstalled(modelId: String): Boolean = runCatching { installed(specFor(modelId)) != null }.getOrDefault(false)

    private fun installed(spec: ModelSpec): OfflineSpeechModelEntity? {
        val destination = destination(spec)
        val marker = File(destination, MARKER_FILE)
        if (!marker.isFile || marker.readText().trim() != spec.sha256) return null
        val root = findModelRoot(destination) ?: return null
        return OfflineSpeechModelEntity(
            id = spec.id,
            displayName = spec.displayName,
            language = spec.language,
            localPath = root.absolutePath,
            sampleRate = 16_000
        )
    }

    private suspend fun download(
        spec: ModelSpec,
        target: File,
        onProgress: (PackProgress) -> Unit
    ) {
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "NEO-Player/${spec.id}")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("Offline model download failed: HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            if (total > MAX_ARCHIVE_BYTES) throw IOException("Offline model archive is unexpectedly large")
            target.parentFile?.mkdirs()
            connection.inputStream.buffered().use { input ->
                FileOutputStream(target).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        downloaded += count
                        if (downloaded > MAX_ARCHIVE_BYTES) throw IOException("Offline model archive exceeds safety limit")
                        output.write(buffer, 0, count)
                        onProgress(PackProgress(downloaded, total))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun extractVerified(spec: ModelSpec, archive: File) {
        val destination = destination(spec)
        val staging = File(destination.parentFile, "${destination.name}.staging")
        staging.deleteRecursively()
        staging.mkdirs()
        var extractedBytes = 0L
        val canonicalRoot = staging.canonicalPath + File.separator

        try {
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    val out = File(staging, entry.name)
                    if (!out.canonicalPath.startsWith(canonicalRoot)) throw IOException("Unsafe path in offline model archive")
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).buffered().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count <= 0) break
                                extractedBytes += count
                                if (extractedBytes > MAX_EXTRACTED_BYTES) throw IOException("Offline model expands beyond safety limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (findModelRoot(staging) == null) throw IOException("Downloaded pack is not a compatible Vosk model")
            File(staging, MARKER_FILE).writeText(spec.sha256)
            destination.deleteRecursively()
            if (!staging.renameTo(destination)) {
                staging.copyRecursively(destination, overwrite = true)
                staging.deleteRecursively()
            }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun destination(spec: ModelSpec): File =
        File(context.filesDir, "offline_speech_models/${spec.id}")

    private fun findModelRoot(base: File): File? = base.walkTopDown()
        .maxDepth(4)
        .filter(File::isDirectory)
        .firstOrNull { dir -> File(dir, "am/final.mdl").isFile && File(dir, "conf").isDirectory }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun specFor(id: String): ModelSpec = when (id) {
        OfflineLyricsTranscriber.BUNDLED_PERSIAN_MODEL_ID -> PERSIAN
        OfflineLyricsTranscriber.BUNDLED_ENGLISH_MODEL_ID -> ENGLISH
        else -> throw IOException("Unknown built-in offline model: $id")
    }

    private data class ModelSpec(
        val id: String,
        val displayName: String,
        val language: String,
        val archiveName: String,
        val url: String,
        val sha256: String
    )

    private companion object {
        const val MARKER_FILE = ".neo-model-sha256"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_ARCHIVE_BYTES = 700L * 1024L * 1024L
        const val MAX_EXTRACTED_BYTES = 1_500L * 1024L * 1024L

        val ENGLISH = ModelSpec(
            id = OfflineLyricsTranscriber.BUNDLED_ENGLISH_MODEL_ID,
            displayName = "Official English offline model",
            language = "en",
            archiveName = "vosk-model-small-en-us-0.15.zip",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
        )
        val PERSIAN = ModelSpec(
            id = OfflineLyricsTranscriber.BUNDLED_PERSIAN_MODEL_ID,
            displayName = "Official Persian offline model",
            language = "fa",
            archiveName = "vosk-model-small-fa-0.42.zip",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip",
            sha256 = "977cb5faa538f3a835ccfd35f5f6d8284b5c450b89c700b9bd4736b66536ad46"
        )
    }
}
