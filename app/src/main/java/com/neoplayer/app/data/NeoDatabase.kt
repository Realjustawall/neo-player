package com.neoplayer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, FavoriteEntity::class, PlaylistEntity::class, PlaylistSongEntity::class,
        CategoryEntity::class, CategorySongEntity::class, ListeningHistoryEntity::class, LyricsEntity::class,
        ExcludedFolderEntity::class, MetadataOverrideEntity::class, FavoriteCollectionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class NeoDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun create(context: Context): NeoDatabase = Room.databaseBuilder(
            context.applicationContext, NeoDatabase::class.java, "neo-player.db"
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `excluded_folders` (`path` TEXT NOT NULL, PRIMARY KEY(`path`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `metadata_overrides` (`songId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `genre` TEXT NOT NULL, `year` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_collections` (`type` TEXT NOT NULL, `key` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`type`, `key`))")
            }
        }
    }
}
