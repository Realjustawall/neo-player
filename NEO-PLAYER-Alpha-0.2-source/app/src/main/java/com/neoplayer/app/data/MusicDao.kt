package com.neoplayer.app.data

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
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE LIMIT 200")
    fun search(query: String): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs WHERE id = :id") suspend fun song(id: Long): SongEntity?
    @Query("SELECT * FROM songs WHERE id IN (:ids)") suspend fun songsByIds(ids: List<Long>): List<SongEntity>
    @Query("SELECT album, artist, albumId, COUNT(*) AS songCount, SUM(durationMs) AS durationMs FROM songs GROUP BY albumId, album ORDER BY album COLLATE NOCASE") fun albums(): Flow<List<AlbumSummary>>
    @Query("SELECT artist, COUNT(*) AS songCount, COUNT(DISTINCT albumId) AS albumCount FROM songs GROUP BY artist ORDER BY artist COLLATE NOCASE") fun artists(): Flow<List<ArtistSummary>>
    @Query("SELECT genre, COUNT(*) AS songCount FROM songs WHERE genre != '' GROUP BY genre ORDER BY genre COLLATE NOCASE") fun genres(): Flow<List<GenreSummary>>
    @Query("SELECT relativePath, COUNT(*) AS songCount FROM songs GROUP BY relativePath ORDER BY relativePath COLLATE NOCASE") fun folders(): Flow<List<FolderSummary>>
    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, trackNumber, title") fun albumSongs(albumId: Long): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album, discNumber, trackNumber") fun artistSongs(artist: String): Flow<List<SongEntity>>
    @Upsert suspend fun upsertSongs(songs: List<SongEntity>)
    @Query("DELETE FROM songs") suspend fun clearSongs()

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
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :id") suspend fun nextPlaylistPosition(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addPlaylistSong(value: PlaylistSongEntity)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId") suspend fun removePlaylistSong(playlistId: Long, songId: Long)
    @Query("SELECT songs.* FROM songs JOIN playlist_songs ON songs.id = playlist_songs.songId WHERE playlist_songs.playlistId = :id ORDER BY playlist_songs.position") fun playlistSongs(id: Long): Flow<List<SongEntity>>

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
    @Query("SELECT * FROM listening_history WHERE songId = :songId") suspend fun history(songId: Long): ListeningHistoryEntity?
    @Query("DELETE FROM listening_history") suspend fun clearHistory()
    @Query("SELECT * FROM listening_history ORDER BY lastPlayedAt DESC") fun histories(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT path FROM excluded_folders ORDER BY path") fun excludedFolders(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun excludeFolder(value: ExcludedFolderEntity)
    @Query("DELETE FROM excluded_folders WHERE path = :path") suspend fun includeFolder(path: String)

    @Query("SELECT * FROM metadata_overrides") suspend fun metadataOverrides(): List<MetadataOverrideEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMetadataOverride(value: MetadataOverrideEntity)
    @Query("DELETE FROM metadata_overrides WHERE songId = :songId") suspend fun clearMetadataOverride(songId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId") suspend fun setPlaylistPosition(playlistId: Long, songId: Long, position: Int)
    @Query("UPDATE category_songs SET position = :position WHERE categoryId = :categoryId AND songId = :songId") suspend fun setCategoryPosition(categoryId: Long, songId: Long, position: Int)

    @Transaction
    suspend fun replaceLibrary(values: List<SongEntity>) {
        clearSongs()
        if (values.isNotEmpty()) upsertSongs(values)
    }
}
