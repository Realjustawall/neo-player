package com.neoplayer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlaylistFolderEntity::class,
        PlaylistPreferenceEntity::class,
        PinnedCollectionEntity::class,
        HiddenSongEntity::class,
        IncludedFolderEntity::class,
        AudioAnalysisEntity::class,
        CategoryEntity::class,
        CategorySongEntity::class,
        ListeningHistoryEntity::class,
        LyricsEntity::class,
        ExcludedFolderEntity::class,
        MetadataOverrideEntity::class,
        FavoriteCollectionEntity::class,
        TrackAudioEffectsEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class NeoDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun create(context: Context): NeoDatabase = Room.databaseBuilder(
            context.applicationContext, NeoDatabase::class.java, "neo-player.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `excluded_folders` (`path` TEXT NOT NULL, PRIMARY KEY(`path`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `metadata_overrides` (`songId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `genre` TEXT NOT NULL, `year` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_collections` (`type` TEXT NOT NULL, `key` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`type`, `key`))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS track_audio_effects (songId INTEGER NOT NULL, preset TEXT NOT NULL, bass INTEGER NOT NULL, virtualizer INTEGER NOT NULL, loudnessMb INTEGER NOT NULL, bandLevels TEXT NOT NULL, PRIMARY KEY(songId))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Extend playlists without rebuilding the existing table, so every Alpha playlist survives.
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `folderId` INTEGER")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `customOrder` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `parentId` INTEGER, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_folders_parentId` ON `playlist_folders` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_folders_position` ON `playlist_folders` (`position`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_preferences` (`playlistId` INTEGER NOT NULL, `sortMode` TEXT NOT NULL, `ascending` INTEGER NOT NULL, `viewMode` TEXT NOT NULL, PRIMARY KEY(`playlistId`))")

                db.execSQL("CREATE TABLE IF NOT EXISTS `pinned_collections` (`type` TEXT NOT NULL, `key` TEXT NOT NULL, `position` INTEGER NOT NULL, `pinnedAt` INTEGER NOT NULL, PRIMARY KEY(`type`, `key`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pinned_collections_position` ON `pinned_collections` (`position`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `hidden_songs` (`songId` INTEGER NOT NULL, `scopeType` TEXT NOT NULL, `scopeKey` TEXT NOT NULL, `hiddenAt` INTEGER NOT NULL, PRIMARY KEY(`songId`, `scopeType`, `scopeKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hidden_songs_scopeType_scopeKey` ON `hidden_songs` (`scopeType`, `scopeKey`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `included_folders` (`path` TEXT NOT NULL, PRIMARY KEY(`path`))")

                db.execSQL("CREATE TABLE IF NOT EXISTS `audio_analysis` (`songId` INTEGER NOT NULL, `integratedLufs` REAL NOT NULL, `peakDb` REAL NOT NULL, `bpm` REAL NOT NULL, `gainMb` INTEGER NOT NULL, `analyzedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
            }
        }
    }
}
