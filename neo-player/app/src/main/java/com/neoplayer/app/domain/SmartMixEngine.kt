package com.neoplayer.app.domain

import com.neoplayer.app.data.ListeningHistoryEntity
import com.neoplayer.app.data.SongEntity
import java.util.Calendar

object SmartMixEngine {
    fun mostPlayed(songs: List<SongEntity>, history: List<ListeningHistoryEntity>, limit: Int = 100): List<SongEntity> {
        val rank = history.associateBy { it.songId }
        return songs.filter { rank[it.id]?.playCount ?: 0 > 0 }
            .sortedWith(compareByDescending<SongEntity> { rank[it.id]?.playCount ?: 0 }.thenByDescending { rank[it.id]?.totalListeningMs ?: 0 })
            .take(limit)
    }

    fun recentlyPlayed(songs: List<SongEntity>, history: List<ListeningHistoryEntity>, limit: Int = 100): List<SongEntity> {
        val rank = history.associateBy { it.songId }
        return songs.filter { rank[it.id]?.lastPlayedAt ?: 0 > 0 }.sortedByDescending { rank[it.id]?.lastPlayedAt }.take(limit)
    }

    fun neverPlayed(songs: List<SongEntity>, history: List<ListeningHistoryEntity>): List<SongEntity> {
        val played = history.filter { it.playCount > 0 }.mapTo(HashSet()) { it.songId }
        return songs.filterNot { it.id in played }
    }

    fun forgottenFavorites(songs: List<SongEntity>, favoriteIds: Set<Long>, history: List<ListeningHistoryEntity>, now: Long = System.currentTimeMillis()): List<SongEntity> {
        val cutoff = now - 45L * 24 * 60 * 60 * 1000
        val rank = history.associateBy { it.songId }
        return songs.filter { it.id in favoriteIds && (rank[it.id]?.lastPlayedAt ?: 0) < cutoff }
    }

    fun decade(songs: List<SongEntity>, decade: Int): List<SongEntity> = songs.filter { it.year in decade..(decade + 9) }

    fun timeOfDay(songs: List<SongEntity>, favoriteIds: Set<Long>, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): List<SongEntity> {
        val preferred = if (hour >= 20 || hour < 6) setOf("ambient", "classical", "jazz", "relax") else setOf("pop", "rock", "electronic", "dance")
        return songs.sortedByDescending { (if (it.id in favoriteIds) 2 else 0) + (if (preferred.any { word -> it.genre.contains(word, true) }) 1 else 0) }
    }
}
