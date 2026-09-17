package com.neoplayer.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neoplayer.app.NeoApplication
import com.neoplayer.app.data.AudioAnalysisEntity
import com.neoplayer.app.data.FolderSummary
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.playback.LocalAudioAnalyzer
import com.neoplayer.app.settings.AppSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class AudioAnalysisProgress(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val error: String? = null
)

/**
 * Additive controller for the advanced offline feature surface. The existing MainViewModel and
 * NeoPlayerApp remain intact; this layer only adds local-first capabilities beside them.
 */
class NeoPlusViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeoApplication
    private val repository = app.repository
    private val analyzer = LocalAudioAnalyzer(application)

    /** Raw library is intentional here so hidden tracks remain visible in the unhide/analysis tools. */
    val songs = repository.rawLibrary.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val visibleSongs = repository.songs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistFolders = repository.playlistFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pinnedCollections = repository.pinnedCollections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hiddenSongs = repository.hiddenSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hiddenGlobalIds = repository.hiddenSongIds().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val includedFolders = repository.includedFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val excludedFolders = repository.excludedFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Direct MediaStore discovery, not the already-filtered Room folder list. */
    val folders = MutableStateFlow<List<FolderSummary>>(emptyList())
    val settings: StateFlow<AppSettings> = app.settings.values
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val playback = app.playback.state
    val audioEffects = app.audioEffects.state

    val cacheBytes = MutableStateFlow(0L)
    val analysisProgress = MutableStateFlow(AudioAnalysisProgress())
    val currentAnalysis = MutableStateFlow<AudioAnalysisEntity?>(null)
    private var libraryAnalysisJob: Job? = null

    init {
        refreshCacheSize()
        refreshSourceFolders()

        viewModelScope.launch {
            app.audioEffects.state
                .map { it.outputVolumeScale }
                .distinctUntilChanged()
                .collect { app.playback.setOutputVolumeScale(it) }
        }

        viewModelScope.launch {
            combine(
                playback.map { it.current?.mediaId?.toLongOrNull() }.distinctUntilChanged(),
                settings.map { Triple(it.loudnessNormalization, it.normalizationTargetLufs, it.automixEnabled) }.distinctUntilChanged()
            ) { songId, config -> songId to config }
                .collectLatest { (songId, config) ->
                    val (normalizationEnabled, targetLufs, _) = config
                    if (!normalizationEnabled || songId == null) {
                        currentAnalysis.value = null
                        app.audioEffects.clearNormalization()
                        return@collectLatest
                    }
                    val song = songs.value.firstOrNull { it.id == songId } ?: return@collectLatest
                    val analysis = repository.audioAnalysis(songId) ?: analyzer.analyze(song, targetLufs).also {
                        repository.saveAudioAnalysis(it)
                    }
                    currentAnalysis.value = analysis
                    val gainMb = ((targetLufs - analysis.integratedLufs) * 100f).toInt().coerceIn(-1200, 1200)
                    app.audioEffects.setNormalizationGain(gainMb)
                }
        }
    }

    fun playlistSongs(id: Long) = repository.playlistSongs(id)
    fun playlistPreference(id: Long) = repository.playlistPreference(id)

    fun createPlaylistFolder(title: String, parentId: Long? = null) = viewModelScope.launch {
        if (title.isNotBlank()) repository.createPlaylistFolder(title, parentId)
    }

    fun renamePlaylistFolder(id: Long, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repository.renamePlaylistFolder(id, title)
    }

    fun movePlaylistFolder(id: Long, parentId: Long?, position: Int) = viewModelScope.launch {
        repository.movePlaylistFolder(id, parentId, position)
    }

    fun deletePlaylistFolder(id: Long) = viewModelScope.launch { repository.deletePlaylistFolder(id) }

    fun movePlaylistToFolder(playlistId: Long, folderId: Long?) = viewModelScope.launch {
        repository.setPlaylistFolder(playlistId, folderId)
    }

    fun reorderPlaylistLibrary(ids: List<Long>) = viewModelScope.launch {
        repository.reorderPlaylistLibrary(ids)
    }

    fun togglePin(type: String, key: String) = viewModelScope.launch { repository.togglePin(type, key) }

    fun toggleHidden(songId: Long, scopeType: String = "global", scopeKey: String = "") = viewModelScope.launch {
        repository.toggleHiddenSong(songId, scopeType, scopeKey)
    }

    fun savePlaylistPreference(id: Long, sortMode: String, ascending: Boolean, viewMode: String) = viewModelScope.launch {
        repository.savePlaylistPreference(id, sortMode, ascending, viewMode)
    }

    fun refreshSourceFolders() = viewModelScope.launch {
        folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }

    fun addSourceFolder(path: String) = viewModelScope.launch {
        repository.addSourceFolder(path)
        repository.rescan(settings.value.minDurationMs)
        folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }

    fun removeSourceFolder(path: String) = viewModelScope.launch {
        repository.removeSourceFolder(path)
        repository.rescan(settings.value.minDurationMs)
        folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }

    fun useAllSourceFolders() = viewModelScope.launch {
        repository.clearSourceFolders()
        repository.rescan(settings.value.minDurationMs)
        folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }

    fun setLoudnessNormalization(enabled: Boolean) = viewModelScope.launch {
        app.settings.setLoudnessNormalization(enabled)
        if (!enabled) app.audioEffects.clearNormalization()
    }

    fun setNormalizationTarget(targetLufs: Float) = viewModelScope.launch {
        app.settings.setNormalizationTargetLufs(targetLufs)
    }

    fun setAutomixEnabled(enabled: Boolean) = viewModelScope.launch { app.settings.setAutomixEnabled(enabled) }
    fun setGaplessEnabled(enabled: Boolean) = viewModelScope.launch { app.settings.setGaplessEnabled(enabled) }
    fun setLibraryViewMode(mode: String) = viewModelScope.launch { app.settings.setLibraryViewMode(mode) }

    fun registerSearch(query: String) = viewModelScope.launch { app.settings.addRecentSearch(query) }
    fun removeRecentSearch(query: String) = viewModelScope.launch { app.settings.removeRecentSearch(query) }
    fun clearRecentSearches() = viewModelScope.launch { app.settings.clearRecentSearches() }

    fun play(song: SongEntity, list: List<SongEntity>) {
        val hidden = hiddenGlobalIds.value
        val visible = list.filterNot { it.id in hidden }
        if (song.id !in hidden) app.playback.play(song, visible.ifEmpty { listOf(song) })
    }

    fun analyzeCurrent() {
        val id = playback.value.current?.mediaId?.toLongOrNull() ?: return
        val song = songs.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val value = analyzer.analyze(song, settings.value.normalizationTargetLufs)
            repository.saveAudioAnalysis(value)
            currentAnalysis.value = value
            if (settings.value.loudnessNormalization) {
                app.audioEffects.setNormalizationGain(
                    ((settings.value.normalizationTargetLufs - value.integratedLufs) * 100f).toInt().coerceIn(-1200, 1200)
                )
            }
        }
    }

    fun analyzeLibrary() {
        if (libraryAnalysisJob?.isActive == true) return
        libraryAnalysisJob = viewModelScope.launch {
            val values = songs.value
            analysisProgress.value = AudioAnalysisProgress(running = true, total = values.size)
            try {
                values.forEachIndexed { index, song ->
                    val existing = repository.audioAnalysis(song.id)
                    if (existing == null) {
                        analysisProgress.value = AudioAnalysisProgress(
                            running = true,
                            completed = index,
                            total = values.size,
                            currentTitle = song.title
                        )
                        repository.saveAudioAnalysis(analyzer.analyze(song, settings.value.normalizationTargetLufs))
                    }
                    analysisProgress.value = AudioAnalysisProgress(
                        running = true,
                        completed = index + 1,
                        total = values.size,
                        currentTitle = song.title
                    )
                    // Yield between tracks so library analysis never monopolizes the process.
                    delay(10L)
                }
                analysisProgress.value = AudioAnalysisProgress(completed = values.size, total = values.size)
            } catch (cancelled: CancellationException) {
                analysisProgress.value = analysisProgress.value.copy(running = false)
                throw cancelled
            } catch (error: Throwable) {
                analysisProgress.value = analysisProgress.value.copy(
                    running = false,
                    error = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    fun cancelLibraryAnalysis() {
        libraryAnalysisJob?.cancel()
        libraryAnalysisJob = null
    }

    fun refreshCacheSize() = viewModelScope.launch(Dispatchers.IO) {
        cacheBytes.value = directorySize(getApplication<Application>().cacheDir) +
            (getApplication<Application>().externalCacheDir?.let(::directorySize) ?: 0L)
    }

    fun clearCache() = viewModelScope.launch(Dispatchers.IO) {
        clearDirectory(getApplication<Application>().cacheDir)
        getApplication<Application>().externalCacheDir?.let(::clearDirectory)
        repository.clearAllAudioAnalysis()
        withContext(Dispatchers.Main) { currentAnalysis.value = null }
        refreshCacheSize()
    }

    fun suggestions(query: String, limit: Int = 12): List<String> {
        val q = query.trim()
        if (q.isBlank()) return settings.value.recentSearches.take(limit)
        return songs.value.asSequence()
            .filterNot { it.id in hiddenGlobalIds.value }
            .flatMap { sequenceOf(it.title, it.artist, it.album, it.genre) }
            .filter { it.isNotBlank() && it.contains(q, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()
    }

    private fun directorySize(root: File): Long = runCatching {
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    private fun clearDirectory(root: File) {
        root.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
    }
}
