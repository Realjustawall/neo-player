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
        TrackAudioEffectsEntity::class,
        TrackVisualProfileEntity::class,
        OfflineLyricsTranscriptEntity::class,
        OfflineSpeechModelEntity::class,
        RecommendationFeedbackEntity::class,
        TrackExperiencePreferenceEntity::class,
        AdvancedAudioAnalysisEntity::class,
        ReplayGainEntity::class,
        OfflineBackupEntryEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class NeoDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun trackExperienceDao(): TrackExperienceDao
    abstract fun offlineProDao(): OfflineProDao

    companion object {
        fun create(context: Context): NeoDatabase = Room.databaseBuilder(
            context.applicationContext, NeoDatabase::class.java, "neo-player.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

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

        /**
         * Purely additive migration: all Alpha 0.3/NEO+ tables survive unchanged. One non-null
         * custom-artwork column is appended to songs and five new feature tables are created.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `customArtworkUri` TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_visual_profiles` (" +
                        "`songId` INTEGER NOT NULL, `canvasUri` TEXT, `canvasEnabled` INTEGER NOT NULL, " +
                        "`canvasFit` TEXT NOT NULL, `themeMode` TEXT NOT NULL, `accentArgb` INTEGER NOT NULL, " +
                        "`backgroundArgb` INTEGER NOT NULL, `secondaryArgb` INTEGER NOT NULL, " +
                        "`visualizerMode` TEXT NOT NULL, `visualizerSensitivity` REAL NOT NULL, " +
                        "`animationIntensity` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_lyrics_transcripts` (" +
                        "`songId` INTEGER NOT NULL, `engine` TEXT NOT NULL, `modelId` TEXT NOT NULL, " +
                        "`language` TEXT NOT NULL, `plainText` TEXT NOT NULL, `lrcText` TEXT NOT NULL, " +
                        "`wordTimedJson` TEXT NOT NULL, `averageConfidence` REAL NOT NULL, " +
                        "`generatedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_lyrics_transcripts_generatedAt` ON `offline_lyrics_transcripts` (`generatedAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_speech_models` (" +
                        "`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`localPath` TEXT NOT NULL, `sampleRate` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_speech_models_language` ON `offline_speech_models` (`language`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_speech_models_importedAt` ON `offline_speech_models` (`importedAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recommendation_feedback` (" +
                        "`songId` INTEGER NOT NULL, `boost` INTEGER NOT NULL, `dismissCount` INTEGER NOT NULL, " +
                        "`lastSeededAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_experience_preferences` (" +
                        "`id` INTEGER NOT NULL, `selectedSpeechModelId` TEXT NOT NULL, " +
                        "`selectedSpeechLanguage` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }


        /** Additive v6 migration; no prior feature table or row is removed. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `canvasStartMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `canvasEndMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `canvasPlaybackSpeed` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `backgroundImageUri` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `backgroundMode` TEXT NOT NULL DEFAULT 'overlay'")
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `backgroundOpacity` REAL NOT NULL DEFAULT 0.28")
                db.execSQL("ALTER TABLE `track_visual_profiles` ADD COLUMN `backgroundBlurDp` INTEGER NOT NULL DEFAULT 18")
                db.execSQL("CREATE TABLE IF NOT EXISTS `advanced_audio_analysis` (`songId` INTEGER NOT NULL, `bpm` REAL NOT NULL, `beatIntervalMs` REAL NOT NULL, `beatOffsetMs` REAL NOT NULL, `beatConfidence` REAL NOT NULL, `phraseLengthBeats` INTEGER NOT NULL, `phraseOffsetMs` REAL NOT NULL, `phraseConfidence` REAL NOT NULL, `musicalKey` TEXT NOT NULL, `camelotKey` TEXT NOT NULL, `keyConfidence` REAL NOT NULL, `energy` REAL NOT NULL, `valence` REAL NOT NULL, `danceability` REAL NOT NULL, `spectralCentroidHz` REAL NOT NULL, `dynamicRangeDb` REAL NOT NULL, `mood` TEXT NOT NULL, `beatGridJson` TEXT NOT NULL, `analyzedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_advanced_audio_analysis_mood` ON `advanced_audio_analysis` (`mood`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_advanced_audio_analysis_camelotKey` ON `advanced_audio_analysis` (`camelotKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_advanced_audio_analysis_analyzedAt` ON `advanced_audio_analysis` (`analyzedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `replay_gain_cache` (`songId` INTEGER NOT NULL, `trackGainDb` REAL, `albumGainDb` REAL, `trackPeak` REAL, `r128TrackGainDb` REAL, `source` TEXT NOT NULL, `scannedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `offline_backup_entries` (`songId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `score` REAL NOT NULL, `reason` TEXT NOT NULL, `mood` TEXT NOT NULL, `genre` TEXT NOT NULL, `generatedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_backup_entries_position` ON `offline_backup_entries` (`position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_backup_entries_mood` ON `offline_backup_entries` (`mood`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_backup_entries_generatedAt` ON `offline_backup_entries` (`generatedAt`)")
            }
        }
    }
}
