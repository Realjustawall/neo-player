package com.neoplayer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neoplayer.app.NeoApplication
import com.neoplayer.app.analysis.AdvancedAudioAnalyzer
import com.neoplayer.app.analysis.ReplayGainScanner
import com.neoplayer.app.data.AdvancedAudioAnalysisEntity
import com.neoplayer.app.data.OfflineBackupEntryEntity
import com.neoplayer.app.data.PinnedCollectionEntity
import com.neoplayer.app.data.PlaylistEntity
import com.neoplayer.app.data.PlaylistFolderEntity
import com.neoplayer.app.data.ReplayGainEntity
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.data.TrackVisualProfileEntity
import com.neoplayer.app.domain.OfflineBackupEngine
import com.neoplayer.app.domain.PlaylistTransferManager
import com.neoplayer.app.visual.LocalSpectrumAnalyzer
import com.neoplayer.app.visual.SpectrumData
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdvancedAnalysisProgress(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val fraction: Float = 0f,
    val error: String? = null
)

data class OfflineBackupItem(val entry: OfflineBackupEntryEntity, val song: SongEntity)

/** Additive controller for the final offline/pro layer. Existing ViewModels remain intact. */
class OfflineProViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeoApplication
    private val repository = app.repository
    private val musicDao = app.database.musicDao()
    private val proDao = app.database.offlineProDao()
    private val trackDao = app.database.trackExperienceDao()
    private val analyzer = AdvancedAudioAnalyzer(application)
    private val replayGainScanner = ReplayGainScanner(application)
    private val spectrumAnalyzer = LocalSpectrumAnalyzer(application)
    private val playlistTransfer = PlaylistTransferManager(application, musicDao)

    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, app.initialSettings)
    val playback = app.playback.state
    val songs = repository.rawLibrary.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val backupEntries = proDao.offlineBackup().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistFolders = repository.playlistFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pinnedCollections = repository.pinnedCollections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val advancedAnalyses = proDao.advancedAnalyses().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val replayGains = proDao.replayGains().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSong: StateFlow<SongEntity?> = combine(songs, playback) { library, state ->
        val id = state.current?.mediaId?.toLongOrNull()
        library.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentAnalysis = currentSong.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else proDao.advancedAnalysis(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentProfile = currentSong.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else trackDao.visualProfile(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val backupItems: StateFlow<List<OfflineBackupItem>> = combine(backupEntries, songs) { entries, library ->
        val byId = library.associateBy { it.id }
        entries.mapNotNull { entry -> byId[entry.songId]?.let { OfflineBackupItem(entry, it) } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val analysisProgress = MutableStateFlow(AdvancedAnalysisProgress())
    val currentReplayGain = MutableStateFlow<ReplayGainEntity?>(null)
    val spectrum = MutableStateFlow<SpectrumData?>(null)
    val spectrumLoading = MutableStateFlow(false)
    val playlistTransferStatus = MutableStateFlow<String?>(null)
    private var analysisJob: Job? = null
    private var spectrumJob: Job? = null
    private var backupRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            currentSong.collectLatest { song ->
                spectrumJob?.cancel()
                spectrum.value = null
                currentReplayGain.value = song?.let { withContext(Dispatchers.IO) { proDao.replayGain(it.id) } }
                if (song != null) spectrum.value = spectrumAnalyzer.loadCached(song)
            }
        }

        viewModelScope.launch {
            combine(
                settings.map { listOf(it.offlineBackupEnabled, it.offlineBackupAutoRefresh, it.offlineBackupLimit, it.offlineBackupMood, it.offlineBackupGenre) }.distinctUntilChanged(),
                repository.histories,
                repository.favorites,
                repository.hiddenSongIds().map { it.toSet() },
                repository.rawLibrary
            ) { config, _, _, _, _ -> config }
                .collectLatest {
                    if (settings.value.offlineBackupEnabled && settings.value.offlineBackupAutoRefresh) {
                        delay(900L)
                        regenerateOfflineBackup()
                    }
                }
        }
    }

    fun analyzeCurrent() {
        val song = currentSong.value ?: return
        if (analysisJob?.isActive == true) return
        analysisJob = viewModelScope.launch {
            analysisProgress.value = AdvancedAnalysisProgress(running = true, total = 1, currentTitle = song.title)
            try {
                val result = analyzer.analyze(song) { fraction ->
                    analysisProgress.value = analysisProgress.value.copy(fraction = fraction)
                }
                withContext(Dispatchers.IO) { proDao.saveAdvancedAnalysis(result) }
                val gain = replayGainScanner.scan(song)
                withContext(Dispatchers.IO) { proDao.saveReplayGain(gain) }
                currentReplayGain.value = gain
                analysisProgress.value = AdvancedAnalysisProgress(completed = 1, total = 1, fraction = 1f)
            } catch (cancelled: CancellationException) {
                analysisProgress.value = analysisProgress.value.copy(running = false)
                throw cancelled
            } catch (error: Throwable) {
                analysisProgress.value = AdvancedAnalysisProgress(error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun analyzeLibrary() {
        if (analysisJob?.isActive == true) return
        analysisJob = viewModelScope.launch {
            val library = songs.value
            analysisProgress.value = AdvancedAnalysisProgress(running = true, total = library.size)
            try {
                library.forEachIndexed { index, song ->
                    val existing = withContext(Dispatchers.IO) { proDao.advancedAnalysisNow(song.id) }
                    analysisProgress.value = AdvancedAnalysisProgress(true, index, library.size, song.title)
                    if (existing == null) {
                        val result = analyzer.analyze(song) { fraction ->
                            analysisProgress.value = AdvancedAnalysisProgress(true, index, library.size, song.title, fraction)
                        }
                        withContext(Dispatchers.IO) { proDao.saveAdvancedAnalysis(result) }
                    }
                    if (withContext(Dispatchers.IO) { proDao.replayGain(song.id) } == null) {
                        withContext(Dispatchers.IO) { proDao.saveReplayGain(replayGainScanner.scan(song)) }
                    }
                    analysisProgress.value = AdvancedAnalysisProgress(true, index + 1, library.size, song.title, 1f)
                    delay(8L)
                }
                analysisProgress.value = AdvancedAnalysisProgress(completed = library.size, total = library.size, fraction = 1f)
                if (settings.value.offlineBackupEnabled) regenerateOfflineBackup()
            } catch (cancelled: CancellationException) {
                analysisProgress.value = analysisProgress.value.copy(running = false)
                throw cancelled
            } catch (error: Throwable) {
                analysisProgress.value = analysisProgress.value.copy(running = false, error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun cancelAnalysis() { analysisJob?.cancel(); analysisJob = null }

    fun ensureSpectrum() {
        val song = currentSong.value ?: return
        if (spectrumLoading.value || spectrum.value?.songId == song.id) return
        spectrumJob?.cancel()
        spectrumJob = viewModelScope.launch {
            spectrumLoading.value = true
            try { spectrum.value = spectrumAnalyzer.analyzeAndCache(song) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { spectrum.value = null }
            finally { spectrumLoading.value = false }
        }
    }

    fun regenerateOfflineBackup() {
        if (!settings.value.offlineBackupEnabled) {
            viewModelScope.launch(Dispatchers.IO) { proDao.clearOfflineBackup() }
            return
        }
        backupRefreshJob?.cancel()
        backupRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            val library = repository.rawLibrary.first()
            val histories = repository.histories.first()
            val favorites = repository.favorites.first().toSet()
            val hidden = repository.hiddenSongIds().first().toSet()
            val advanced = proDao.advancedAnalysisSnapshot()
            val feedback = trackDao.recommendationFeedbackSnapshot()
            val cfg = settings.value
            val values = OfflineBackupEngine.generate(
                songs = library,
                histories = histories,
                favorites = favorites,
                advanced = advanced,
                feedback = feedback,
                hidden = hidden,
                moodFilter = cfg.offlineBackupMood,
                genreFilter = cfg.offlineBackupGenre,
                limit = cfg.offlineBackupLimit
            )
            proDao.replaceOfflineBackup(values)
        }
    }

    fun refreshPosition() = app.playback.refreshPosition()

    fun playPlaylistTrack(song: SongEntity, queue: List<SongEntity>) {
        app.playback.play(song, queue.ifEmpty { listOf(song) })
    }

    fun playBackup(start: SongEntity? = backupItems.value.firstOrNull()?.song) {
        val list = backupItems.value.map { it.song }
        val first = start ?: return
        app.playback.play(first, list.ifEmpty { listOf(first) })
    }

    fun queueBackup(limit: Int = 25) = backupItems.value.take(limit.coerceIn(1, 100)).forEach { app.playback.addToQueue(it.song) }

    fun rawPlaylistSongs(id: Long): Flow<List<SongEntity>> = musicDao.rawPlaylistSongs(id)

    fun playlistScopedHiddenIds(id: Long): Flow<List<Long>> = repository.hiddenSongIds("playlist", id.toString())

    fun togglePlaylistHidden(playlistId: Long, songId: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.toggleHiddenSong(songId, "playlist", playlistId.toString())
    }

    fun reorderPlaylistTracks(playlistId: Long, songIds: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        repository.reorderPlaylist(playlistId, songIds.distinct())
    }

    fun movePlaylistToFolder(playlistId: Long, folderId: Long?) = viewModelScope.launch(Dispatchers.IO) {
        repository.setPlaylistFolder(playlistId, folderId)
    }

    fun reorderFolder(folder: PlaylistFolderEntity, delta: Int) = viewModelScope.launch(Dispatchers.IO) {
        val siblings = playlistFolders.value.filter { it.parentId == folder.parentId }
            .sortedWith(compareBy<PlaylistFolderEntity> { it.position }.thenBy { it.createdAt })
            .toMutableList()
        val index = siblings.indexOfFirst { it.id == folder.id }
        val targetIndex = index + delta
        if (index !in siblings.indices || targetIndex !in siblings.indices) return@launch
        val moved = siblings.removeAt(index)
        siblings.add(targetIndex, moved)
        siblings.forEachIndexed { position, value ->
            repository.movePlaylistFolder(value.id, value.parentId, position)
        }
    }

    fun reorderPinned(from: Int, to: Int) {
        val values = pinnedCollections.value.map { it.type to it.key }.toMutableList()
        if (from !in values.indices || to !in values.indices || from == to) return
        val moved = values.removeAt(from)
        values.add(to, moved)
        viewModelScope.launch(Dispatchers.IO) { repository.reorderPins(values) }
    }

    fun togglePin(type: String, key: String) = viewModelScope.launch(Dispatchers.IO) { repository.togglePin(type, key) }

    fun exportPlaylistM3u8(uri: Uri, playlist: PlaylistEntity) = viewModelScope.launch {
        try {
            val tracks = musicDao.rawPlaylistSongs(playlist.id).first()
            playlistTransfer.exportM3u8(uri, playlist, tracks)
            playlistTransferStatus.value = "Exported ${tracks.size} tracks"
        } catch (error: Throwable) {
            playlistTransferStatus.value = error.message ?: error.javaClass.simpleName
        }
    }

    fun importPlaylistM3u8(uri: Uri, title: String? = null) = viewModelScope.launch {
        persistUriPermission(uri)
        try {
            val result = playlistTransfer.importM3u8(uri, title)
            playlistTransferStatus.value = "Imported ${result.imported}/${result.totalEntries} • unmatched ${result.unmatched}"
        } catch (error: Throwable) {
            playlistTransferStatus.value = error.message ?: error.javaClass.simpleName
        }
    }

    fun setStrictOffline(value: Boolean) = viewModelScope.launch { app.settings.setStrictOfflineMode(value) }
    fun setBackupEnabled(value: Boolean) = viewModelScope.launch { app.settings.setOfflineBackupEnabled(value); if (value) regenerateOfflineBackup() else proDao.clearOfflineBackup() }
    fun setBackupAuto(value: Boolean) = viewModelScope.launch { app.settings.setOfflineBackupAutoRefresh(value) }
    fun setBackupLimit(value: Int) = viewModelScope.launch { app.settings.setOfflineBackupLimit(value); regenerateOfflineBackup() }
    fun setBackupMood(value: String) = viewModelScope.launch { app.settings.setOfflineBackupMood(value); regenerateOfflineBackup() }
    fun setBackupGenre(value: String) = viewModelScope.launch { app.settings.setOfflineBackupGenre(value); regenerateOfflineBackup() }
    fun setAdvancedAutomix(value: Boolean) = viewModelScope.launch { app.settings.setAdvancedAutomixEnabled(value) }
    fun setNormalizationMode(value: String) = viewModelScope.launch { app.settings.setNormalizationMode(value) }
    fun setRememberQueue(value: Boolean) = viewModelScope.launch { app.settings.setRememberQueue(value) }
    fun setResumeLastSong(value: Boolean) = viewModelScope.launch { app.settings.setResumeLastSong(value) }
    fun setDefaultSpeed(value: Float) = viewModelScope.launch { app.settings.setDefaultSpeed(value) }
    fun setLyricsMode(value: String) = viewModelScope.launch { app.settings.setLyricsMode(value) }

    fun persistUriPermission(uri: Uri) = runCatching {
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun setBackgroundImage(uri: Uri?) {
        val song = currentSong.value ?: return
        uri?.let(::persistUriPermission)
        viewModelScope.launch(Dispatchers.IO) {
            val current = trackDao.visualProfileNow(song.id) ?: TrackVisualProfileEntity(song.id)
            trackDao.saveVisualProfile(current.copy(backgroundImageUri = uri?.toString().orEmpty(), updatedAt = System.currentTimeMillis()))
        }
    }

    fun setBackgroundStyle(opacity: Float, blurDp: Int) = updateProfile { current ->
        current.copy(backgroundOpacity = opacity.coerceIn(0f, .85f), backgroundBlurDp = blurDp.coerceIn(0, 48), updatedAt = System.currentTimeMillis())
    }

    fun setCanvasClip(startMs: Long, endMs: Long, speed: Float) = updateProfile { current ->
        val duration = currentSong.value?.durationMs?.coerceAtLeast(0L) ?: 0L
        val start = startMs.coerceIn(0L, duration)
        val end = endMs.takeIf { it > start }?.coerceAtMost(duration) ?: 0L
        current.copy(canvasStartMs = start, canvasEndMs = end, canvasPlaybackSpeed = speed.coerceIn(.5f, 2f), updatedAt = System.currentTimeMillis())
    }

    private fun updateProfile(transform: (TrackVisualProfileEntity) -> TrackVisualProfileEntity) {
        val song = currentSong.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val current = trackDao.visualProfileNow(song.id) ?: TrackVisualProfileEntity(song.id)
            trackDao.saveVisualProfile(transform(current))
        }
    }

    fun availableMoods(): List<String> = listOf("all", "happy", "chill", "focus", "dance", "workout", "sad", "dark", "balanced")
    fun availableGenres(): List<String> = listOf("all") + songs.value.asSequence().map { it.genre.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }.sorted().take(50)

    override fun onCleared() {
        analysisJob?.cancel(); spectrumJob?.cancel(); backupRefreshJob?.cancel()
        super.onCleared()
    }
}
