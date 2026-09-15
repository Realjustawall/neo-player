package com.neoplayer.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.neoplayer.app.data.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlaybackState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val current: MediaItem? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList()
)

class PlaybackConnection(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepTimerGeneration = 0
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, NeoPlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { result ->
            result.addListener({
                runCatching { result.get() }.onSuccess {
                    controller = it
                    it.addListener(listener)
                    publish(it)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun play(song: SongEntity, library: List<SongEntity>) {
        controller?.apply {
            val index = library.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            setMediaItems(library.map(SongEntity::toMediaItem), index, 0)
            prepare()
            play()
        }
    }
    fun toggle() = controller?.run { if (isPlaying) pause() else play() }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(position: Long) = controller?.seekTo(position)
    fun addNext(song: SongEntity) = controller?.addMediaItem((controller?.currentMediaItemIndex ?: 0) + 1, song.toMediaItem())
    fun addToQueue(song: SongEntity) = controller?.addMediaItem(song.toMediaItem())
    fun removeQueueItem(index: Int) = controller?.removeMediaItem(index)
    fun moveQueueItem(from: Int, to: Int) = controller?.moveMediaItem(from, to)
    fun clearQueue() = controller?.clearMediaItems()
    fun toggleShuffle() { controller?.shuffleModeEnabled = controller?.shuffleModeEnabled != true }
    fun cycleRepeat() { controller?.repeatMode = when (controller?.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }
    fun setSpeed(speed: Float) = controller?.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int) {
        val generation = ++sleepTimerGeneration
        scope.launch {
            delay(minutes * 60_000L)
            if (generation == sleepTimerGeneration) controller?.pause()
        }
    }
    fun cancelSleepTimer() { sleepTimerGeneration++ }

    fun refreshPosition() { controller?.let(::publish) }
    private fun publish(player: Player) {
        _state.value = PlaybackState(
            connected = true,
            playing = player.isPlaying,
            current = player.currentMediaItem,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        )
    }
}
