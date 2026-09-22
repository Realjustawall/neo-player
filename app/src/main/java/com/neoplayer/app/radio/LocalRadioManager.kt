package com.neoplayer.app.radio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.neoplayer.app.data.MusicRepository
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.playback.PlaybackConnection
import fi.iki.elonen.NanoHTTPD
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class LocalRadioMode { IDLE, HOSTING, LISTENING }

data class LocalRadioListener(
    val id: String,
    val name: String,
    val lastSeenAt: Long
)

data class LocalRadioState(
    val mode: LocalRadioMode = LocalRadioMode.IDLE,
    val joinCode: String = "",
    val trackTitle: String = "",
    val trackArtist: String = "",
    val listeners: List<LocalRadioListener> = emptyList(),
    val bluetoothAdvertising: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null
)

/**
 * Hosts the active local track over the current LAN/hotspot and keeps guest playback aligned.
 * Bluetooth LE only advertises that a nearby NEO session exists; audio uses the local network so
 * multiple listeners receive sufficient bandwidth. Every HTTP request is protected by a random
 * session token contained in the QR code, and no server is opened until the user explicitly hosts.
 */
class LocalRadioManager(
    private val context: Context,
    private val repository: MusicRepository,
    private val playback: PlaybackConnection
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val random = SecureRandom()
    private val listeners = ConcurrentHashMap<String, LocalRadioListener>()
    private val blockedListeners = ConcurrentHashMap.newKeySet<String>()
    private val _state = MutableStateFlow(LocalRadioState())
    val state = _state.asStateFlow()

    private var library: Map<Long, SongEntity> = emptyMap()
    private var server: RadioServer? = null
    private var hostToken = ""
    private var hostPort = DEFAULT_PORT
    private var clientJob: Job? = null
    private var clientBaseUrl = ""
    private var clientToken = ""
    private val clientId = UUID.randomUUID().toString()
    private val guestPlayer = ExoPlayer.Builder(context).build()
    private var guestTrackId: Long? = null
    private var advertiseCallback: AdvertiseCallback? = null

    init {
        scope.launch {
            repository.songs.collect { songs -> library = songs.associateBy { it.id } }
        }
        scope.launch {
            while (true) {
                if (_state.value.mode == LocalRadioMode.HOSTING) refreshHostState()
                delay(1_000L)
            }
        }
    }

    fun startHosting(): Result<String> = runCatching {
        stopClient()
        server?.stop()
        hostToken = ByteArray(18).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        blockedListeners.clear()
        listeners.clear()
        val selectedPort = (DEFAULT_PORT..DEFAULT_PORT + 20).firstOrNull { port ->
            runCatching {
                RadioServer(port).also { it.start(SOCKET_TIMEOUT_MS, false); server = it }
            }.isSuccess
        } ?: error("No free local radio port")
        hostPort = selectedPort
        val address = localIpv4Address() ?: error("Connect to Wi-Fi or enable a hotspot first")
        val code = Uri.Builder()
            .scheme("neoplayer")
            .authority("local-radio")
            .appendPath("join")
            .appendQueryParameter("host", address)
            .appendQueryParameter("port", selectedPort.toString())
            .appendQueryParameter("token", hostToken)
            .build().toString()
        val bluetooth = startBluetoothAdvertisement(hostToken)
        _state.value = LocalRadioState(
            mode = LocalRadioMode.HOSTING,
            joinCode = code,
            bluetoothAdvertising = bluetooth
        )
        refreshHostState()
        code
    }.onFailure { error ->
        server?.stop()
        server = null
        stopBluetoothAdvertisement()
        _state.value = LocalRadioState(error = error.message)
    }

    fun join(code: String, listenerName: String = Build.MODEL.ifBlank { "NEO listener" }): Result<Unit> = runCatching {
        val uri = Uri.parse(code.trim())
        require(uri.scheme == "neoplayer" && uri.host == "local-radio" && uri.path == "/join") { "Invalid NEO Local Radio QR" }
        val host = requireNotNull(uri.getQueryParameter("host"))
        val port = requireNotNull(uri.getQueryParameter("port")).toInt()
        val token = requireNotNull(uri.getQueryParameter("token"))
        stopHosting()
        clientBaseUrl = "http://$host:$port/radio"
        clientToken = token
        _state.value = LocalRadioState(mode = LocalRadioMode.LISTENING, joinCode = code)
        clientJob = scope.launch(Dispatchers.IO) { pollHost(listenerName) }
    }.onFailure { error ->
        _state.value = LocalRadioState(error = error.message)
    }

    fun disconnectListener(id: String) {
        blockedListeners += id
        listeners.remove(id)
        refreshHostState()
    }

    fun disconnectAllListeners() {
        blockedListeners += listeners.keys
        listeners.clear()
        refreshHostState()
    }

    fun stop() {
        stopClient()
        stopHosting()
        _state.value = LocalRadioState()
    }

    fun release() {
        stop()
        guestPlayer.release()
        scope.cancel()
    }

    private fun stopHosting() {
        server?.stop()
        server = null
        listeners.clear()
        blockedListeners.clear()
        stopBluetoothAdvertisement()
        if (_state.value.mode == LocalRadioMode.HOSTING) _state.value = LocalRadioState()
    }

    private fun stopClient() {
        clientJob?.cancel()
        clientJob = null
        guestPlayer.stop()
        guestPlayer.clearMediaItems()
        guestTrackId = null
        if (_state.value.mode == LocalRadioMode.LISTENING) _state.value = LocalRadioState()
    }

    private fun refreshHostState() {
        if (_state.value.mode != LocalRadioMode.HOSTING) return
        val now = System.currentTimeMillis()
        listeners.entries.removeIf { now - it.value.lastSeenAt > LISTENER_TIMEOUT_MS }
        val current = playback.state.value.current
        _state.value = _state.value.copy(
            trackTitle = current?.mediaMetadata?.title?.toString().orEmpty(),
            trackArtist = current?.mediaMetadata?.artist?.toString().orEmpty(),
            listeners = listeners.values.sortedBy { it.name },
            connected = true,
            error = null
        )
    }

    private suspend fun pollHost(listenerName: String) {
        while (true) {
            try {
                val statusUrl = Uri.parse("$clientBaseUrl/status").buildUpon()
                    .appendQueryParameter("token", clientToken)
                    .appendQueryParameter("listenerId", clientId)
                    .appendQueryParameter("name", listenerName)
                    .build().toString()
                val json = JSONObject(httpGet(statusUrl))
                val songId = json.optLong("songId", -1L)
                val playing = json.optBoolean("playing", false)
                val serverPosition = json.optLong("positionMs", 0L)
                val measuredAt = json.optLong("measuredAt", System.currentTimeMillis())
                val targetPosition = serverPosition + if (playing) (System.currentTimeMillis() - measuredAt).coerceAtLeast(0L) else 0L
                if (songId > 0 && guestTrackId != songId) {
                    val audioUrl = Uri.parse("$clientBaseUrl/audio").buildUpon()
                        .appendQueryParameter("token", clientToken)
                        .appendQueryParameter("songId", songId.toString())
                        .build().toString()
                    withContext(Dispatchers.Main) {
                        guestTrackId = songId
                        guestPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
                        guestPlayer.prepare()
                        guestPlayer.seekTo(targetPosition)
                        guestPlayer.playWhenReady = playing
                    }
                } else if (songId > 0) {
                    withContext(Dispatchers.Main) {
                        val drift = kotlin.math.abs(guestPlayer.currentPosition - targetPosition)
                        if (drift > MAX_SYNC_DRIFT_MS) guestPlayer.seekTo(targetPosition)
                        if (playing && !guestPlayer.isPlaying) guestPlayer.play()
                        if (!playing && guestPlayer.playbackState != Player.STATE_IDLE) guestPlayer.pause()
                    }
                }
                _state.value = _state.value.copy(
                    trackTitle = json.optString("title"),
                    trackArtist = json.optString("artist"),
                    connected = true,
                    error = null
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(connected = false, error = error.message)
                if (error.message?.contains("403") == true) break
            }
            delay(CLIENT_POLL_MS)
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        connection.requestMethod = "GET"
        val status = connection.responseCode
        if (status !in 200..299) error("Local Radio returned $status")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private inner class RadioServer(port: Int) : NanoHTTPD("0.0.0.0", port) {
        override fun serve(session: IHTTPSession): Response {
            val token = session.parameters["token"]?.firstOrNull()
            if (token != hostToken) return textResponse(Response.Status.FORBIDDEN, "Invalid session")
            return when (session.uri) {
                "/radio/status" -> serveStatus(session)
                "/radio/audio" -> serveAudio(session)
                else -> textResponse(Response.Status.NOT_FOUND, "NEO Local Radio")
            }
        }

        private fun serveStatus(session: IHTTPSession): Response {
            val listenerId = session.parameters["listenerId"]?.firstOrNull().orEmpty()
            if (listenerId.isBlank() || listenerId in blockedListeners) return textResponse(Response.Status.FORBIDDEN, "Listener disconnected")
            val name = session.parameters["name"]?.firstOrNull()?.take(60).orEmpty().ifBlank { "NEO listener" }
            listeners[listenerId] = LocalRadioListener(listenerId, name, System.currentTimeMillis())
            val playbackState = playback.state.value
            val current = playbackState.current
            val id = current?.mediaId?.toLongOrNull() ?: -1L
            val body = JSONObject()
                .put("songId", id)
                .put("title", current?.mediaMetadata?.title?.toString().orEmpty())
                .put("artist", current?.mediaMetadata?.artist?.toString().orEmpty())
                .put("positionMs", playbackState.positionMs)
                .put("durationMs", playbackState.durationMs)
                .put("playing", playbackState.playing)
                .put("measuredAt", System.currentTimeMillis())
                .toString()
            refreshHostState()
            return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
        }

        private fun serveAudio(session: IHTTPSession): Response {
            val requestedId = session.parameters["songId"]?.firstOrNull()?.toLongOrNull()
            val currentId = playback.state.value.current?.mediaId?.toLongOrNull()
            if (requestedId == null || requestedId != currentId) return textResponse(Response.Status.NOT_FOUND, "Track changed")
            val song = library[requestedId] ?: return textResponse(Response.Status.NOT_FOUND, "Track unavailable")
            val descriptor = context.contentResolver.openAssetFileDescriptor(Uri.parse(song.uri), "r")
                ?: return textResponse(Response.Status.NOT_FOUND, "Audio unavailable")
            val totalLength = descriptor.length
            if (totalLength <= 0L) {
                val stream = object : FilterInputStream(descriptor.createInputStream()) {
                    override fun close() { super.close(); descriptor.close() }
                }
                return newChunkedResponse(Response.Status.OK, song.mimeType.ifBlank { "audio/*" }, stream).apply {
                    addHeader("Accept-Ranges", "bytes")
                }
            }
            val range = session.headers["range"]?.removePrefix("bytes=")?.substringBefore(',')
            val start = range?.substringBefore('-')?.toLongOrNull()?.coerceIn(0L, totalLength - 1) ?: 0L
            val requestedEnd = range?.substringAfter('-', "")?.toLongOrNull()
            val end = (requestedEnd ?: totalLength - 1).coerceIn(start, totalLength - 1)
            val length = end - start + 1
            val input = object : FilterInputStream(descriptor.createInputStream()) {
                override fun close() { super.close(); descriptor.close() }
            }
            var remaining = start
            while (remaining > 0L) {
                val skipped = input.skip(remaining)
                if (skipped <= 0L) break
                remaining -= skipped
            }
            val status = if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT
            return newFixedLengthResponse(status, song.mimeType.ifBlank { "audio/*" }, input, length).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Length", length.toString())
                if (range != null) addHeader("Content-Range", "bytes $start-$end/$totalLength")
                addHeader("Cache-Control", "no-store")
            }
        }

        private fun textResponse(status: Response.Status, text: String) =
            newFixedLengthResponse(status, "text/plain; charset=utf-8", text)
    }

    private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList().map { address -> it.name to address } }
        .filter { (_, address) -> address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress }
        .sortedBy { (name, _) -> if (name.startsWith("wlan") || name.startsWith("ap")) 0 else 1 }
        .firstOrNull()?.second?.hostAddress

    @SuppressLint("MissingPermission")
    private fun startBluetoothAdvertisement(token: String): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return false
        if (!adapter.isEnabled || !adapter.isMultipleAdvertisementSupported) return false
        val advertiser = adapter.bluetoothLeAdvertiser ?: return false
        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                _state.value = _state.value.copy(bluetoothAdvertising = false)
            }
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val marker = token.take(12).encodeToByteArray()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(RADIO_SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, marker)
            .setIncludeDeviceName(false)
            .build()
        return runCatching {
            advertiser.startAdvertising(settings, data, callback)
            advertiseCallback = callback
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun stopBluetoothAdvertisement() {
        val callback = advertiseCallback ?: return
        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) }
        }
        advertiseCallback = null
    }

    private companion object {
        const val DEFAULT_PORT = 8765
        const val SOCKET_TIMEOUT_MS = 5_000
        const val LISTENER_TIMEOUT_MS = 8_000L
        const val CLIENT_POLL_MS = 900L
        const val MAX_SYNC_DRIFT_MS = 1_200L
        const val MANUFACTURER_ID = 0x4E45
        val RADIO_SERVICE_UUID: UUID = UUID.fromString("6e656f70-6c61-7965-7200-000000000001")
    }
}
