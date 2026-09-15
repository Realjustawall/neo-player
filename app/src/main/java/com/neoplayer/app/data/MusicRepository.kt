package com.neoplayer.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.neoplayer.app.lyrics.LyricsProviderRegistry

class MusicRepository(private val dao: MusicDao, private val scanner: MediaStoreScanner, private val lyricsProviders: LyricsProviderRegistry) {
    val songs = dao.songs()
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

    fun search(query: String) = if (query.isBlank()) songs else dao.search(query.trim())
    suspend fun rescan(minDurationMs: Long = 10_000): Int {
        val excluded = dao.excludedFolders().first().toSet()
        val overrides = dao.metadataOverrides().associateBy { it.songId }
        return scanner.scan(minDurationMs).filterNot { song -> excluded.any { path -> song.relativePath.startsWith(path) } }
            .map { song -> overrides[song.id]?.let { value -> song.copy(title = value.title, artist = value.artist, album = value.album, genre = value.genre, year = value.year) } ?: song }
            .also { dao.replaceLibrary(it) }.size
    }
    suspend fun toggleFavorite(id: Long) = if (dao.isFavorite(id)) dao.removeFavorite(id) else dao.addFavorite(FavoriteEntity(id))
    suspend fun toggleFavoriteCollection(type: String, key: String) = if (dao.isFavoriteCollection(type, key)) dao.removeFavoriteCollection(type, key) else dao.addFavoriteCollection(FavoriteCollectionEntity(type, key))
    suspend fun createPlaylist(title: String) = dao.createPlaylist(PlaylistEntity(title = title.trim()))
    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)
    suspend fun addToPlaylist(playlistId: Long, songId: Long) = dao.addPlaylistSong(PlaylistSongEntity(playlistId, songId, dao.nextPlaylistPosition(playlistId)))
    fun playlistSongs(id: Long) = dao.playlistSongs(id)
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = dao.removePlaylistSong(playlistId, songId)
    suspend fun createCategory(title: String, description: String = "") = dao.createCategory(CategoryEntity(title = title.trim(), description = description.trim()))
    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
    suspend fun addToCategory(categoryId: Long, songId: Long) = dao.addCategorySong(CategorySongEntity(categoryId, songId, dao.nextCategoryPosition(categoryId)))
    fun categorySongs(id: Long) = dao.categorySongs(id)
    suspend fun removeFromCategory(categoryId: Long, songId: Long) = dao.removeCategorySong(categoryId, songId)
    suspend fun renamePlaylist(id: Long, title: String) = dao.renamePlaylist(id, title.trim())
    suspend fun setPlaylistArtwork(id: Long, uri: String?) = dao.setPlaylistArtwork(id, uri)
    suspend fun updateCategory(id: Long, title: String, description: String) = dao.updateCategory(id, title.trim(), description.trim())
    suspend fun setCategoryArtwork(id: Long, uri: String?) = dao.setCategoryArtwork(id, uri)
    suspend fun excludeFolder(path: String) = dao.excludeFolder(ExcludedFolderEntity(path))
    suspend fun includeFolder(path: String) = dao.includeFolder(path)
    suspend fun saveMetadata(song: SongEntity, title: String, artist: String, album: String, genre: String, year: Int) {
        dao.saveMetadataOverride(MetadataOverrideEntity(song.id, title.trim(), artist.trim(), album.trim(), genre.trim(), year))
        dao.upsertSongs(listOf(song.copy(title = title.trim(), artist = artist.trim(), album = album.trim(), genre = genre.trim(), year = year)))
    }
    suspend fun reorderPlaylist(id: Long, songs: List<Long>) = songs.forEachIndexed { index, songId -> dao.setPlaylistPosition(id, songId, index) }
    suspend fun reorderCategory(id: Long, songs: List<Long>) = songs.forEachIndexed { index, songId -> dao.setCategoryPosition(id, songId, index) }
    fun lyrics(songId: Long): Flow<LyricsEntity?> = dao.lyrics(songId)
    suspend fun saveLyrics(value: LyricsEntity) = dao.saveLyrics(value)
    suspend fun fetchLyrics(songId: Long): Boolean {
        val song = dao.song(songId) ?: return false
        val found = lyricsProviders.find(song) ?: return false
        dao.saveLyrics(LyricsEntity(songId, found.original, found.translation, found.romanization, found.synchronized, found.source))
        return true
    }
    suspend fun recordPlay(id: Long, listenedMs: Long) {
        val previous = dao.history(id)
        dao.saveHistory((previous ?: ListeningHistoryEntity(id)).copy(
            playCount = (previous?.playCount ?: 0) + 1,
            totalListeningMs = (previous?.totalListeningMs ?: 0) + listenedMs,
            lastPlayedAt = System.currentTimeMillis()
        ))
    }
    suspend fun addListeningTime(id: Long, listenedMs: Long) {
        val previous = dao.history(id) ?: ListeningHistoryEntity(id)
        dao.saveHistory(previous.copy(totalListeningMs = previous.totalListeningMs + listenedMs, lastPlayedAt = System.currentTimeMillis()))
    }
    suspend fun recordSkip(id: Long) {
        val previous = dao.history(id) ?: ListeningHistoryEntity(id)
        dao.saveHistory(previous.copy(skipCount = previous.skipCount + 1))
    }
}
