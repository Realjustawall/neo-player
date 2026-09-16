package com.neoplayer.app.lyrics

import com.neoplayer.app.data.SongEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsProviderRegistryTest {
    private val song = SongEntity(1, "content://1", "Title", "Artist", "Album", 1, "", "", 2024, 1, 1, 1, "", 0, "audio/mpeg", 1, "Music", 1, 1)

    @Test fun triesProvidersInPriorityOrderAndReturnsFirstMatch() = runTest {
        val miss = object : LyricsProvider { override val id = "miss"; override suspend fun find(song: SongEntity) = null }
        val hit = object : LyricsProvider { override val id = "hit"; override suspend fun find(song: SongEntity) = LyricsPayload("lyrics", source = id) }
        assertEquals("hit", LyricsProviderRegistry(listOf(miss, hit)).find(song)?.source)
    }
}
