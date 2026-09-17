package com.neoplayer.app.visual

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/** Muted, looped local Canvas player. The real song audio remains owned by NeoPlaybackService. */
@Composable
fun LocalCanvasVideo(
    uri: String,
    audioPositionMs: Long,
    playing: Boolean,
    fit: String,
    startMs: Long = 0L,
    endMs: Long = 0L,
    playbackSpeed: Float = 1f,
    modifier: Modifier = Modifier,
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val safeStart = startMs.coerceAtLeast(0L)
    val safeEnd = endMs.takeIf { it > safeStart } ?: Long.MIN_VALUE
    val player = remember(uri, safeStart, safeEnd, playbackSpeed) {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(safeStart)
            .apply { if (safeEnd != Long.MIN_VALUE) setEndPositionMs(safeEnd) }
            .build()
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setPlaybackSpeed(playbackSpeed.coerceIn(.5f, 2f))
            setMediaItem(MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(clipping).build())
            prepare()
        }
    }
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    DisposableEffect(player, onError) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onError?.invoke() }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(playing, foreground) {
        player.playWhenReady = playing && foreground
    }

    // Canvas is decorative rather than a second audio timeline. Correct only large drift so seeking
    // the song feels coherent without turning position polling into a second seek storm.
    val syncBucket = audioPositionMs / 2_000L
    LaunchedEffect(syncBucket, player.duration) {
        val duration = player.duration
        if (duration > 0L) {
            val target = safeStart + (audioPositionMs % duration)
            if (abs(player.currentPosition - target) > 2_500L) player.seekTo(target)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
                resizeMode = if (fit == "fit") {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = if (fit == "fit") AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    )
}
