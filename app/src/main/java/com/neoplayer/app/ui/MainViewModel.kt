package com.neoplayer.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.neoplayer.app.NeoApplication
import com.neoplayer.app.data.LyricsEntity
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.settings.Accent
import com.neoplayer.app.settings.ThemeMode
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeoApplication
    private val repository = app.repository

    val songs = repository.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val songsPaged = repository.songsPaged.cachedIn(viewModelScope)
    val albums = repository.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val genres = repository.genres.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteIds = repository.favorites.map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val favoriteCollections = repository.favoriteCollections.map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val histories = repository.histories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val excludedFolders = repository.excludedFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, app.initialSettings)
    val playback = app.playback.state
    val audioEffects = app.audioEffects.state
    val localRadio = app.localRadio.state
    val audioPresets get() = app.audioEffects.presets

    val query = MutableStateFlow("")

    /**
     * Search is intentionally local and hot. The full library is available immediately when the
     * Search destination opens, and every keystroke filters a pre-normalized in-memory index on a
     * background dispatcher. This avoids the old debounce + Room round-trip and keeps typing fluid
     * even for large local libraries.
     */
    private data class SearchEntry(val song: SongEntity, val haystack: String)
    private val searchIndex = songs
        .map { library ->
            library.map { song ->
                SearchEntry(
                    song = song,
                    haystack = buildString {
                        append(song.title); append('\u0000')
                        append(song.artist); append('\u0000')
                        append(song.album); append('\u0000')
                        append(song.genre); append('\u0000')
                        append(song.relativePath)
                    }.lowercase(Locale.ROOT)
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val results = combine(searchIndex, query) { index, rawQuery ->
        val terms = rawQuery.trim().lowercase(Locale.ROOT)
            .split(' ')
            .filter { it.isNotBlank() }
        if (terms.isEmpty()) index.map { it.song }
        else index.asSequence()
            .filter { entry -> terms.all(entry.haystack::contains) }
            .map { it.song }
            .toList()
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scanning = MutableStateFlow(false)
    val scanError = MutableStateFlow<String?>(null)
    val lyricsLoading = MutableStateFlow(false)
    val lyricsError = MutableStateFlow<String?>(null)
    val onlineLyricsAvailable: Boolean get() = app.lyricsProviders.available

    private var effectsPersistJob: Job? = null
    private var minDurationRescanJob: Job? = null
    private var crossfadePersistJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.crossfadeMs }.distinctUntilChanged().collect { app.playback.setCrossfadeDuration(it) }
        }

        // Retained client restore makes changes visible immediately; the service now mirrors this
        // logic so profiles also survive when the Activity/ViewModel is gone.
        viewModelScope.launch {
            playback.map { it.current?.mediaId?.toLongOrNull() }.filterNotNull().distinctUntilChanged().collectLatest { id ->
                delay(250L)
                if (playback.value.current?.mediaId?.toLongOrNull() == id) {
                    repository.trackEffects(id)?.let(app.audioEffects::applyProfile) ?: app.audioEffects.resetForTrack()
                }
            }
        }

        // Accurate accounting with residual flush on pause and track transition. The old logic
        // could lose the final <15 seconds forever; this keeps the same batching but never discards
        // listened time simply because playback paused before the next batch boundary.
        viewModelScope.launch {
            var trackedId: Long? = null
            var accumulatedMs = 0L
            var playCounted = false
            var wasPlaying = false
            var lastTick = SystemClock.elapsedRealtime()

            suspend fun flushResidual() {
                val id = trackedId ?: return
                if (accumulatedMs <= 0L) return
                if (playCounted) repository.addListeningTime(id, accumulatedMs)
                else repository.addListeningTime(id, accumulatedMs)
                accumulatedMs = 0L
            }

            while (true) {
                delay(1_000L)
                val now = SystemClock.elapsedRealtime()
                val elapsed = (now - lastTick).coerceIn(0L, 2_500L)
                lastTick = now
                val state = playback.value
                val currentId = state.current?.mediaId?.toLongOrNull()

                if (currentId != trackedId) {
                    flushResidual()
                    trackedId = currentId
                    accumulatedMs = 0L
                    // Play-count threshold applies to this listening session, not lifetime history.
                    playCounted = false
                }

                if (currentId != null && state.playing) {
                    accumulatedMs += elapsed
                    if (!playCounted && accumulatedMs >= PLAY_THRESHOLD_MS) {
                        repository.recordPlay(currentId, accumulatedMs)
                        accumulatedMs = 0L
                        playCounted = true
                    } else if (playCounted && accumulatedMs >= LISTENING_WRITE_BATCH_MS) {
                        repository.addListeningTime(currentId, accumulatedMs)
                        accumulatedMs = 0L
                    }
                } else if (wasPlaying && !state.playing) {
                    flushResidual()
                }
                wasPlaying = state.playing
            }
        }
    }

    fun rescan() = viewModelScope.launch { performRescan(settings.value.minDurationMs) }
    private suspend fun performRescan(minDurationMs: Long) {
        scanning.value = true; scanError.value = null
        try { repository.rescan(minDurationMs.coerceAtLeast(0L)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { scanError.value = error.message ?: error.javaClass.simpleName }
        finally { scanning.value = false }
    }

    fun play(song: SongEntity, list: List<SongEntity> = songs.value) = app.playback.play(song, list)
    fun startRadio(seed: SongEntity) {
        val related = songs.value.asSequence()
            .filter { it.id == seed.id || it.artist.equals(seed.artist, true) || (seed.genre.isNotBlank() && it.genre.equals(seed.genre, true)) }
            .distinctBy { it.id }.toList().shuffled()
        app.playback.play(seed, related.ifEmpty { listOf(seed) })
    }
    fun playPlaylist(id: Long) = viewModelScope.launch { repository.playlistSongs(id).first().let { list -> list.firstOrNull()?.let { app.playback.play(it, list) } } }
    fun playCategory(id: Long) = viewModelScope.launch { repository.categorySongs(id).first().let { list -> list.firstOrNull()?.let { app.playback.play(it, list) } } }
    fun togglePlayback() = app.playback.toggle()
    fun next() {
        val state = playback.value
        state.current?.mediaId?.toLongOrNull()?.let { id ->
            val duration = state.durationMs
            val skipBoundary = if (duration > 0L) minOf(SKIP_MAX_POSITION_MS, duration / 2L) else SKIP_MAX_POSITION_MS
            if (state.positionMs < skipBoundary) viewModelScope.launch { repository.recordSkip(id) }
        }
        app.playback.next()
    }
    fun previous() = app.playback.previous()
    fun seek(position: Long) = app.playback.seekTo(position)
    fun toggleShuffle() = app.playback.toggleShuffle()
    fun cycleRepeat() = app.playback.cycleRepeat()
    fun refreshPosition() = app.playback.refreshPosition()
    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }
    fun toggleFavoriteCollection(type: String, key: String) = viewModelScope.launch { repository.toggleFavoriteCollection(type, key) }
    fun addNext(song: SongEntity) = app.playback.addNext(song)
    fun addQueue(song: SongEntity) = app.playback.addToQueue(song)
    fun playQueueItem(index: Int) = app.playback.playQueueItem(index)
    fun clearQueue() = app.playback.clearQueue()
    fun removeQueueItem(index: Int) = app.playback.removeQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = app.playback.moveQueueItem(from, to)

    fun createPlaylist(title: String) = viewModelScope.launch { if (title.isNotBlank()) repository.createPlaylist(title) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repository.deletePlaylist(id) }
    fun addToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { repository.addToPlaylist(playlistId, songId) }
    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = viewModelScope.launch { repository.addSongsToPlaylist(playlistId, songIds) }
    fun createCategory(title: String, description: String = "") = viewModelScope.launch { if (title.isNotBlank()) repository.createCategory(title, description) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }
    fun addToCategory(categoryId: Long, songId: Long) = viewModelScope.launch { repository.addToCategory(categoryId, songId) }
    fun setSpeed(speed: Float) = app.playback.setSpeed(speed)

    fun setCrossfadeMs(value: Long) {
        val safe = value.coerceIn(0L, 12_000L)
        app.playback.setCrossfadeDuration(safe)
        crossfadePersistJob?.cancel()
        crossfadePersistJob = viewModelScope.launch { delay(120L); app.settings.setCrossfadeMs(safe) }
    }
    fun setSleepTimer(minutes: Int) = app.playback.setSleepTimer(minutes)
    fun cancelSleepTimer() = app.playback.cancelSleepTimer()
    fun sleepAtEndOfSong() = app.playback.setSleepAtEndOfSong()
    fun sleepAtEndOfQueue() = app.playback.setSleepAtEndOfQueue()

    fun renamePlaylist(id: Long, title: String) = viewModelScope.launch { if (title.isNotBlank()) repository.renamePlaylist(id, title) }
    fun updateCategory(id: Long, title: String, description: String) = viewModelScope.launch { if (title.isNotBlank()) repository.updateCategory(id, title, description) }
    fun setCollectionArtwork(id: Long, uri: String?, playlist: Boolean) = viewModelScope.launch { if (playlist) repository.setPlaylistArtwork(id, uri) else repository.setCategoryArtwork(id, uri) }
    fun playlistSongs(id: Long) = repository.playlistSongs(id)
    fun categorySongs(id: Long) = repository.categorySongs(id)
    fun removeFromPlaylist(id: Long, songId: Long) = viewModelScope.launch { repository.removeFromPlaylist(id, songId) }
    fun removeFromCategory(id: Long, songId: Long) = viewModelScope.launch { repository.removeFromCategory(id, songId) }
    fun reorderCollection(id: Long, songIds: List<Long>, playlist: Boolean) = viewModelScope.launch { if (playlist) repository.reorderPlaylist(id, songIds) else repository.reorderCategory(id, songIds) }

    fun excludeFolder(path: String) = viewModelScope.launch { repository.excludeFolder(path); performRescan(settings.value.minDurationMs) }
    fun includeFolder(path: String) = viewModelScope.launch { repository.includeFolder(path); performRescan(settings.value.minDurationMs) }
    fun saveMetadata(song: SongEntity, title: String, artist: String, album: String, genre: String, year: Int) = viewModelScope.launch { repository.saveMetadata(song, title, artist, album, genre, year) }

    fun lyrics(id: Long) = repository.lyrics(id)
    fun loadSidecarLyrics(id: Long) = viewModelScope.launch { repository.loadSidecarLyrics(id) }
    fun saveLyrics(id: Long, text: String) = viewModelScope.launch { repository.saveLyrics(LyricsEntity(id, text, synchronized = text.contains(Regex("\\[\\d+:\\d+")))) }
    fun saveLyricsLayers(id: Long, original: String, translation: String, romanization: String) = viewModelScope.launch {
        repository.saveLyrics(LyricsEntity(id, original, translation, romanization, synchronized = original.contains(Regex("\\[\\d+:\\d+"))))
    }

    fun fetchLyrics(id: Long) = viewModelScope.launch {
        lyricsLoading.value = true; lyricsError.value = null
        try {
            val mode = settings.value.lyricsMode.lowercase()
            val success = when (mode) {
                "offline" -> repository.fetchOfflineLyrics(id)
                "online" -> if (settings.value.strictOfflineMode) false else repository.fetchOnlineLyrics(id)
                else -> repository.fetchLyrics(id)
            }
            if (!success) lyricsError.value = when {
                settings.value.strictOfflineMode && mode == "online" -> "Strict Offline Mode blocks online lyrics"
                mode == "offline" -> "No local/sidecar lyrics found"
                else -> "Lyrics provider returned no result"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { lyricsError.value = error.message ?: error.javaClass.simpleName }
        finally { lyricsLoading.value = false }
    }

    fun setTheme(value: ThemeMode) = viewModelScope.launch { app.settings.setTheme(value) }
    fun setAccent(value: Accent) = viewModelScope.launch { app.settings.setAccent(value) }
    fun setCustomColor(value: Int) = viewModelScope.launch { app.settings.setCustomColor(value) }
    fun setLanguage(value: String) = viewModelScope.launch { app.settings.setLanguage(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { app.settings.setReduceMotion(value) }
    fun setDynamicArtwork(value: Boolean) = viewModelScope.launch { app.settings.setDynamicArtwork(value) }
    fun setRememberQueue(value: Boolean) = viewModelScope.launch { app.settings.setRememberQueue(value) }
    fun setResumeLastSong(value: Boolean) = viewModelScope.launch { app.settings.setResumeLastSong(value) }
    fun setDefaultSpeed(value: Float) = viewModelScope.launch { app.settings.setDefaultSpeed(value.coerceIn(.25f, 3f)) }

    fun setMinDuration(value: Long) {
        val safe = value.coerceAtLeast(0L)
        viewModelScope.launch { app.settings.setMinDuration(safe) }
        minDurationRescanJob?.cancel()
        minDurationRescanJob = viewModelScope.launch { delay(450L); performRescan(safe) }
    }
    fun setLyricsMode(value: String) = viewModelScope.launch { app.settings.setLyricsMode(value) }
    fun setLyricsFontSize(value: Int) = viewModelScope.launch { app.settings.setLyricsFontSize(value) }

    private fun persistTrackEffects() {
        val id = playback.value.current?.mediaId?.toLongOrNull() ?: return
        effectsPersistJob?.cancel()
        effectsPersistJob = viewModelScope.launch {
            delay(140L)
            if (playback.value.current?.mediaId?.toLongOrNull() == id) repository.saveTrackEffects(app.audioEffects.snapshot(id))
        }
    }
    fun setAudioPreset(value: String) { app.audioEffects.applyPreset(value); persistTrackEffects() }
    fun setBass(value: Int) { app.audioEffects.setBass(value); persistTrackEffects() }
    fun setVirtualizer(value: Int) { app.audioEffects.setVirtualizer(value); persistTrackEffects() }
    fun setLoudness(value: Int) { app.audioEffects.setLoudness(value); persistTrackEffects() }
    fun setEqualizerBand(index: Int, value: Short) { app.audioEffects.setBand(index, value); persistTrackEffects() }
    fun setEqualizerEnabled(value: Boolean) { app.audioEffects.setEqualizerEnabled(value); persistTrackEffects() }
    fun setBassEnabled(value: Boolean) { app.audioEffects.setBassEnabled(value); persistTrackEffects() }
    fun setVirtualizerEnabled(value: Boolean) { app.audioEffects.setVirtualizerEnabled(value); persistTrackEffects() }
    fun setLoudnessEnabled(value: Boolean) { app.audioEffects.setLoudnessEnabled(value); persistTrackEffects() }
    fun hostLocalRadio() = app.localRadio.startHosting()
    fun joinLocalRadio(code: String, name: String) = app.localRadio.join(code, name)
    fun stopLocalRadio() = app.localRadio.stop()
    fun disconnectRadioListener(id: String) = app.localRadio.disconnectListener(id)
    fun disconnectAllRadioListeners() = app.localRadio.disconnectAllListeners()
    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }

    private companion object {
        const val PLAY_THRESHOLD_MS = 30_000L
        const val LISTENING_WRITE_BATCH_MS = 15_000L
        const val SKIP_MAX_POSITION_MS = 30_000L
    }
}
