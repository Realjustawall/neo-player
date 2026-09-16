package com.neoplayer.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neoplayer.app.NeoApplication
import com.neoplayer.app.data.LyricsEntity
import com.neoplayer.app.settings.Accent
import com.neoplayer.app.settings.AppSettings
import com.neoplayer.app.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeoApplication
    private val repository = app.repository
    val songs = repository.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums = repository.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val genres = repository.genres.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteIds = repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteCollections = repository.favoriteCollections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val histories = repository.histories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val excludedFolders = repository.excludedFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val playback = app.playback.state
    val audioEffects = app.audioEffects.state
    val audioPresets get() = app.audioEffects.presets
    val query = MutableStateFlow("")
    val results = query.flatMapLatest(repository::search).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scanning = MutableStateFlow(false)
    val lyricsLoading = MutableStateFlow(false)
    val lyricsError = MutableStateFlow<String?>(null)
    val onlineLyricsAvailable: Boolean get() = app.lyricsProviders.available

    init {
        viewModelScope.launch {
            playback.map { it.current?.mediaId?.toLongOrNull() }.filterNotNull().distinctUntilChanged().collect { id ->
                delay(30_000)
                if (playback.value.current?.mediaId?.toLongOrNull() == id && playback.value.playing) {
                    repository.recordPlay(id, 30_000)
                    while (playback.value.current?.mediaId?.toLongOrNull() == id) {
                        delay(30_000)
                        if (playback.value.playing) repository.addListeningTime(id, 30_000)
                    }
                }
            }
        }
    }

    fun rescan() = viewModelScope.launch { scanning.value = true; runCatching { repository.rescan(settings.value.minDurationMs) }; scanning.value = false }
    fun play(song: com.neoplayer.app.data.SongEntity, list: List<com.neoplayer.app.data.SongEntity> = songs.value) = app.playback.play(song, list)
    fun togglePlayback() = app.playback.toggle()
    fun next() { playback.value.current?.mediaId?.toLongOrNull()?.let { id -> viewModelScope.launch { repository.recordSkip(id) } }; app.playback.next() }
    fun previous() = app.playback.previous()
    fun seek(position: Long) = app.playback.seekTo(position)
    fun toggleShuffle() = app.playback.toggleShuffle()
    fun cycleRepeat() = app.playback.cycleRepeat()
    fun refreshPosition() = app.playback.refreshPosition()
    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }
    fun toggleFavoriteCollection(type: String, key: String) = viewModelScope.launch { repository.toggleFavoriteCollection(type, key) }
    fun addNext(song: com.neoplayer.app.data.SongEntity) = app.playback.addNext(song)
    fun addQueue(song: com.neoplayer.app.data.SongEntity) = app.playback.addToQueue(song)
    fun clearQueue() = app.playback.clearQueue()
    fun removeQueueItem(index: Int) = app.playback.removeQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = app.playback.moveQueueItem(from, to)
    fun createPlaylist(title: String) = viewModelScope.launch { if (title.isNotBlank()) repository.createPlaylist(title) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repository.deletePlaylist(id) }
    fun addToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { repository.addToPlaylist(playlistId, songId) }
    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = viewModelScope.launch { songIds.forEach { repository.addToPlaylist(playlistId, it) } }
    fun createCategory(title: String, description: String = "") = viewModelScope.launch { if (title.isNotBlank()) repository.createCategory(title, description) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }
    fun addToCategory(categoryId: Long, songId: Long) = viewModelScope.launch { repository.addToCategory(categoryId, songId) }
    fun setSpeed(speed: Float) = app.playback.setSpeed(speed)
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
    fun excludeFolder(path: String) = viewModelScope.launch { repository.excludeFolder(path); repository.rescan() }
    fun includeFolder(path: String) = viewModelScope.launch { repository.includeFolder(path); repository.rescan() }
    fun saveMetadata(song: com.neoplayer.app.data.SongEntity, title: String, artist: String, album: String, genre: String, year: Int) = viewModelScope.launch { repository.saveMetadata(song, title, artist, album, genre, year) }
    fun lyrics(id: Long) = repository.lyrics(id)
    fun saveLyrics(id: Long, text: String) = viewModelScope.launch { repository.saveLyrics(LyricsEntity(id, text, synchronized = text.contains(Regex("\\[\\d+:\\d+")))) }
    fun saveLyricsLayers(id: Long, original: String, translation: String, romanization: String) = viewModelScope.launch {
        repository.saveLyrics(LyricsEntity(id, original, translation, romanization, synchronized = original.contains(Regex("\\[\\d+:\\d+"))))
    }
    fun fetchLyrics(id: Long) = viewModelScope.launch {
        lyricsLoading.value = true
        lyricsError.value = if (repository.fetchLyrics(id)) null else "Lyrics provider returned no result"
        lyricsLoading.value = false
    }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { app.settings.setTheme(value) }
    fun setAccent(value: Accent) = viewModelScope.launch { app.settings.setAccent(value) }
    fun setCustomColor(value: Int) = viewModelScope.launch { app.settings.setCustomColor(value) }
    fun setLanguage(value: String) = viewModelScope.launch { app.settings.setLanguage(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { app.settings.setReduceMotion(value) }
    fun setDynamicArtwork(value: Boolean) = viewModelScope.launch { app.settings.setDynamicArtwork(value) }
    fun setMinDuration(value: Long) = viewModelScope.launch { app.settings.setMinDuration(value); repository.rescan(value) }
    fun setLyricsMode(value: String) = viewModelScope.launch { app.settings.setLyricsMode(value) }
    fun setLyricsFontSize(value: Int) = viewModelScope.launch { app.settings.setLyricsFontSize(value) }
    fun setAudioPreset(value: String) = app.audioEffects.applyPreset(value)
    fun setBass(value: Int) = app.audioEffects.setBass(value)
    fun setVirtualizer(value: Int) = app.audioEffects.setVirtualizer(value)
    fun setLoudness(value: Int) = app.audioEffects.setLoudness(value)
    fun setEqualizerBand(index: Int, value: Short) = app.audioEffects.setBand(index, value)
    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }
}
