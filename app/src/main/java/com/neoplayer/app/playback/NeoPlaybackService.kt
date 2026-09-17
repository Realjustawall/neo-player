package com.neoplayer.app.playback

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.neoplayer.app.NeoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

@OptIn(markerClass = [UnstableApi::class])
class NeoPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var transitionPlayer: ExoPlayer
    private lateinit var session: MediaSession
    private val store by lazy { getSharedPreferences("player_state", MODE_PRIVATE) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistJob: Job? = null
    private var transitionJob: Job? = null
    private var transitionMonitorJob: Job? = null
    private var tempoRestoreJob: Job? = null
    private var crossfadeMs = 0L
    private var automixEnabled = false
    private var gaplessEnabled = true
    private var transitionGeneration = 0
    private var transitionActive = false

    private data class PersistedItem(
        val id: String,
        val uri: String,
        val title: String,
        val artist: String,
        val album: String,
        val art: String
    )

    private data class QueueSnapshot(
        val items: List<PersistedItem>,
        val index: Int,
        val position: Long
    )

    private data class TransitionSettings(val crossfadeMs: Long, val automix: Boolean, val gapless: Boolean)

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // The secondary decoder never requests audio focus. It exists only during an overlap and
        // is released from the active media item immediately after handoff.
        transitionPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(false)
            .build()
            .apply { volume = 0f }

        restoreQueue()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!transitionActive) cancelTransition(restorePrimaryVolume = false)
                schedulePersistQueue()
                PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                schedulePersistQueue()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                schedulePersistQueue()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.audioSessionId > 0) {
                    (application as NeoApplication).audioEffects.attach(player.audioSessionId)
                }
                if (playbackState == Player.STATE_ENDED) schedulePersistQueue()
                PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelTransition(restorePrimaryVolume = true)
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    player.pause()
                }
                schedulePersistQueue()
            }
        })
        session = MediaSession.Builder(this, player).build()

        observeTransitionSettings()
        startTransitionMonitor()
    }

    private fun observeTransitionSettings() {
        val app = application as NeoApplication
        serviceScope.launch {
            app.settings.values
                .map { TransitionSettings(it.crossfadeMs, it.automixEnabled, it.gaplessEnabled) }
                .distinctUntilChanged()
                .collect { value ->
                    crossfadeMs = value.crossfadeMs.coerceIn(0L, 12_000L)
                    automixEnabled = value.automix
                    gaplessEnabled = value.gapless
                    if (crossfadeMs == 0L) cancelTransition(restorePrimaryVolume = true)
                    // Media3 already performs metadata-aware gapless playback. pauseAtEndOfMediaItems
                    // lets users explicitly opt out without changing the queue or any existing item.
                    runCatching { player.pauseAtEndOfMediaItems = !gaplessEnabled && crossfadeMs == 0L }
                }
        }
    }

    private fun startTransitionMonitor() {
        transitionMonitorJob?.cancel()
        transitionMonitorJob = serviceScope.launch {
            while (isActive) {
                delay(TRANSITION_POLL_MS)
                if (transitionActive || crossfadeMs <= 0L || !player.isPlaying || !player.hasNextMediaItem()) continue
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: continue
                val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
                if (remaining in 1L..(crossfadeMs + TRANSITION_EARLY_MARGIN_MS)) {
                    startOverlappingTransition(remaining)
                }
            }
        }
    }

    private fun startOverlappingTransition(remainingAtDetection: Long) {
        if (transitionActive || crossfadeMs <= 0L || !player.hasNextMediaItem()) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex !in 0 until player.mediaItemCount) return
        val currentItem = player.currentMediaItem ?: return
        val nextItem = player.getMediaItemAt(nextIndex)
        if (nextItem.localConfiguration?.uri == null) return

        transitionActive = true
        val generation = ++transitionGeneration
        transitionJob?.cancel()
        transitionJob = serviceScope.launch {
            val primaryVolume = player.volume.coerceIn(0f, 1f)
            val originalSpeed = player.playbackParameters.speed
            val (currentBpm, nextBpm) = if (automixEnabled) loadTempoPair(currentItem, nextItem) else 0f to 0f
            val tempoRatio = if (currentBpm > 0f && nextBpm > 0f) {
                (currentBpm / nextBpm).coerceIn(MIN_AUTOMIX_RATIO, MAX_AUTOMIX_RATIO)
            } else 1f

            val beatAlignedFade = if (automixEnabled && currentBpm > 0f) {
                val beatMs = (60_000f / currentBpm).toLong().coerceAtLeast(250L)
                (crossfadeMs / beatMs * beatMs).coerceAtLeast(min(crossfadeMs, beatMs))
            } else crossfadeMs
            val fadeDuration = min(remainingAtDetection.coerceAtLeast(250L), beatAlignedFade.coerceAtLeast(250L))

            transitionPlayer.stop()
            transitionPlayer.clearMediaItems()
            transitionPlayer.setMediaItem(nextItem)
            transitionPlayer.setPlaybackSpeed((originalSpeed * tempoRatio).coerceIn(0.25f, 3f))
            transitionPlayer.volume = 0f
            transitionPlayer.prepare()
            transitionPlayer.playWhenReady = true

            // Give the second decoder a short head start to reach READY. If a device is slow, the
            // overlap simply begins a little later rather than blocking the primary player.
            var warmup = 0
            while (transitionPlayer.playbackState == Player.STATE_BUFFERING && warmup < MAX_WARMUP_STEPS) {
                if (generation != transitionGeneration) return@launch
                delay(WARMUP_STEP_MS)
                warmup++
            }

            val steps = (fadeDuration / FADE_STEP_MS).toInt().coerceIn(MIN_FADE_STEPS, MAX_FADE_STEPS)
            val stepDelay = (fadeDuration / steps).coerceAtLeast(25L)
            repeat(steps) { step ->
                if (generation != transitionGeneration || player.currentMediaItem?.mediaId != currentItem.mediaId) {
                    cancelTransition(restorePrimaryVolume = true)
                    return@launch
                }
                val progress = (step + 1) / steps.toFloat()
                player.volume = primaryVolume * (1f - progress)
                transitionPlayer.volume = primaryVolume * progress
                delay(stepDelay)
            }

            if (generation != transitionGeneration) return@launch
            val handoffPosition = transitionPlayer.currentPosition.coerceAtLeast(0L)
            player.seekTo(nextIndex, handoffPosition)
            if (tempoRatio != 1f) {
                player.setPlaybackSpeed((originalSpeed * tempoRatio).coerceIn(0.25f, 3f))
                scheduleTempoRestore(originalSpeed, generation)
            } else {
                player.setPlaybackSpeed(originalSpeed)
            }
            player.volume = primaryVolume
            if (!player.isPlaying) player.play()
            transitionPlayer.pause()
            transitionPlayer.stop()
            transitionPlayer.clearMediaItems()
            transitionPlayer.volume = 0f
            transitionActive = false
            schedulePersistQueue()
            PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
        }
    }

    private suspend fun loadTempoPair(current: MediaItem, next: MediaItem): Pair<Float, Float> = withContext(Dispatchers.IO) {
        val repository = (application as NeoApplication).repository
        val currentId = current.mediaId.toLongOrNull()
        val nextId = next.mediaId.toLongOrNull()
        val currentBpm = currentId?.let { repository.audioAnalysis(it)?.bpm } ?: 0f
        val nextBpm = nextId?.let { repository.audioAnalysis(it)?.bpm } ?: 0f
        currentBpm to nextBpm
    }

    private fun scheduleTempoRestore(originalSpeed: Float, generation: Int) {
        tempoRestoreJob?.cancel()
        tempoRestoreJob = serviceScope.launch {
            val start = player.playbackParameters.speed
            repeat(TEMPO_RESTORE_STEPS) { index ->
                if (generation != transitionGeneration) return@launch
                val progress = (index + 1) / TEMPO_RESTORE_STEPS.toFloat()
                player.setPlaybackSpeed(start + (originalSpeed - start) * progress)
                delay(TEMPO_RESTORE_STEP_MS)
            }
            player.setPlaybackSpeed(originalSpeed)
        }
    }

    private fun cancelTransition(restorePrimaryVolume: Boolean) {
        transitionGeneration++
        transitionJob?.cancel()
        tempoRestoreJob?.cancel()
        transitionActive = false
        transitionPlayer.pause()
        transitionPlayer.stop()
        transitionPlayer.clearMediaItems()
        transitionPlayer.volume = 0f
        if (restorePrimaryVolume && player.volume <= 0.01f) player.volume = 1f
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueueImmediate()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Timeline callbacks can arrive in bursts when a large queue is created or reordered. We
     * collapse those bursts and move JSON serialization away from the application thread.
     */
    private fun schedulePersistQueue() {
        persistJob?.cancel()
        persistJob = serviceScope.launch {
            delay(300L)
            val snapshot = snapshotQueue()
            launch(Dispatchers.IO) { writeSnapshot(snapshot) }
        }
    }

    private fun snapshotQueue(): QueueSnapshot {
        val items = List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            PersistedItem(
                id = item.mediaId,
                uri = item.localConfiguration?.uri?.toString().orEmpty(),
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
                art = item.mediaMetadata.artworkUri?.toString().orEmpty()
            )
        }
        return QueueSnapshot(
            items = items,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            position = player.currentPosition.coerceAtLeast(0L)
        )
    }

    private fun writeSnapshot(snapshot: QueueSnapshot) {
        val items = JSONArray()
        snapshot.items.forEach { item ->
            if (item.uri.isBlank()) return@forEach
            items.put(JSONObject().apply {
                put("id", item.id)
                put("uri", item.uri)
                put("title", item.title)
                put("artist", item.artist)
                put("album", item.album)
                put("art", item.art)
            })
        }
        store.edit()
            .putString("queue", items.toString())
            .putInt("index", snapshot.index)
            .putLong("position", snapshot.position)
            .apply()
    }

    /** Rare shutdown/task-removal path where losing the latest position is worse than the cost. */
    private fun persistQueueImmediate() {
        persistJob?.cancel()
        runCatching { writeSnapshot(snapshotQueue()) }
    }

    private fun restoreQueue() = runCatching {
        val array = JSONArray(store.getString("queue", "[]"))
        val restored = buildList {
            repeat(array.length()) { i ->
                runCatching {
                    val value = array.getJSONObject(i)
                    val uri = value.optString("uri")
                    if (uri.isBlank() || uri == "null") return@runCatching null
                    MediaItem.Builder()
                        .setMediaId(value.optString("id"))
                        .setUri(uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(value.optString("title"))
                                .setArtist(value.optString("artist"))
                                .setAlbumTitle(value.optString("album"))
                                .setArtworkUri(value.optString("art").takeIf(String::isNotBlank)?.let(Uri::parse))
                                .build()
                        )
                        .build()
                }.getOrNull()?.let(::add)
            }
        }
        if (restored.isNotEmpty()) {
            val index = store.getInt("index", 0).coerceIn(restored.indices)
            val position = store.getLong("position", 0L).coerceAtLeast(0L)
            player.setMediaItems(restored, index, position)
            player.prepare()
        }
    }

    override fun onDestroy() {
        persistQueueImmediate()
        cancelTransition(restorePrimaryVolume = false)
        transitionMonitorJob?.cancel()
        serviceScope.cancel()
        session.release()
        transitionPlayer.release()
        player.release()
        (application as NeoApplication).audioEffects.release()
        super.onDestroy()
    }

    private companion object {
        const val TRANSITION_POLL_MS = 80L
        const val TRANSITION_EARLY_MARGIN_MS = 120L
        const val WARMUP_STEP_MS = 25L
        const val MAX_WARMUP_STEPS = 8
        const val FADE_STEP_MS = 50L
        const val MIN_FADE_STEPS = 6
        const val MAX_FADE_STEPS = 120
        const val MIN_AUTOMIX_RATIO = 0.96f
        const val MAX_AUTOMIX_RATIO = 1.04f
        const val TEMPO_RESTORE_STEPS = 10
        const val TEMPO_RESTORE_STEP_MS = 80L
    }
}
