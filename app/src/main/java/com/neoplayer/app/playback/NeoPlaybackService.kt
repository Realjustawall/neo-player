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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(markerClass = [UnstableApi::class])
class NeoPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val store by lazy { getSharedPreferences("player_state", MODE_PRIVATE) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistJob: Job? = null

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

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        restoreQueue()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                schedulePersistQueue()
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
            }

            override fun onPlayerError(error: PlaybackException) {
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
        serviceScope.cancel()
        session.release()
        player.release()
        (application as NeoApplication).audioEffects.release()
        super.onDestroy()
    }
}
