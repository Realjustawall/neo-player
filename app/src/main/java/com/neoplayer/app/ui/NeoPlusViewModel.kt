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
import java.util.Locale
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

data class CacheBreakdown(
    val totalBytes: Long = 0L,
    val waveformBytes: Long = 0L,
    val spectrumBytes: Long = 0L,
    val otherBytes: Long = 0L
)

class NeoPlusViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeoApplication
    private val repository = app.repository
    private val analyzer = LocalAudioAnalyzer(application)

    val songs = repository.rawLibrary.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val visibleSongs = repository.songs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistFolders = repository.playlistFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pinnedCollections = repository.pinnedCollections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hiddenSongs = repository.hiddenSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hiddenGlobalIds = repository.hiddenSongIds().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val includedFolders = repository.includedFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val excludedFolders = repository.excludedFolders.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folders = MutableStateFlow<List<FolderSummary>>(emptyList())
    val settings: StateFlow<AppSettings> = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, app.initialSettings)
    val playback = app.playback.state
    val audioEffects = app.audioEffects.state

    val cacheBytes = MutableStateFlow(0L)
    val cacheBreakdown = MutableStateFlow(CacheBreakdown())
    val analysisProgress = MutableStateFlow(AudioAnalysisProgress())
    val currentAnalysis = MutableStateFlow<AudioAnalysisEntity?>(null)
    private var libraryAnalysisJob: Job? = null
    private var normalizationTargetJob: Job? = null
    @Volatile private var suggestionIndex: List<SuggestionEntry> = emptyList()

    private data class SuggestionEntry(val display: String, val normalized: String)

    init {
        refreshCacheSize()
        refreshSourceFolders()

        // UI only observes the cached analysis now. PlaybackService owns normalization so it remains
        // correct with no Activity and cannot be accidentally reset by ViewModel lifecycle.
        viewModelScope.launch {
            playback.map { it.current?.mediaId?.toLongOrNull() }.distinctUntilChanged().collectLatest { id ->
                currentAnalysis.value = id?.let { repository.audioAnalysis(it) }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            combine(songs, hiddenGlobalIds) { library, hidden -> library to hidden }.collectLatest { (library, hidden) ->
                suggestionIndex = library.asSequence()
                    .filterNot { it.id in hidden }
                    .flatMap { sequenceOf(it.title, it.artist, it.album, it.genre) }
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinctBy { it.lowercase(Locale.ROOT) }
                    .map { SuggestionEntry(it, it.lowercase(Locale.ROOT)) }
                    .toList()
            }
        }
    }

    fun playlistSongs(id: Long) = repository.playlistSongs(id)
    fun playlistPreference(id: Long) = repository.playlistPreference(id)
    fun createPlaylistFolder(title: String, parentId: Long? = null) = viewModelScope.launch { if (title.isNotBlank()) repository.createPlaylistFolder(title, parentId) }
    fun renamePlaylistFolder(id: Long, title: String) = viewModelScope.launch { if (title.isNotBlank()) repository.renamePlaylistFolder(id, title) }
    fun movePlaylistFolder(id: Long, parentId: Long?, position: Int) = viewModelScope.launch { repository.movePlaylistFolder(id, parentId, position) }
    fun deletePlaylistFolder(id: Long) = viewModelScope.launch { repository.deletePlaylistFolder(id) }
    fun movePlaylistToFolder(playlistId: Long, folderId: Long?) = viewModelScope.launch { repository.setPlaylistFolder(playlistId, folderId) }
    fun reorderPlaylistLibrary(ids: List<Long>) = viewModelScope.launch { repository.reorderPlaylistLibrary(ids) }
    fun togglePin(type: String, key: String) = viewModelScope.launch { repository.togglePin(type, key) }
    fun toggleHidden(songId: Long, scopeType: String = "global", scopeKey: String = "") = viewModelScope.launch { repository.toggleHiddenSong(songId, scopeType, scopeKey) }
    fun savePlaylistPreference(id: Long, sortMode: String, ascending: Boolean, viewMode: String) = viewModelScope.launch { repository.savePlaylistPreference(id, sortMode, ascending, viewMode) }

    fun refreshSourceFolders() = viewModelScope.launch { folders.value = repository.discoverSourceFolders(settings.value.minDurationMs) }
    fun addSourceFolder(path: String) = viewModelScope.launch {
        repository.addSourceFolder(path); repository.rescan(settings.value.minDurationMs); folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }
    fun removeSourceFolder(path: String) = viewModelScope.launch {
        repository.removeSourceFolder(path); repository.rescan(settings.value.minDurationMs); folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }
    fun useAllSourceFolders() = viewModelScope.launch {
        repository.clearSourceFolders(); repository.rescan(settings.value.minDurationMs); folders.value = repository.discoverSourceFolders(settings.value.minDurationMs)
    }

    fun setLoudnessNormalization(enabled: Boolean) = viewModelScope.launch {
        app.settings.setLoudnessNormalization(enabled)
        if (!enabled) app.audioEffects.clearNormalization()
    }

    /** DataStore writes are delayed while a slider is moving; service receives only settled values. */
    fun setNormalizationTarget(targetLufs: Float) {
        normalizationTargetJob?.cancel()
        normalizationTargetJob = viewModelScope.launch {
            delay(160L)
            app.settings.setNormalizationTargetLufs(targetLufs)
        }
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
            // Immediate foreground feedback is retained; the service will reuse this cache for all
            // following/background playback and remains authoritative after the Activity disappears.
            if (settings.value.loudnessNormalization && settings.value.normalizationMode == "analysis") {
                app.audioEffects.setNormalizationGain(((settings.value.normalizationTargetLufs - value.integratedLufs) * 100f).toInt().coerceIn(-1200, 1200))
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
                        analysisProgress.value = AudioAnalysisProgress(true, index, values.size, song.title)
                        repository.saveAudioAnalysis(analyzer.analyze(song, settings.value.normalizationTargetLufs))
                    }
                    analysisProgress.value = AudioAnalysisProgress(true, index + 1, values.size, song.title)
                    delay(10L)
                }
                analysisProgress.value = AudioAnalysisProgress(completed = values.size, total = values.size)
            } catch (cancelled: CancellationException) {
                analysisProgress.value = analysisProgress.value.copy(running = false); throw cancelled
            } catch (error: Throwable) {
                analysisProgress.value = analysisProgress.value.copy(running = false, error = error.message ?: error.javaClass.simpleName)
            }
        }
    }
    fun cancelLibraryAnalysis() { libraryAnalysisJob?.cancel(); libraryAnalysisJob = null }

    fun refreshCacheSize() = viewModelScope.launch(Dispatchers.IO) {
        val cache = getApplication<Application>().cacheDir
        val external = getApplication<Application>().externalCacheDir
        val waveform = directorySize(File(cache, "neo_waveforms"))
        val spectrum = directorySize(File(cache, "neo_spectrum"))
        val total = directorySize(cache) + (external?.let(::directorySize) ?: 0L)
        cacheBytes.value = total
        cacheBreakdown.value = CacheBreakdown(total, waveform, spectrum, (total - waveform - spectrum).coerceAtLeast(0L))
    }

    fun clearCache() = viewModelScope.launch(Dispatchers.IO) {
        clearDirectory(getApplication<Application>().cacheDir)
        getApplication<Application>().externalCacheDir?.let(::clearDirectory)
        repository.clearAllAudioAnalysis()
        app.database.offlineProDao().clearAllAdvancedAnalysis()
        app.database.offlineProDao().clearAllReplayGain()
        app.database.offlineProDao().clearOfflineBackup()
        withContext(Dispatchers.Main) { currentAnalysis.value = null }
        refreshCacheSize()
    }
    fun clearWaveformCache() = clearNamedCache("neo_waveforms")
    fun clearSpectrumCache() = clearNamedCache("neo_spectrum")
    fun clearAnalysisCache() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAllAudioAnalysis()
        app.database.offlineProDao().clearAllAdvancedAnalysis()
        app.database.offlineProDao().clearAllReplayGain()
        app.database.offlineProDao().clearOfflineBackup()
        withContext(Dispatchers.Main) { currentAnalysis.value = null }
        refreshCacheSize()
    }
    private fun clearNamedCache(name: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { File(getApplication<Application>().cacheDir, name).deleteRecursively() }
        refreshCacheSize()
    }

    fun suggestions(query: String, limit: Int = 12): List<String> {
        val key = query.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return settings.value.recentSearches.take(limit)
        return suggestionIndex.asSequence().filter { key in it.normalized }.take(limit).map { it.display }.toList()
    }

    private fun directorySize(root: File): Long = runCatching { if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    private fun clearDirectory(root: File) { root.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } } }

    override fun onCleared() {
        normalizationTargetJob?.cancel(); libraryAnalysisJob?.cancel(); super.onCleared()
    }
}
