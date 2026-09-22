package com.neoplayer.app.data

import androidx.room.ColumnInfo
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
    val dateModified: Long,
    /** Optional per-song artwork override. Kept in songs so every existing UI surface benefits. */
    @ColumnInfo(defaultValue = "''")
    val customArtworkUri: String = ""
) {
    val artworkUri: String
        get() = customArtworkUri.takeIf { it.isNotBlank() }
            ?: "content://media/external/audio/albumart/$albumId"
}

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: Long, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val folderId: Long? = null,
    val customOrder: Int = 0
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(val playlistId: Long, val songId: Long, val position: Int)

/** Local-only folders can be nested and organize playlists without changing playlist contents. */
@Entity(tableName = "playlist_folders", indices = [Index("parentId"), Index("position")])
data class PlaylistFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val parentId: Long? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Per-playlist presentation preferences. Custom song order remains stored in playlist_songs.position. */
@Entity(tableName = "playlist_preferences")
data class PlaylistPreferenceEntity(
    @PrimaryKey val playlistId: Long,
    val sortMode: String = "custom",
    val ascending: Boolean = true,
    val viewMode: String = "list"
)

/** Generic pinning works for playlist, album, artist, genre and folder without duplicating tables. */
@Entity(tableName = "pinned_collections", primaryKeys = ["type", "key"], indices = [Index("position")])
data class PinnedCollectionEntity(
    val type: String,
    val key: String,
    val position: Int = 0,
    val pinnedAt: Long = System.currentTimeMillis()
)

/** A song can be hidden globally or only inside a particular collection scope. */
@Entity(tableName = "hidden_songs", primaryKeys = ["songId", "scopeType", "scopeKey"], indices = [Index("scopeType", "scopeKey")])
data class HiddenSongEntity(
    val songId: Long,
    val scopeType: String = "global",
    val scopeKey: String = "",
    val hiddenAt: Long = System.currentTimeMillis()
)

/** When non-empty, included folders become an allow-list. Excluded folders still take precedence. */
@Entity(tableName = "included_folders")
data class IncludedFolderEntity(@PrimaryKey val path: String)

/** Cached local DSP analysis used by loudness normalization and tempo-aware transitions. */
@Entity(tableName = "audio_analysis")
data class AudioAnalysisEntity(
    @PrimaryKey val songId: Long,
    val integratedLufs: Float = -14f,
    val peakDb: Float = 0f,
    val bpm: Float = 0f,
    val gainMb: Int = 0,
    val analyzedAt: Long = System.currentTimeMillis()
)

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

@Entity(tableName = "metadata_overrides")
data class MetadataOverrideEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int
)

@Entity(tableName = "favorite_collections", primaryKeys = ["type", "key"])
data class FavoriteCollectionEntity(val type: String, val key: String, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "track_audio_effects")
data class TrackAudioEffectsEntity(
    @PrimaryKey val songId: Long,
    val preset: String = "Normal",
    val bass: Int = 0,
    val virtualizer: Int = 0,
    val loudnessMb: Int = 0,
    val bandLevels: String = "",
    val equalizerEnabled: Boolean = false,
    val bassEnabled: Boolean = false,
    val virtualizerEnabled: Boolean = false,
    val loudnessEnabled: Boolean = false
)

data class AlbumSummary(val album: String, val artist: String, val albumId: Long, val songCount: Int, val durationMs: Long)
data class ArtistSummary(val artist: String, val songCount: Int, val albumCount: Int)
data class GenreSummary(val genre: String, val songCount: Int)
data class FolderSummary(val relativePath: String, val songCount: Int)
