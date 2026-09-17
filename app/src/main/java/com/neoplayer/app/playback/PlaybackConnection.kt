package com.neoplayer.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.neoplayer.app.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min


data class PlaybackState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val current: MediaItem? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList(),
    val error: String? = null,
    val ended: Boolean = false
)

/**
 * Application-scoped MediaController connection.
 *
 * Position-only refreshes deliberately avoid rebuilding the entire queue. This matters for large
 * libraries/queues because the player UI polls position frequently while queue topology changes
 * comparatively rarely.
 */
class PlaybackConnection(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepTimerGeneration = 0
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var reconnectJob: Job? = null
    private var seekJob: Job? = null
    private var errorClearJob: Job? = null
    private var pendingSeekPositionMs: Long = 0L
    private var reconnectAttempt = 0
    private var released = false
    private val _state = MutableStateFlow(PlaybackState())
    private var pendingError: String? = null
    private var crossfadeMs: Long = 0L
    private var fadeGeneration = 0
    private var fadingMediaId: String? = null
    private var outputVolumeScale = 1f
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED)
            publish(player, rebuildQueue = queueChanged)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            cancelFade(restoreVolume = true)
            controller?.let { publish(it, rebuildQueue = false) }
        }

        override fun onPlayerError(error: PlaybackException) {
            pendingError = error.errorCodeName
            controller?.let { publish(it, rebuildQueue = false) }
            scheduleErrorClear()
        }
    }

    fun connect() {
        if (released || controller != null || future != null) return
        reconnectJob?.cancel()
        val token = SessionToken(context, ComponentName(context, NeoPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()
        future = controllerFuture
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }
                .onSuccess { mediaController ->
                    if (released) {
                        mediaController.release()
                        return@onSuccess
                    }
                    reconnectAttempt = 0
                    controller = mediaController
                    mediaController.addListener(listener)
                    mediaController.volume = outputVolumeScale
                    publish(mediaController, rebuildQueue = true)
                }
                .onFailure { error ->
                    runCatching { MediaController.releaseFuture(controllerFuture) }
                    if (future === controllerFuture) future = null
                    controller = null
                    _state.value = _state.value.copy(
                        connected = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                    scheduleErrorClear()
                    scheduleReconnect()
                }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun scheduleReconnect() {
        if (released || reconnectJob?.isActive == true) return
        val attempt = reconnectAttempt++
        val waitMs = min(15_000L, 1_000L shl attempt.coerceAtMost(4))
        reconnectJob = scope.launch {
            delay(waitMs)
            if (!released && controller == null && future == null) connect()
        }
    }

    fun play(song: SongEntity, library: List<SongEntity>) {
        controller?.apply {
            val requestedIndex = library.indexOfFirst { it.id == song.id }
            val effectiveLibrary = if (requestedIndex >= 0) library else listOf(song) + library
            val index = if (requestedIndex >= 0) requestedIndex else 0
            setMediaItems(effectiveLibrary.map(SongEntity::toMediaItem), index, 0L)
            prepare()
            play()
        }
    }

    fun toggle() = controller?.run { if (isPlaying) pause() else play() }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()

    /**
     * Slider callbacks can arrive much faster than the media session can usefully process them.
     * Coalescing to roughly one command per frame keeps scrubbing responsive without flooding IPC.
     */
    fun seekTo(position: Long) {
        pendingSeekPositionMs = position.coerceAtLeast(0L)
        if (seekJob?.isActive == true) return
        seekJob = scope.launch {
            delay(SEEK_COALESCE_MS)
            val target = pendingSeekPositionMs
            controller?.let { player ->
                val duration = safeDuration(player)
                player.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
            }
        }
    }

    fun addNext(song: SongEntity) = controller?.let { player ->
        val insertAt = (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
        player.addMediaItem(insertAt, song.toMediaItem())
    }

    fun addToQueue(song: SongEntity) = controller?.addMediaItem(song.toMediaItem())

    fun removeQueueItem(index: Int) = controller?.let { player ->
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) = controller?.let { player ->
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) {
            player.moveMediaItem(from, to)
        }
    }

    fun clearQueue() = controller?.clearMediaItems()
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setSpeed(speed: Float) = controller?.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))

    /** Baseline volume used by normalization. Timers and fades multiply this value instead of overwriting it. */
    fun setOutputVolumeScale(scale: Float) {
        outputVolumeScale = scale.coerceIn(0.05f, 1f)
        if (fadingMediaId == null) controller?.volume = outputVolumeScale
    }

    fun setCrossfadeDuration(value: Long) {
        crossfadeMs = value.coerceIn(0L, 12_000L)
        if (crossfadeMs == 0L) cancelFade(restoreVolume = true)
    }

    fun setSleepTimer(minutes: Int) {
        val generation = ++sleepTimerGeneration
        scope.launch {
            val total = minutes.coerceAtLeast(0).toLong() * 60_000L
            delay((total - 5_000L).coerceAtLeast(0L))
            repeat(5) { step ->
                if (generation != sleepTimerGeneration) return@launch
                controller?.volume = outputVolumeScale * (1f - ((step + 1) / 5f))
                delay(1_000L)
            }
            if (generation == sleepTimerGeneration) {
                controller?.pause()
                controller?.volume = outputVolumeScale
            }
        }
    }

    fun setSleepAtEndOfSong() {
        val generation = ++sleepTimerGeneration
        scope.launch {
            while (generation == sleepTimerGeneration) {
                delay(500L)
                controller?.let { player ->
                    val duration = safeDuration(player)
                    if (duration > 0L && duration - player.currentPosition in 0L..700L) {
                        player.pause()
                        return@launch
                    }
                }
            }
        }
    }

    fun setSleepAtEndOfQueue() {
        val generation = ++sleepTimerGeneration
        scope.launch {
            while (generation == sleepTimerGeneration) {
                delay(500L)
                controller?.let { player ->
                    if (player.playbackState == Player.STATE_ENDED && !player.hasNextMediaItem()) {
                        player.pause()
                        return@launch
                    }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerGeneration++
        controller?.volume = outputVolumeScale
    }

    /** Cheap high-frequency update used by the progress UI. */
    fun refreshPosition() {
        controller?.let { player ->
            maybeStartFade(player)
            val old = _state.value
            val nextPosition = player.currentPosition.coerceAtLeast(0L)
            val nextDuration = safeDuration(player)
            if (
                old.positionMs != nextPosition ||
                old.durationMs != nextDuration ||
                old.playing != player.isPlaying ||
                old.ended != (player.playbackState == Player.STATE_ENDED)
            ) {
                _state.value = old.copy(
                    playing = player.isPlaying,
                    current = player.currentMediaItem,
                    positionMs = nextPosition,
                    durationMs = nextDuration,
                    ended = player.playbackState == Player.STATE_ENDED,
                    error = pendingError ?: old.error
                )
                pendingError = null
            }
        }
    }

    private fun publish(player: Player, rebuildQueue: Boolean) {
        maybeStartFade(player)
        val old = _state.value
        val queue = if (rebuildQueue || old.queue.size != player.mediaItemCount) {
            List(player.mediaItemCount) { player.getMediaItemAt(it) }
        } else {
            old.queue
        }
        _state.value = PlaybackState(
            connected = true,
            playing = player.isPlaying,
            current = player.currentMediaItem,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = safeDuration(player),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queue,
            error = pendingError ?: old.error,
            ended = player.playbackState == Player.STATE_ENDED
        )
        pendingError = null
    }

    private fun scheduleErrorClear() {
        errorClearJob?.cancel()
        errorClearJob = scope.launch {
            delay(ERROR_VISIBLE_MS)
            _state.value = _state.value.copy(error = null)
        }
    }

    private fun maybeStartFade(player: Player) {
        val duration = safeDuration(player)
        if (duration <= 0L || crossfadeMs <= 0L || !player.isPlaying || !player.hasNextMediaItem()) return
        val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
        val mediaId = player.currentMediaItem?.mediaId
        if (remaining !in 1L..crossfadeMs || fadingMediaId == mediaId) return

        fadingMediaId = mediaId
        val generation = ++fadeGeneration
        scope.launch {
            val steps = 12
            val stepDelay = (crossfadeMs / steps).coerceAtLeast(40L)
            repeat(steps) { step ->
                if (generation != fadeGeneration || fadingMediaId != mediaId) return@launch
                controller?.volume = outputVolumeScale * (1f - ((step + 1) / steps.toFloat()))
                delay(stepDelay)
            }
        }
    }

    private fun cancelFade(restoreVolume: Boolean) {
        fadeGeneration++
        fadingMediaId = null
        if (restoreVolume) controller?.volume = outputVolumeScale
    }

    private fun safeDuration(player: Player): Long = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

    /** Releases controller resources. Safe to call more than once. */
    fun release() {
        if (released) return
        released = true
        reconnectJob?.cancel()
        seekJob?.cancel()
        errorClearJob?.cancel()
        cancelFade(restoreVolume = false)
        sleepTimerGeneration++
        controller?.removeListener(listener)
        future?.let { runCatching { MediaController.releaseFuture(it) } }
        if (future == null) controller?.let { runCatching { it.release() } }
        future = null
        controller = null
        _state.value = PlaybackState()
        scope.cancel()
    }

    private companion object {
        const val SEEK_COALESCE_MS = 24L
        const val ERROR_VISIBLE_MS = 3_000L
    }
}
