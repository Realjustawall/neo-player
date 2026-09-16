package com.neoplayer.app.domain

import com.neoplayer.app.data.ListeningHistoryEntity
import com.neoplayer.app.data.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartMixEngineTest {
    private fun song(id: Long, year: Int = 2020, genre: String = "") = SongEntity(
        id, "content://$id", "Song $id", "Artist", "Album", 1, "", genre, year,
        180_000, 1, 1, "", 320_000, "audio/mpeg", 1_000, "Music", 1, 1
    )

    @Test fun mostPlayedUsesPlayCountThenListeningTime() {
        val songs = listOf(song(1), song(2))
        val history = listOf(ListeningHistoryEntity(1, playCount = 2), ListeningHistoryEntity(2, playCount = 4))
        assertEquals(listOf(2L, 1L), SmartMixEngine.mostPlayed(songs, history).map { it.id })
    }

    @Test fun neverPlayedExcludesTracksWithMeaningfulPlay() {
        val songs = listOf(song(1), song(2))
        assertEquals(listOf(1L), SmartMixEngine.neverPlayed(songs, listOf(ListeningHistoryEntity(2, playCount = 1))).map { it.id })
    }

    @Test fun decadeUsesMetadataYear() {
        assertEquals(listOf(1L), SmartMixEngine.decade(listOf(song(1, 1994), song(2, 2001)), 1990).map { it.id })
    }
}
