package com.neoplayer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SongEntity::class, FavoriteEntity::class, PlaylistEntity::class, PlaylistSongEntity::class,
        CategoryEntity::class, CategorySongEntity::class, ListeningHistoryEntity::class, LyricsEntity::class],
    version = 1,
    exportSchema = true
)
abstract class NeoDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun create(context: Context): NeoDatabase = Room.databaseBuilder(
            context.applicationContext, NeoDatabase::class.java, "neo-player.db"
        ).fallbackToDestructiveMigration().build()
    }
}
