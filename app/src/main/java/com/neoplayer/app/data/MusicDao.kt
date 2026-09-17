package com.neoplayer.app.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE") fun songs(): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE") fun songsPaged(): PagingSource<Int, SongEntity>
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE LIMIT 200")
    fun search(query: String): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs WHERE id = :id") suspend fun song(id: Long): SongEntity?
    @Query("SELECT * FROM songs WHERE id IN (:ids)") suspend fun songsByIds(ids: List<Long>): List<SongEntity>
    @Query("SELECT * FROM songs") suspend fun songSnapshot(): List<SongEntity>
    @Query("SELECT album, artist, albumId, COUNT(*) AS songCount, SUM(durationMs) AS durationMs FROM songs GROUP BY albumId, album ORDER BY album COLLATE NOCASE") fun albums(): Flow<List<AlbumSummary>>
    @Query("SELECT artist, COUNT(*) AS songCount, COUNT(DISTINCT albumId) AS albumCount FROM songs GROUP BY artist ORDER BY artist COLLATE NOCASE") fun artists(): Flow<List<ArtistSummary>>
    @Query("SELECT genre, COUNT(*) AS songCount FROM songs WHERE genre != '' GROUP BY genre ORDER BY genre COLLATE NOCASE") fun genres(): Flow<List<GenreSummary>>
    @Query("SELECT relativePath, COUNT(*) AS songCount FROM songs GROUP BY relativePath ORDER BY relativePath COLLATE NOCASE") fun folders(): Flow<List<FolderSummary>>
    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, trackNumber, title") fun albumSongs(albumId: Long): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, discNumber, trackNumber") fun artistSongs(artist: String): Flow<List<SongEntity>>
    @Upsert suspend fun upsertSongs(songs: List<SongEntity>)
    @Query("DELETE FROM songs") suspend fun clearSongs()
    @Query("DELETE FROM songs WHERE id IN (:ids)") suspend fun deleteSongsByIds(ids: List<Long>)

    @Query("SELECT songId FROM favorites") fun favoriteIds(): Flow<List<Long>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)") suspend fun isFavorite(id: Long): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(value: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE songId = :id") suspend fun removeFavorite(id: Long)
    @Query("SELECT type || ':' || `key` FROM favorite_collections") fun favoriteCollectionKeys(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_collections WHERE type = :type AND `key` = :key)") suspend fun isFavoriteCollection(type: String, key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavoriteCollection(value: FavoriteCollectionEntity)
    @Query("DELETE FROM favorite_collections WHERE type = :type AND `key` = :key") suspend fun removeFavoriteCollection(type: String, key: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun playlists(): Flow<List<PlaylistEntity>>
    @Insert suspend fun createPlaylist(value: PlaylistEntity): Long
    @Query("UPDATE playlists SET title = :title WHERE id = :id") suspend fun renamePlaylist(id: Long, title: String)
    @Query("UPDATE playlists SET artworkUri = :uri WHERE id = :id") suspend fun setPlaylistArtwork(id: Long, uri: String?)
    @Query("UPDATE playlists SET folderId = :folderId WHERE id = :id") suspend fun setPlaylistFolder(id: Long, folderId: Long?)
    @Query("UPDATE playlists SET customOrder = :position WHERE id = :id") suspend fun setPlaylistCustomOrder(id: Long, position: Int)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :id") suspend fun nextPlaylistPosition(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addPlaylistSong(value: PlaylistSongEntity)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId") suspend fun removePlaylistSong(playlistId: Long, songId: Long)
    @Query("SELECT songs.* FROM songs JOIN playlist_songs ON songs.id = playlist_songs.songId WHERE playlist_songs.playlistId = :id ORDER BY playlist_songs.position") fun playlistSongs(id: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlist_folders ORDER BY position, createdAt") fun playlistFolders(): Flow<List<PlaylistFolderEntity>>
    @Insert suspend fun createPlaylistFolder(value: PlaylistFolderEntity): Long
    @Query("UPDATE playlist_folders SET title = :title WHERE id = :id") suspend fun renamePlaylistFolder(id: Long, title: String)
    @Query("UPDATE playlist_folders SET parentId = :parentId, position = :position WHERE id = :id") suspend fun movePlaylistFolder(id: Long, parentId: Long?, position: Int)
    @Query("DELETE FROM playlist_folders WHERE id = :id") suspend fun deletePlaylistFolderRow(id: Long)
    @Query("UPDATE playlists SET folderId = NULL WHERE folderId = :folderId") suspend fun ungroupPlaylists(folderId: Long)
    @Query("UPDATE playlist_folders SET parentId = NULL WHERE parentId = :folderId") suspend fun ungroupChildFolders(folderId: Long)

    @Query("SELECT * FROM playlist_preferences WHERE playlistId = :playlistId") fun playlistPreference(playlistId: Long): Flow<PlaylistPreferenceEntity?>
    @Upsert suspend fun savePlaylistPreference(value: PlaylistPreferenceEntity)

    @Query("SELECT * FROM pinned_collections ORDER BY position, pinnedAt") fun pinnedCollections(): Flow<List<PinnedCollectionEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM pinned_collections WHERE type = :type AND `key` = :key)") suspend fun isPinned(type: String, key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun pinCollection(value: PinnedCollectionEntity)
    @Query("DELETE FROM pinned_collections WHERE type = :type AND `key` = :key") suspend fun unpinCollection(type: String, key: String)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pinned_collections") suspend fun nextPinPosition(): Int
    @Query("UPDATE pinned_collections SET position = :position WHERE type = :type AND `key` = :key") suspend fun setPinPosition(type: String, key: String, position: Int)

    @Query("SELECT * FROM hidden_songs ORDER BY hiddenAt DESC") fun hiddenSongs(): Flow<List<HiddenSongEntity>>
    @Query("SELECT songId FROM hidden_songs WHERE scopeType = :scopeType AND scopeKey = :scopeKey") fun hiddenSongIds(scopeType: String, scopeKey: String): Flow<List<Long>>
    @Query("SELECT EXISTS(SELECT 1 FROM hidden_songs WHERE songId = :songId AND scopeType = :scopeType AND scopeKey = :scopeKey)") suspend fun isSongHidden(songId: Long, scopeType: String, scopeKey: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun hideSong(value: HiddenSongEntity)
    @Query("DELETE FROM hidden_songs WHERE songId = :songId AND scopeType = :scopeType AND scopeKey = :scopeKey") suspend fun unhideSong(songId: Long, scopeType: String, scopeKey: String)

    @Query("SELECT path FROM included_folders ORDER BY path") fun includedFolders(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun includeSourceFolder(value: IncludedFolderEntity)
    @Query("DELETE FROM included_folders WHERE path = :path") suspend fun removeSourceFolder(path: String)
    @Query("DELETE FROM included_folders") suspend fun clearSourceFolders()

    @Query("SELECT * FROM audio_analysis WHERE songId = :songId") suspend fun audioAnalysis(songId: Long): AudioAnalysisEntity?
    @Query("SELECT * FROM audio_analysis") suspend fun audioAnalysisSnapshot(): List<AudioAnalysisEntity>
    @Upsert suspend fun saveAudioAnalysis(value: AudioAnalysisEntity)
    @Query("DELETE FROM audio_analysis WHERE songId = :songId") suspend fun clearAudioAnalysis(songId: Long)
    @Query("DELETE FROM audio_analysis") suspend fun clearAllAudioAnalysis()

    @Query("SELECT * FROM categories ORDER BY createdAt DESC") fun categories(): Flow<List<CategoryEntity>>
    @Insert suspend fun createCategory(value: CategoryEntity): Long
    @Query("UPDATE categories SET title = :title, description = :description WHERE id = :id") suspend fun updateCategory(id: Long, title: String, description: String)
    @Query("UPDATE categories SET artworkUri = :uri WHERE id = :id") suspend fun setCategoryArtwork(id: Long, uri: String?)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category_songs WHERE categoryId = :id") suspend fun nextCategoryPosition(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addCategorySong(value: CategorySongEntity)
    @Query("DELETE FROM category_songs WHERE categoryId = :categoryId AND songId = :songId") suspend fun removeCategorySong(categoryId: Long, songId: Long)
    @Query("SELECT songs.* FROM songs JOIN category_songs ON songs.id = category_songs.songId WHERE category_songs.categoryId = :id ORDER BY category_songs.position") fun categorySongs(id: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM lyrics WHERE songId = :songId") fun lyrics(songId: Long): Flow<LyricsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLyrics(value: LyricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveHistory(value: ListeningHistoryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensureHistory(value: ListeningHistoryEntity)
    @Query("SELECT * FROM listening_history WHERE songId = :songId") suspend fun history(songId: Long): ListeningHistoryEntity?
    @Query("UPDATE listening_history SET playCount = playCount + 1, totalListeningMs = totalListeningMs + :listenedMs, lastPlayedAt = :at WHERE songId = :songId")
    suspend fun incrementPlay(songId: Long, listenedMs: Long, at: Long)
    @Query("UPDATE listening_history SET totalListeningMs = totalListeningMs + :listenedMs, lastPlayedAt = :at WHERE songId = :songId")
    suspend fun incrementListeningTime(songId: Long, listenedMs: Long, at: Long)
    @Query("UPDATE listening_history SET skipCount = skipCount + 1 WHERE songId = :songId") suspend fun incrementSkip(songId: Long)
    @Query("DELETE FROM listening_history") suspend fun clearHistory()
    @Query("SELECT * FROM listening_history ORDER BY lastPlayedAt DESC") fun histories(): Flow<List<ListeningHistoryEntity>>
    @Query("SELECT * FROM track_audio_effects WHERE songId = :songId") suspend fun trackEffects(songId: Long): TrackAudioEffectsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTrackEffects(value: TrackAudioEffectsEntity)

    @Query("SELECT path FROM excluded_folders ORDER BY path") fun excludedFolders(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun excludeFolder(value: ExcludedFolderEntity)
    @Query("DELETE FROM excluded_folders WHERE path = :path") suspend fun includeFolder(path: String)

    @Query("SELECT * FROM metadata_overrides") suspend fun metadataOverrides(): List<MetadataOverrideEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMetadataOverride(value: MetadataOverrideEntity)
    @Query("DELETE FROM metadata_overrides WHERE songId = :songId") suspend fun clearMetadataOverride(songId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId") suspend fun setPlaylistPosition(playlistId: Long, songId: Long, position: Int)
    @Query("UPDATE category_songs SET position = :position WHERE categoryId = :categoryId AND songId = :songId") suspend fun setCategoryPosition(categoryId: Long, songId: Long, position: Int)

    @Transaction
    suspend fun addPlaylistSongs(playlistId: Long, songIds: List<Long>) {
        var position = nextPlaylistPosition(playlistId)
        songIds.distinct().forEach { songId -> addPlaylistSong(PlaylistSongEntity(playlistId, songId, position++)) }
    }

    @Transaction
    suspend fun addCategorySongs(categoryId: Long, songIds: List<Long>) {
        var position = nextCategoryPosition(categoryId)
        songIds.distinct().forEach { songId -> addCategorySong(CategorySongEntity(categoryId, songId, position++)) }
    }

    @Transaction
    suspend fun reorderPlaylistPositions(playlistId: Long, songIds: List<Long>) {
        songIds.forEachIndexed { index, songId -> setPlaylistPosition(playlistId, songId, index) }
    }

    @Transaction
    suspend fun reorderCategoryPositions(categoryId: Long, songIds: List<Long>) {
        songIds.forEachIndexed { index, songId -> setCategoryPosition(categoryId, songId, index) }
    }

    @Transaction
    suspend fun reorderPlaylistLibrary(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setPlaylistCustomOrder(id, index) }
    }

    @Transaction
    suspend fun deletePlaylistFolder(folderId: Long) {
        ungroupPlaylists(folderId)
        ungroupChildFolders(folderId)
        deletePlaylistFolderRow(folderId)
    }

    @Transaction
    suspend fun togglePin(type: String, key: String) {
        if (isPinned(type, key)) unpinCollection(type, key)
        else pinCollection(PinnedCollectionEntity(type, key, nextPinPosition()))
    }

    @Transaction
    suspend fun reorderPins(values: List<Pair<String, String>>) {
        values.forEachIndexed { index, value -> setPinPosition(value.first, value.second, index) }
    }

    @Transaction
    suspend fun toggleHiddenSong(songId: Long, scopeType: String, scopeKey: String) {
        if (isSongHidden(songId, scopeType, scopeKey)) unhideSong(songId, scopeType, scopeKey)
        else hideSong(HiddenSongEntity(songId, scopeType, scopeKey))
    }

    @Transaction
    suspend fun recordPlayAtomic(songId: Long, listenedMs: Long, at: Long) {
        ensureHistory(ListeningHistoryEntity(songId))
        incrementPlay(songId, listenedMs.coerceAtLeast(0L), at)
    }

    @Transaction
    suspend fun addListeningTimeAtomic(songId: Long, listenedMs: Long, at: Long) {
        ensureHistory(ListeningHistoryEntity(songId))
        incrementListeningTime(songId, listenedMs.coerceAtLeast(0L), at)
    }

    @Transaction
    suspend fun recordSkipAtomic(songId: Long) {
        ensureHistory(ListeningHistoryEntity(songId))
        incrementSkip(songId)
    }

    /**
     * Incremental library replacement. A full DELETE+INSERT invalidates every observing query and
     * briefly exposes an empty library, which becomes visible jank on large collections.
     */
    @Transaction
    suspend fun replaceLibrary(values: List<SongEntity>) {
        if (values.isEmpty()) {
            clearSongs()
            return
        }
        val existing = songSnapshot()
        val existingById = existing.associateBy { it.id }
        val incomingIds = values.asSequence().map { it.id }.toHashSet()
        val changed = values.filter { existingById[it.id] != it }
        if (changed.isNotEmpty()) upsertSongs(changed)

        existing.asSequence()
            .map { it.id }
            .filterNot(incomingIds::contains)
            .chunked(800)
            .forEach { ids -> if (ids.isNotEmpty()) deleteSongsByIds(ids) }
    }
}
