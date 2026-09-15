package com.neoplayer.app.lyrics

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(input: String): List<LyricLine> = buildList {
        input.lineSequence().forEach { raw ->
            val matches = timestamp.findAll(raw).toList()
            val text = timestamp.replace(raw, "").trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0
                val seconds = match.groupValues[2].toLongOrNull() ?: 0
                val fractionText = match.groupValues[3]
                val millis = when (fractionText.length) { 1 -> fractionText.toLongOrNull()?.times(100); 2 -> fractionText.toLongOrNull()?.times(10); 3 -> fractionText.toLongOrNull(); else -> 0 } ?: 0
                add(LyricLine((minutes * 60 + seconds) * 1000 + millis, text))
            }
        }
    }.sortedBy { it.timeMs }

    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int =
        lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)

    fun stampLine(input: String, lineIndex: Int, positionMs: Long): String {
        val lines = input.lines().toMutableList()
        if (lineIndex !in lines.indices) return input
        val totalCentiseconds = positionMs.coerceAtLeast(0) / 10
        val minutes = totalCentiseconds / 6000
        val seconds = (totalCentiseconds / 100) % 60
        val fraction = totalCentiseconds % 100
        val clean = timestamp.replace(lines[lineIndex], "").trim()
        lines[lineIndex] = "[%02d:%02d.%02d]%s".format(minutes, seconds, fraction, clean)
        return lines.joinToString("\n")
    }
}
