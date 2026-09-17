package com.neoplayer.app.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.neoplayer.app.lyrics.LyricsProviderRegistry
import com.neoplayer.app.lyrics.SidecarLyricsLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.util.Locale

class MusicRepository(
    private val dao: MusicDao,
    private val scanner: MediaStoreScanner,
    private val lyricsProviders: LyricsProviderRegistry,
    private val sidecarLyrics: SidecarLyricsLoader? = null
) {
    private data class CachedSearch(val at: Long, val values: List<SongEntity>)

    private val searchCache = object : LinkedHashMap<String, CachedSearch>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearch>?) = size > 32
    }

    val songs = dao.songs()
    val songsPaged = Pager(PagingConfig(pageSize = 80, prefetchDistance = 24, enablePlaceholders = false)) { dao.songsPaged() }.flow
    val albums = dao.albums()
    val artists = dao.artists()
    val genres = dao.genres()
    val folders = dao.folders()
    val favorites = dao.favoriteIds()
    val favoriteCollections = dao.favoriteCollectionKeys()
    val playlists = dao.playlists()
    val categories = dao.categories()
    val histories = dao.histories()
    val excludedFolders = dao.excludedFolders()

    fun search(query: String) = if (query.isBlank()) songs else flow {
        val key = query.trim().lowercase(Locale.ROOT)
        var lastEmitted: List<SongEntity>? = synchronized(searchCache) {
            searchCache[key]
                ?.takeIf { System.currentTimeMillis() - it.at < SEARCH_CACHE_TTL_MS }
                ?.values
        }
        lastEmitted?.let { emit(it) }

        dao.search(key).distinctUntilChanged().collect { values ->
            synchronized(searchCache) {
                searchCache[key] = CachedSearch(System.currentTimeMillis(), values)
            }
            if (values != lastEmitted) {
                lastEmitted = values
                emit(values)
            }
        }
    }

    suspend fun rescan(minDurationMs: Long = 10_000): Int {
        val excluded = dao.excludedFolders().first().map(::normalizeFolder).filter(String::isNotBlank).toSet()
        val overrides = dao.metadataOverrides().associateBy { it.songId }
        val scanned = scanner.scan(minDurationMs)
            .asSequence()
            .filterNot { song -> isExcluded(song.relativePath, excluded) }
            .map { song ->
                overrides[song.id]?.let { value ->
                    song.copy(
                        title = value.title,
                        artist = value.artist,
                        album = value.album,
                        genre = value.genre,
                        year = value.year
                    )
                } ?: song
            }
            .toList()

        dao.replaceLibrary(scanned)
        synchronized(searchCache) { searchCache.clear() }
        return scanned.size
    }

    suspend fun toggleFavorite(id: Long) = if (dao.isFavorite(id)) dao.removeFavorite(id) else dao.addFavorite(FavoriteEntity(id))
    suspend fun toggleFavoriteCollection(type: String, key: String) = if (dao.isFavoriteCollection(type, key)) dao.removeFavoriteCollection(type, key) else dao.addFavoriteCollection(FavoriteCollectionEntity(type, key))
    suspend fun createPlaylist(title: String) = dao.createPlaylist(PlaylistEntity(title = title.trim()))
    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)
    suspend fun addToPlaylist(playlistId: Long, songId: Long) = dao.addPlaylistSongs(playlistId, listOf(songId))
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = dao.addPlaylistSongs(playlistId, songIds)
    fun playlistSongs(id: Long) = dao.playlistSongs(id)
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = dao.removePlaylistSong(playlistId, songId)

    suspend fun createCategory(title: String, description: String = "") = dao.createCategory(CategoryEntity(title = title.trim(), description = description.trim()))
    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
    suspend fun addToCategory(categoryId: Long, songId: Long) = dao.addCategorySongs(categoryId, listOf(songId))
    suspend fun addSongsToCategory(categoryId: Long, songIds: List<Long>) = dao.addCategorySongs(categoryId, songIds)
    fun categorySongs(id: Long) = dao.categorySongs(id)
    suspend fun removeFromCategory(categoryId: Long, songId: Long) = dao.removeCategorySong(categoryId, songId)

    suspend fun renamePlaylist(id: Long, title: String) = dao.renamePlaylist(id, title.trim())
    suspend fun setPlaylistArtwork(id: Long, uri: String?) = dao.setPlaylistArtwork(id, uri)
    suspend fun updateCategory(id: Long, title: String, description: String) = dao.updateCategory(id, title.trim(), description.trim())
    suspend fun setCategoryArtwork(id: Long, uri: String?) = dao.setCategoryArtwork(id, uri)

    suspend fun excludeFolder(path: String) = dao.excludeFolder(ExcludedFolderEntity(normalizeFolder(path)))
    suspend fun includeFolder(path: String) = dao.includeFolder(normalizeFolder(path))

    suspend fun saveMetadata(song: SongEntity, title: String, artist: String, album: String, genre: String, year: Int) {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        val normalizedAlbum = album.trim()
        val normalizedGenre = genre.trim()
        dao.saveMetadataOverride(MetadataOverrideEntity(song.id, normalizedTitle, normalizedArtist, normalizedAlbum, normalizedGenre, year))
        dao.upsertSongs(listOf(song.copy(title = normalizedTitle, artist = normalizedArtist, album = normalizedAlbum, genre = normalizedGenre, year = year)))
        synchronized(searchCache) { searchCache.clear() }
    }

    suspend fun reorderPlaylist(id: Long, songs: List<Long>) = dao.reorderPlaylistPositions(id, songs.distinct())
    suspend fun reorderCategory(id: Long, songs: List<Long>) = dao.reorderCategoryPositions(id, songs.distinct())

    fun lyrics(songId: Long): Flow<LyricsEntity?> = dao.lyrics(songId)
    suspend fun saveLyrics(value: LyricsEntity) = dao.saveLyrics(value)

    suspend fun loadSidecarLyrics(songId: Long): Boolean {
        val song = dao.song(songId) ?: return false
        val found = sidecarLyrics?.load(song) ?: return false
        dao.saveLyrics(LyricsEntity(songId, found.first, synchronized = found.second, source = "sidecar"))
        return true
    }

    suspend fun fetchLyrics(songId: Long): Boolean {
        val song = dao.song(songId) ?: return false
        sidecarLyrics?.load(song)?.let { (text, synced) ->
            dao.saveLyrics(LyricsEntity(songId, text, synchronized = synced, source = "sidecar"))
            return true
        }
        val found = lyricsProviders.find(song) ?: return false
        dao.saveLyrics(LyricsEntity(songId, found.original, found.translation, found.romanization, found.synchronized, found.source))
        return true
    }

    suspend fun recordPlay(id: Long, listenedMs: Long) {
        dao.recordPlayAtomic(id, listenedMs, System.currentTimeMillis())
    }

    suspend fun addListeningTime(id: Long, listenedMs: Long) {
        if (listenedMs > 0L) dao.addListeningTimeAtomic(id, listenedMs, System.currentTimeMillis())
    }

    suspend fun recordSkip(id: Long) = dao.recordSkipAtomic(id)
    suspend fun clearHistory() = dao.clearHistory()
    suspend fun trackEffects(songId: Long) = dao.trackEffects(songId)
    suspend fun saveTrackEffects(value: TrackAudioEffectsEntity) = dao.saveTrackEffects(value)

    private fun normalizeFolder(path: String): String = path.replace('\\', '/').trim().trim('/')

    private fun isExcluded(relativePath: String, excluded: Set<String>): Boolean {
        val normalized = normalizeFolder(relativePath)
        return excluded.any { path -> normalized == path || normalized.startsWith("$path/") }
    }

    private companion object {
        const val SEARCH_CACHE_TTL_MS = 30_000L
    }
}
