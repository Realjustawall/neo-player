package com.neoplayer.app.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val dao: MusicDao, private val scanner: MediaStoreScanner) {
    val songs = dao.songs()
    val albums = dao.albums()
    val artists = dao.artists()
    val genres = dao.genres()
    val folders = dao.folders()
    val favorites = dao.favoriteIds()
    val playlists = dao.playlists()
    val categories = dao.categories()

    fun search(query: String) = if (query.isBlank()) songs else dao.search(query.trim())
    suspend fun rescan(): Int = scanner.scan().also { dao.replaceLibrary(it) }.size
    suspend fun toggleFavorite(id: Long) = if (dao.isFavorite(id)) dao.removeFavorite(id) else dao.addFavorite(FavoriteEntity(id))
    suspend fun createPlaylist(title: String) = dao.createPlaylist(PlaylistEntity(title = title.trim()))
    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)
    suspend fun addToPlaylist(playlistId: Long, songId: Long) = dao.addPlaylistSong(PlaylistSongEntity(playlistId, songId, dao.nextPlaylistPosition(playlistId)))
    suspend fun createCategory(title: String, description: String = "") = dao.createCategory(CategoryEntity(title = title.trim(), description = description.trim()))
    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
    suspend fun addToCategory(categoryId: Long, songId: Long) = dao.addCategorySong(CategorySongEntity(categoryId, songId, dao.nextCategoryPosition(categoryId)))
    fun lyrics(songId: Long): Flow<LyricsEntity?> = dao.lyrics(songId)
    suspend fun saveLyrics(value: LyricsEntity) = dao.saveLyrics(value)
    suspend fun recordPlay(id: Long, listenedMs: Long) {
        val previous = dao.history(id)
        dao.saveHistory((previous ?: ListeningHistoryEntity(id)).copy(
            playCount = (previous?.playCount ?: 0) + 1,
            totalListeningMs = (previous?.totalListeningMs ?: 0) + listenedMs,
            lastPlayedAt = System.currentTimeMillis()
        ))
    }
}
