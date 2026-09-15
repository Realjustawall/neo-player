package com.neoplayer.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs", indices = [Index("album"), Index("artist"), Index("dateAdded")])
data class SongEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val composer: String,
    val bitrate: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val dateAdded: Long,
    val dateModified: Long
) {
    val artworkUri: String get() = "content://media/external/audio/albumart/$albumId"
}

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: Long, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(val playlistId: Long, val songId: Long, val position: Int)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "category_songs",
    primaryKeys = ["categoryId", "songId"],
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")]
)
data class CategorySongEntity(val categoryId: Long, val songId: Long, val position: Int)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val totalListeningMs: Long = 0,
    val lastPlayedAt: Long = 0
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val original: String,
    val translation: String = "",
    val romanization: String = "",
    val synchronized: Boolean = false,
    val source: String = "local",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(@PrimaryKey val path: String)

data class AlbumSummary(val album: String, val artist: String, val albumId: Long, val songCount: Int, val durationMs: Long)
data class ArtistSummary(val artist: String, val songCount: Int, val albumCount: Int)
data class GenreSummary(val genre: String, val songCount: Int)
data class FolderSummary(val relativePath: String, val songCount: Int)
