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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val playback = app.playback.state
    val query = MutableStateFlow("")
    val results = query.flatMapLatest(repository::search).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scanning = MutableStateFlow(false)

    fun rescan() = viewModelScope.launch { scanning.value = true; runCatching { repository.rescan() }; scanning.value = false }
    fun play(song: com.neoplayer.app.data.SongEntity, list: List<com.neoplayer.app.data.SongEntity> = songs.value) = app.playback.play(song, list)
    fun togglePlayback() = app.playback.toggle()
    fun next() = app.playback.next()
    fun previous() = app.playback.previous()
    fun seek(position: Long) = app.playback.seekTo(position)
    fun toggleShuffle() = app.playback.toggleShuffle()
    fun cycleRepeat() = app.playback.cycleRepeat()
    fun refreshPosition() = app.playback.refreshPosition()
    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }
    fun addNext(song: com.neoplayer.app.data.SongEntity) = app.playback.addNext(song)
    fun addQueue(song: com.neoplayer.app.data.SongEntity) = app.playback.addToQueue(song)
    fun clearQueue() = app.playback.clearQueue()
    fun removeQueueItem(index: Int) = app.playback.removeQueueItem(index)
    fun createPlaylist(title: String) = viewModelScope.launch { if (title.isNotBlank()) repository.createPlaylist(title) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repository.deletePlaylist(id) }
    fun addToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { repository.addToPlaylist(playlistId, songId) }
    fun createCategory(title: String, description: String = "") = viewModelScope.launch { if (title.isNotBlank()) repository.createCategory(title, description) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }
    fun addToCategory(categoryId: Long, songId: Long) = viewModelScope.launch { repository.addToCategory(categoryId, songId) }
    fun setSpeed(speed: Float) = app.playback.setSpeed(speed)
    fun lyrics(id: Long) = repository.lyrics(id)
    fun saveLyrics(id: Long, text: String) = viewModelScope.launch { repository.saveLyrics(LyricsEntity(id, text, synchronized = text.contains(Regex("\\[\\d+:\\d+")))) }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { app.settings.setTheme(value) }
    fun setAccent(value: Accent) = viewModelScope.launch { app.settings.setAccent(value) }
    fun setCustomColor(value: Int) = viewModelScope.launch { app.settings.setCustomColor(value) }
    fun setLanguage(value: String) = viewModelScope.launch { app.settings.setLanguage(value) }
}
