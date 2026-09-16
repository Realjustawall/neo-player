package com.neoplayer.app.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.neoplayer.app.NeoApplication
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray
import org.json.JSONObject

@OptIn(UnstableApi::class)
class NeoPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val store by lazy { getSharedPreferences("player_state", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        restoreQueue()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = persistQueue()
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = persistQueue()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = persistQueue()
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.audioSessionId > 0) (application as NeoApplication).audioEffects.attach(player.audioSessionId)
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueue()
        super.onTaskRemoved(rootIntent)
    }

    private fun persistQueue() {
        val items = JSONArray()
        repeat(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            items.put(JSONObject().apply {
                put("id", item.mediaId)
                put("uri", item.localConfiguration?.uri.toString())
                put("title", item.mediaMetadata.title?.toString().orEmpty())
                put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                put("album", item.mediaMetadata.albumTitle?.toString().orEmpty())
                put("art", item.mediaMetadata.artworkUri?.toString().orEmpty())
            })
        }
        store.edit().putString("queue", items.toString())
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", player.currentPosition.coerceAtLeast(0))
            .apply()
    }

    private fun restoreQueue() = runCatching {
        val array = JSONArray(store.getString("queue", "[]"))
        val restored = buildList {
            repeat(array.length()) { i ->
                val o = array.getJSONObject(i)
                add(MediaItem.Builder().setMediaId(o.getString("id")).setUri(o.getString("uri"))
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(o.optString("title")).setArtist(o.optString("artist"))
                        .setAlbumTitle(o.optString("album"))
                        .setArtworkUri(o.optString("art").takeIf(String::isNotBlank)?.let(android.net.Uri::parse))
                        .build()).build())
            }
        }
        if (restored.isNotEmpty()) {
            player.setMediaItems(restored, store.getInt("index", 0).coerceIn(restored.indices), store.getLong("position", 0))
            player.prepare()
        }
    }

    override fun onDestroy() {
        persistQueue()
        session.release()
        player.release()
        (application as NeoApplication).audioEffects.release()
        super.onDestroy()
    }
}
