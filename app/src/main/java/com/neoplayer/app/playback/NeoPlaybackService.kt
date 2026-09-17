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
import com.neoplayer.app.analysis.AdvancedAudioAnalyzer
import com.neoplayer.app.analysis.ReplayGainScanner
import com.neoplayer.app.data.AdvancedAudioAnalysisEntity
import com.neoplayer.app.settings.AppSettings
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlinx.coroutines.CancellationException
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

@OptIn(markerClass = [UnstableApi::class])
class NeoPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var transitionPlayer: ExoPlayer
    private lateinit var session: MediaSession
    private val store by lazy { getSharedPreferences("player_state", MODE_PRIVATE) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transitionEffects = AudioEffectsEngine()
    private val localAnalyzer by lazy { LocalAudioAnalyzer(this) }
    private val replayGainScanner by lazy { ReplayGainScanner(this) }
    private val advancedAnalyzer by lazy { AdvancedAudioAnalyzer(this) }

    private var persistJob: Job? = null
    private var transitionJob: Job? = null
    private var transitionMonitorJob: Job? = null
    private var tempoRestoreJob: Job? = null
    private var processingJob: Job? = null
    private var deepAnalysisJob: Job? = null
    private var crossfadeMs = 0L
    private var automixEnabled = false
    private var advancedAutomixEnabled = true
    private var gaplessEnabled = true
    private var rememberQueue = true
    private var resumeLastSong = true
    private var defaultSpeed = 1f
    private var normalizationEnabled = false
    private var normalizationTarget = -14f
    private var normalizationMode = "smart"
    private var transitionGeneration = 0
    private var transitionActive = false
    private var transitionFailed = false
    private var restoreAttempted = false
    private var primaryBaseScale = 1f
    private var transitionBaseScale = 1f
    private var primaryVolumeBeforeTransition = 1f

    private data class PersistedItem(val id: String, val uri: String, val title: String, val artist: String, val album: String, val art: String)
    private data class QueueSnapshot(val items: List<PersistedItem>, val currentId: String, val index: Int, val position: Long)
    private data class PlaybackSettings(
        val crossfadeMs: Long,
        val automix: Boolean,
        val advancedAutomix: Boolean,
        val gapless: Boolean,
        val rememberQueue: Boolean,
        val resumeLastSong: Boolean,
        val defaultSpeed: Float,
        val normalize: Boolean,
        val targetLufs: Float,
        val normalizationMode: String
    )
    private data class MixInfo(
        val bpm: Float = 0f,
        val beatMs: Float = 0f,
        val beatOffsetMs: Float = 0f,
        val beatConfidence: Float = 0f,
        val phraseLength: Int = 8,
        val phraseOffsetMs: Float = 0f,
        val phraseConfidence: Float = 0f,
        val camelot: String = ""
    )
    private data class TransitionPlan(val fadeMs: Long, val nextCueMs: Long, val tempoRatio: Float, val preAlignDelayMs: Long)

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        player = ExoPlayer.Builder(this).setAudioAttributes(attributes, true).setHandleAudioBecomingNoisy(true).build()
        transitionPlayer = ExoPlayer.Builder(this).setAudioAttributes(attributes, false).setHandleAudioBecomingNoisy(false).build().apply { volume = 0f }

        player.addListener(primaryListener)
        transitionPlayer.addListener(transitionListener)
        session = MediaSession.Builder(this, player).build()

        val app = application as NeoApplication
        serviceScope.launch {
            app.audioEffects.state.map { it.outputVolumeScale }.distinctUntilChanged().collect { scale ->
                primaryBaseScale = scale.coerceIn(.05f, 1f)
                if (!transitionActive) player.volume = primaryBaseScale
            }
        }
        observePlaybackSettings()
        startTransitionMonitor()
    }

    private val primaryListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!transitionActive) cancelTransition(restorePrimaryVolume = false)
            mediaItem?.let { item ->
                schedulePrimaryProcessing(item)
                scheduleBackgroundDeepAnalysis(item)
            }
            schedulePersistQueue()
            PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = schedulePersistQueue()
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = schedulePersistQueue()
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && player.audioSessionId > 0) {
                (application as NeoApplication).audioEffects.attach(player.audioSessionId)
                player.currentMediaItem?.let(::schedulePrimaryProcessing)
            }
            if (playbackState == Player.STATE_ENDED) schedulePersistQueue()
            PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
        override fun onPlayerError(error: PlaybackException) {
            cancelTransition(restorePrimaryVolume = true)
            if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() } else player.pause()
            schedulePersistQueue()
        }
    }

    private val transitionListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && transitionPlayer.audioSessionId > 0) {
                transitionEffects.attach(transitionPlayer.audioSessionId)
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            transitionFailed = true
            serviceScope.launch { cancelTransition(restorePrimaryVolume = true) }
        }
    }

    private fun observePlaybackSettings() {
        val app = application as NeoApplication
        serviceScope.launch {
            app.settings.values.map {
                PlaybackSettings(
                    it.crossfadeMs, it.automixEnabled, it.advancedAutomixEnabled, it.gaplessEnabled,
                    it.rememberQueue, it.resumeLastSong, it.defaultSpeed, it.loudnessNormalization,
                    it.normalizationTargetLufs, it.normalizationMode
                )
            }.distinctUntilChanged().collect { value ->
                crossfadeMs = value.crossfadeMs.coerceIn(0L, 12_000L)
                automixEnabled = value.automix
                advancedAutomixEnabled = value.advancedAutomix
                gaplessEnabled = value.gapless
                rememberQueue = value.rememberQueue
                resumeLastSong = value.resumeLastSong
                defaultSpeed = value.defaultSpeed.coerceIn(.25f, 3f)
                normalizationEnabled = value.normalize
                normalizationTarget = value.targetLufs.coerceIn(-23f, -8f)
                normalizationMode = value.normalizationMode
                if (!rememberQueue) clearPersistedQueue()
                if (!restoreAttempted) { restoreAttempted = true; if (rememberQueue) restoreQueue() }
                if (player.playbackParameters.speed == 1f || player.mediaItemCount == 0) player.setPlaybackSpeed(defaultSpeed)
                if (crossfadeMs == 0L) cancelTransition(restorePrimaryVolume = true)
                runCatching { player.pauseAtEndOfMediaItems = !gaplessEnabled && crossfadeMs == 0L }
                if (!normalizationEnabled) (application as NeoApplication).audioEffects.clearNormalization()
                else player.currentMediaItem?.let(::schedulePrimaryProcessing)
            }
        }
    }

    private fun schedulePrimaryProcessing(item: MediaItem) {
        val id = item.mediaId.toLongOrNull() ?: return
        processingJob?.cancel()
        processingJob = serviceScope.launch {
            delay(80L)
            if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
            val app = application as NeoApplication
            val profile = withContext(Dispatchers.IO) { app.repository.trackEffects(id) }
            if (profile != null) app.audioEffects.applyProfile(profile) else app.audioEffects.resetForTrack()
            val gain = resolveNormalizationGainMb(id)
            if (normalizationEnabled) app.audioEffects.setNormalizationGain(gain) else app.audioEffects.clearNormalization()
            primaryBaseScale = app.audioEffects.state.value.outputVolumeScale
            if (!transitionActive) player.volume = primaryBaseScale
        }
    }

    /**
     * Professional AutoMix becomes self-improving without making transition timing wait for a deep
     * decode. The active track (and then the upcoming track) are analyzed after playback settles,
     * entirely off the application thread, and cached for later transitions/Offline Backup.
     */
    private fun scheduleBackgroundDeepAnalysis(item: MediaItem) {
        if (!advancedAutomixEnabled) return
        val songId = item.mediaId.toLongOrNull() ?: return
        deepAnalysisJob?.cancel()
        deepAnalysisJob = serviceScope.launch {
            delay(DEEP_ANALYSIS_IDLE_DELAY_MS)
            val app = application as NeoApplication
            val pro = app.database.offlineProDao()
            suspend fun ensure(id: Long) {
                if (pro.advancedAnalysisNow(id) != null) return
                val song = app.database.musicDao().song(id) ?: return
                val value = advancedAnalyzer.analyze(song)
                pro.saveAdvancedAnalysis(value)
            }
            withContext(Dispatchers.IO) { ensure(songId) }
            if (player.currentMediaItem?.mediaId != item.mediaId || !advancedAutomixEnabled) return@launch
            val nextIndex = player.nextMediaItemIndex
            if (nextIndex in 0 until player.mediaItemCount) {
                player.getMediaItemAt(nextIndex).mediaId.toLongOrNull()?.let { nextId ->
                    withContext(Dispatchers.IO) { ensure(nextId) }
                }
            }
        }
    }

    private suspend fun resolveNormalizationGainMb(songId: Long): Int {
        if (!normalizationEnabled) return 0
        val app = application as NeoApplication
        val pro = app.database.offlineProDao()
        if (normalizationMode != "analysis") {
            var tag = withContext(Dispatchers.IO) { pro.replayGain(songId) }
            if (tag == null) {
                val song = withContext(Dispatchers.IO) { app.database.musicDao().song(songId) }
                if (song != null) {
                    tag = replayGainScanner.scan(song)
                    withContext(Dispatchers.IO) { pro.saveReplayGain(tag) }
                }
            }
            tag?.preferredGainDb?.let { return (it * 100f).toInt().coerceIn(-1200, 1200) }
            if (normalizationMode == "replaygain") return 0
        }
        var analysis = withContext(Dispatchers.IO) { app.repository.audioAnalysis(songId) }
        if (analysis == null) {
            val song = withContext(Dispatchers.IO) { app.database.musicDao().song(songId) } ?: return 0
            analysis = localAnalyzer.analyze(song, normalizationTarget)
            withContext(Dispatchers.IO) { app.repository.saveAudioAnalysis(analysis) }
        }
        return ((normalizationTarget - analysis.integratedLufs) * 100f).toInt().coerceIn(-1200, 1200)
    }

    private fun startTransitionMonitor() {
        transitionMonitorJob?.cancel()
        transitionMonitorJob = serviceScope.launch {
            while (isActive) {
                delay(TRANSITION_POLL_MS)
                if (transitionActive || crossfadeMs <= 0L || !player.isPlaying || !player.hasNextMediaItem()) continue
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: continue
                val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
                if (remaining in 1L..(crossfadeMs + TRANSITION_EARLY_MARGIN_MS)) startOverlappingTransition(remaining)
            }
        }
    }

    private fun startOverlappingTransition(remainingAtDetection: Long) {
        if (transitionActive || crossfadeMs <= 0L || !player.hasNextMediaItem()) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex !in 0 until player.mediaItemCount) return
        val current = player.currentMediaItem ?: return
        val next = player.getMediaItemAt(nextIndex)
        if (next.localConfiguration?.uri == null) return
        transitionActive = true
        transitionFailed = false
        primaryVolumeBeforeTransition = player.volume.coerceIn(0f, 1f)
        val generation = ++transitionGeneration
        transitionJob?.cancel()
        transitionJob = serviceScope.launch {
            try {
                val originalSpeed = player.playbackParameters.speed
                val plan = if (automixEnabled) buildTransitionPlan(current, next, remainingAtDetection, originalSpeed)
                    else TransitionPlan(min(remainingAtDetection.coerceAtLeast(250L), crossfadeMs.coerceAtLeast(250L)), 0L, 1f, 0L)
                if (plan.preAlignDelayMs > 0L) delay(plan.preAlignDelayMs)
                if (generation != transitionGeneration || transitionFailed) return@launch

                transitionPlayer.stop(); transitionPlayer.clearMediaItems(); transitionEffects.release()
                transitionPlayer.setMediaItem(next)
                transitionPlayer.setPlaybackSpeed((originalSpeed * plan.tempoRatio).coerceIn(.25f, 3f))
                transitionPlayer.volume = 0f
                transitionPlayer.prepare()
                if (plan.nextCueMs > 0L) transitionPlayer.seekTo(plan.nextCueMs)
                transitionPlayer.playWhenReady = true

                var warmup = 0
                while (transitionPlayer.playbackState != Player.STATE_READY && warmup < MAX_WARMUP_STEPS && !transitionFailed) {
                    if (generation != transitionGeneration) return@launch
                    delay(WARMUP_STEP_MS); warmup++
                }
                if (transitionFailed || transitionPlayer.playbackState == Player.STATE_IDLE) {
                    cancelTransition(restorePrimaryVolume = true); return@launch
                }

                val nextId = next.mediaId.toLongOrNull()
                val nextProfile = if (nextId != null) {
                    val app = application as NeoApplication
                    withContext(Dispatchers.IO) { app.repository.trackEffects(nextId) }
                } else null
                val nextNormalizationGain = if (nextId != null) resolveNormalizationGainMb(nextId) else 0
                if (nextProfile != null) transitionEffects.applyProfile(nextProfile) else transitionEffects.resetForTrack()
                if (normalizationEnabled) transitionEffects.setNormalizationGain(nextNormalizationGain) else transitionEffects.clearNormalization()
                transitionBaseScale = transitionEffects.state.value.outputVolumeScale
                // Controller-side volume changes (for example the existing sleep-timer fade) are a
                // multiplier on top of normalization. Mirror that multiplier to the second decoder
                // so a crossfade can never suddenly become louder during the timer fade.
                val externalVolumeScale = if (primaryBaseScale > .001f)
                    (primaryVolumeBeforeTransition / primaryBaseScale).coerceIn(0f, 1f) else 1f
                val transitionTargetVolume = transitionBaseScale * externalVolumeScale

                val fadeDuration = plan.fadeMs.coerceAtLeast(250L)
                val steps = (fadeDuration / FADE_STEP_MS).toInt().coerceIn(MIN_FADE_STEPS, MAX_FADE_STEPS)
                val stepDelay = (fadeDuration / steps).coerceAtLeast(25L)
                repeat(steps) { step ->
                    if (generation != transitionGeneration || transitionFailed || player.currentMediaItem?.mediaId != current.mediaId) {
                        cancelTransition(restorePrimaryVolume = true); return@launch
                    }
                    val progress = (step + 1) / steps.toFloat()
                    player.volume = primaryVolumeBeforeTransition * (1f - progress)
                    transitionPlayer.volume = transitionTargetVolume * progress
                    delay(stepDelay)
                }

                if (generation != transitionGeneration || transitionFailed) return@launch
                val handoff = transitionPlayer.currentPosition.coerceAtLeast(plan.nextCueMs)
                player.seekTo(nextIndex, handoff)
                // Apply the exact next-track audio profile immediately at handoff instead of waiting
                // for the delayed transition callback. This removes the short EQ/gain mismatch that
                // could otherwise be audible after a successful dual-decoder crossfade.
                val primaryEffects = (application as NeoApplication).audioEffects
                if (nextProfile != null) primaryEffects.applyProfile(nextProfile) else primaryEffects.resetForTrack()
                if (normalizationEnabled) primaryEffects.setNormalizationGain(nextNormalizationGain) else primaryEffects.clearNormalization()
                primaryBaseScale = primaryEffects.state.value.outputVolumeScale
                if (plan.tempoRatio != 1f) {
                    player.setPlaybackSpeed((originalSpeed * plan.tempoRatio).coerceIn(.25f, 3f))
                    scheduleTempoRestore(originalSpeed, generation)
                } else player.setPlaybackSpeed(originalSpeed)
                player.volume = primaryBaseScale * externalVolumeScale
                if (!player.isPlaying) player.play()
                transitionPlayer.pause(); transitionPlayer.stop(); transitionPlayer.clearMediaItems(); transitionPlayer.volume = 0f
                transitionEffects.release()
                transitionActive = false
                schedulePersistQueue(); PlaybackWidgetProvider.updateAll(this@NeoPlaybackService)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { cancelTransition(restorePrimaryVolume = true) }
        }
    }

    private suspend fun buildTransitionPlan(current: MediaItem, next: MediaItem, remaining: Long, originalSpeed: Float): TransitionPlan {
        val pair = loadMixPair(current, next)
        val currentInfo = pair.first
        val nextInfo = pair.second
        val tempoRatio = if (currentInfo.bpm > 0f && nextInfo.bpm > 0f) (currentInfo.bpm / nextInfo.bpm).coerceIn(MIN_AUTOMIX_RATIO, MAX_AUTOMIX_RATIO) else 1f
        var fade = crossfadeMs.coerceAtLeast(250L)
        if (currentInfo.beatMs > 0f) {
            val beat = currentInfo.beatMs.toLong().coerceAtLeast(250L)
            val availableBeats = floor(fade.toDouble() / beat).toInt().coerceAtLeast(1)
            val phraseBeats = if (advancedAutomixEnabled && currentInfo.phraseConfidence >= .25f) currentInfo.phraseLength.coerceAtLeast(4) else 1
            val alignedBeats = if (phraseBeats > 1 && availableBeats >= 4) (availableBeats / 4 * 4).coerceAtLeast(4) else availableBeats
            fade = (alignedBeats * beat).coerceAtMost(crossfadeMs.coerceAtLeast(beat))
        }
        if (advancedAutomixEnabled && currentInfo.camelot.isNotBlank() && nextInfo.camelot.isNotBlank() && !harmonicCompatible(currentInfo.camelot, nextInfo.camelot)) {
            fade = (fade * .78f).toLong().coerceAtLeast(500L)
        }
        fade = min(remaining.coerceAtLeast(250L), fade)
        val nextCue = if (advancedAutomixEnabled && nextInfo.phraseConfidence >= .30f) nextInfo.phraseOffsetMs.toLong().coerceIn(0L, 12_000L)
            else nextInfo.beatOffsetMs.toLong().coerceIn(0L, 2_000L)
        val preAlign = if (advancedAutomixEnabled && currentInfo.beatMs > 0f && currentInfo.beatConfidence >= .25f) {
            val beat = currentInfo.beatMs
            val phase = ((player.currentPosition - currentInfo.beatOffsetMs) % beat + beat) % beat
            (beat - phase).toLong().takeIf { it in 1..MAX_PREALIGN_MS } ?: 0L
        } else 0L
        return TransitionPlan(fade, nextCue, tempoRatio, preAlign)
    }

    private suspend fun loadMixPair(current: MediaItem, next: MediaItem): Pair<MixInfo, MixInfo> = withContext(Dispatchers.IO) {
        val app = application as NeoApplication
        suspend fun one(item: MediaItem): MixInfo {
            val id = item.mediaId.toLongOrNull() ?: return MixInfo()
            val deep = app.database.offlineProDao().advancedAnalysisNow(id)
            if (deep != null) return deep.toMixInfo()
            val simple = app.repository.audioAnalysis(id)
            return MixInfo(bpm = simple?.bpm ?: 0f, beatMs = simple?.bpm?.takeIf { it > 0 }?.let { 60_000f / it } ?: 0f)
        }
        one(current) to one(next)
    }

    private fun AdvancedAudioAnalysisEntity.toMixInfo() = MixInfo(bpm, beatIntervalMs, beatOffsetMs, beatConfidence, phraseLengthBeats, phraseOffsetMs, phraseConfidence, camelotKey)

    private fun harmonicCompatible(a: String, b: String): Boolean {
        if (a == b) return true
        val an = a.dropLast(1).toIntOrNull() ?: return false; val bn = b.dropLast(1).toIntOrNull() ?: return false
        val al = a.lastOrNull(); val bl = b.lastOrNull()
        if (an == bn && al != bl) return true
        if (al == bl) { val d = abs(an - bn); return d == 1 || d == 11 }
        return false
    }

    private fun scheduleTempoRestore(originalSpeed: Float, generation: Int) {
        tempoRestoreJob?.cancel()
        tempoRestoreJob = serviceScope.launch {
            val start = player.playbackParameters.speed
            repeat(TEMPO_RESTORE_STEPS) { index ->
                if (generation != transitionGeneration) return@launch
                val p = (index + 1) / TEMPO_RESTORE_STEPS.toFloat()
                player.setPlaybackSpeed(start + (originalSpeed - start) * p)
                delay(TEMPO_RESTORE_STEP_MS)
            }
            player.setPlaybackSpeed(originalSpeed)
        }
    }

    private fun cancelTransition(restorePrimaryVolume: Boolean) {
        val wasActive = transitionActive
        transitionGeneration++
        transitionJob?.cancel(); tempoRestoreJob?.cancel(); transitionActive = false; transitionFailed = false
        runCatching { transitionPlayer.pause() }; runCatching { transitionPlayer.stop() }; runCatching { transitionPlayer.clearMediaItems() }
        transitionPlayer.volume = 0f; transitionEffects.release()
        if (restorePrimaryVolume) {
            // A settings emission with crossfade=0 must never erase normalization attenuation.
            player.volume = if (wasActive) primaryVolumeBeforeTransition.coerceIn(.05f, 1f) else primaryBaseScale
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (rememberQueue) persistQueueImmediate(); super.onTaskRemoved(rootIntent) }

    private fun schedulePersistQueue() {
        if (!rememberQueue) return
        persistJob?.cancel()
        persistJob = serviceScope.launch { delay(300L); val snapshot = snapshotQueue(); launch(Dispatchers.IO) { writeSnapshot(snapshot) } }
    }

    private fun snapshotQueue(): QueueSnapshot {
        val items = List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            PersistedItem(item.mediaId, item.localConfiguration?.uri?.toString().orEmpty(), item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.albumTitle?.toString().orEmpty(), item.mediaMetadata.artworkUri?.toString().orEmpty())
        }
        return QueueSnapshot(items, player.currentMediaItem?.mediaId.orEmpty(), player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0L))
    }

    private fun writeSnapshot(snapshot: QueueSnapshot) {
        val items = JSONArray()
        snapshot.items.forEach { item -> if (item.uri.isNotBlank()) items.put(JSONObject().apply { put("id",item.id);put("uri",item.uri);put("title",item.title);put("artist",item.artist);put("album",item.album);put("art",item.art) }) }
        store.edit().putInt("queue_schema", QUEUE_SCHEMA).putString("queue", items.toString()).putString("current_id", snapshot.currentId).putInt("index", snapshot.index).putLong("position", snapshot.position).apply()
    }

    private fun persistQueueImmediate() { if (!rememberQueue) return; persistJob?.cancel(); runCatching { writeSnapshot(snapshotQueue()) } }
    private fun clearPersistedQueue() { persistJob?.cancel(); store.edit().remove("queue").remove("queue_schema").remove("current_id").remove("index").remove("position").apply() }

    private fun restoreQueue() = runCatching {
        val array = JSONArray(store.getString("queue", "[]"))
        val restored = buildList {
            repeat(array.length()) { i ->
                runCatching {
                    val value=array.getJSONObject(i);val uri=value.optString("uri");if(uri.isBlank()||uri=="null")return@runCatching null
                    MediaItem.Builder().setMediaId(value.optString("id")).setUri(uri).setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder().setTitle(value.optString("title")).setArtist(value.optString("artist")).setAlbumTitle(value.optString("album")).setArtworkUri(value.optString("art").takeIf(String::isNotBlank)?.let(Uri::parse)).build()
                    ).build()
                }.getOrNull()?.let(::add)
            }
        }
        if (restored.isNotEmpty()) {
            val currentId = store.getString("current_id", "").orEmpty()
            val matched = restored.indexOfFirst { it.mediaId == currentId }
            val index = (if (matched >= 0) matched else store.getInt("index", 0)).coerceIn(restored.indices)
            val position = if (resumeLastSong) store.getLong("position", 0L).coerceAtLeast(0L) else 0L
            player.setMediaItems(restored, index, position); player.setPlaybackSpeed(defaultSpeed); player.prepare()
        }
    }

    override fun onDestroy() {
        persistQueueImmediate(); processingJob?.cancel(); deepAnalysisJob?.cancel(); cancelTransition(false); transitionMonitorJob?.cancel(); serviceScope.cancel(); session.release(); transitionPlayer.removeListener(transitionListener); transitionPlayer.release(); player.removeListener(primaryListener); player.release(); transitionEffects.release(); (application as NeoApplication).audioEffects.release(); super.onDestroy()
    }

    private companion object {
        const val QUEUE_SCHEMA=2
        const val TRANSITION_POLL_MS=80L;const val TRANSITION_EARLY_MARGIN_MS=120L;const val WARMUP_STEP_MS=25L;const val MAX_WARMUP_STEPS=12;const val FADE_STEP_MS=50L;const val MIN_FADE_STEPS=6;const val MAX_FADE_STEPS=120
        const val DEEP_ANALYSIS_IDLE_DELAY_MS=2_500L;const val MIN_AUTOMIX_RATIO=.96f;const val MAX_AUTOMIX_RATIO=1.04f;const val TEMPO_RESTORE_STEPS=10;const val TEMPO_RESTORE_STEP_MS=80L;const val MAX_PREALIGN_MS=320L
    }
}
