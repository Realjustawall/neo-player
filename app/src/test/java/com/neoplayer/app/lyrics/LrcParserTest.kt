package com.neoplayer.app.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test fun parsesMultipleTimestampPrecisionsAndSorts() {
        val result = LrcParser.parse("[01:02.50]Second\n[00:01.005]First\n[02:03]Third")
        assertEquals(listOf(1_005L, 62_500L, 123_000L), result.map { it.timeMs })
        assertEquals(listOf("First", "Second", "Third"), result.map { it.text })
    }

    @Test fun supportsMultipleTimestampsOnOneLine() {
        val result = LrcParser.parse("[00:01.00][00:02.00]Again")
        assertEquals(2, result.size)
        assertEquals("Again", result.last().text)
    }

    @Test fun findsActiveLine() {
        val lines = listOf(LyricLine(1_000, "A"), LyricLine(2_000, "B"))
        assertEquals(0, LrcParser.activeIndex(lines, 1_500))
        assertEquals(1, LrcParser.activeIndex(lines, 2_500))
    }
}
