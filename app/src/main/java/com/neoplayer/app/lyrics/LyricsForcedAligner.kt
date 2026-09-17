package com.neoplayer.app.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray

/**
 * Offline forced alignment for a user-supplied/reference lyric and a Vosk word-timestamp stream.
 * It never calls a network service. Matching is monotonic and tolerant of punctuation, repeated
 * choruses and common Persian/Arabic Unicode variants. When a line cannot be matched confidently,
 * timing is interpolated between surrounding recognized speech rather than dropping the line.
 */
object LyricsForcedAligner {
    data class Result(
        val lrc: String,
        val matchedLines: Int,
        val totalLines: Int,
        val averageConfidence: Float
    )

    private data class Word(val raw: String, val token: String, val startMs: Long, val endMs: Long)
    private data class Line(val raw: String, val tokens: List<String>)
    private data class Match(val index: Int, val score: Float)

    fun align(referenceLyrics: String, wordTimedJson: String, durationMs: Long): Result {
        val words = parseWords(wordTimedJson)
        val lines = referenceLyrics.lineSequence()
            .map(::stripLrcTimestamp)
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { Line(it, tokenize(it)) }
            .filter { it.tokens.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return Result("", 0, 0, 0f)
        if (words.isEmpty()) return Result(proportionalFallback(lines, durationMs), 0, lines.size, 0f)

        val starts = LongArray(lines.size)
        val scores = FloatArray(lines.size)
        var cursor = 0
        var matched = 0
        lines.forEachIndexed { lineIndex, line ->
            val remainingLines = (lines.size - lineIndex).coerceAtLeast(1)
            val reserve = (remainingLines - 1).coerceAtLeast(0)
            val searchEnd = min(words.lastIndex, cursor + max(SEARCH_AHEAD_WORDS, line.tokens.size * 12))
            val match = bestMatch(line.tokens, words, cursor.coerceAtMost(words.lastIndex), searchEnd)
            if (match != null && match.score >= MIN_ACCEPT_SCORE) {
                starts[lineIndex] = words[match.index].startMs
                scores[lineIndex] = match.score
                matched++
                cursor = (match.index + max(1, line.tokens.size / 2)).coerceAtMost((words.size - reserve - 1).coerceAtLeast(0))
            } else {
                val previous = starts.getOrNull(lineIndex - 1)?.takeIf { lineIndex > 0 } ?: words.first().startMs
                val futureWord = words.getOrNull(cursor)?.startMs ?: words.last().endMs
                val remainingDuration = (durationMs.takeIf { it > 0 } ?: words.last().endMs).coerceAtLeast(futureWord) - previous
                starts[lineIndex] = previous + (remainingDuration / remainingLines).coerceAtLeast(450L)
                scores[lineIndex] = 0f
            }
        }

        // Preserve strict monotonicity so repeated choruses never make the lyric scroller jump back.
        for (i in starts.indices) {
            val floor = if (i == 0) 0L else starts[i - 1] + MIN_LINE_GAP_MS
            starts[i] = starts[i].coerceAtLeast(floor)
        }
        val cap = (durationMs.takeIf { it > 0 } ?: words.last().endMs).coerceAtLeast(1L)
        for (i in starts.indices) starts[i] = starts[i].coerceAtMost(cap)

        val lrc = lines.indices.joinToString("\n") { i -> "[${formatLrc(starts[i])}]${lines[i].raw}" }
        return Result(lrc, matched, lines.size, if (lines.isEmpty()) 0f else scores.average().toFloat())
    }

    private fun bestMatch(tokens: List<String>, words: List<Word>, from: Int, to: Int): Match? {
        if (tokens.isEmpty() || words.isEmpty() || from > to) return null
        var best: Match? = null
        val expected = tokens.size.coerceAtLeast(1)
        for (start in from..to) {
            val maxWindow = min(words.size - start, expected + max(6, expected / 2))
            if (maxWindow <= 0) continue
            val score = localSequenceScore(tokens, words, start, maxWindow)
            val current = best
            if (current == null || score > current.score) best = Match(start, score)
            if (score >= EARLY_EXIT_SCORE) break
        }
        return best
    }

    /** Small local alignment DP: match=+2, near/fuzzy=+1, mismatch/gap penalties. */
    private fun localSequenceScore(tokens: List<String>, words: List<Word>, start: Int, count: Int): Float {
        val n = tokens.size
        val m = count
        val prev = FloatArray(m + 1)
        val cur = FloatArray(m + 1)
        var best = 0f
        for (i in 1..n) {
            cur[0] = 0f
            for (j in 1..m) {
                val a = tokens[i - 1]
                val b = words[start + j - 1].token
                val similarity = tokenSimilarity(a, b)
                val diagonal = prev[j - 1] + when {
                    similarity >= .99f -> 2f
                    similarity >= .66f -> 1.15f
                    similarity >= .45f -> .45f
                    else -> -1.25f
                }
                val skipReference = prev[j] - .65f
                val skipRecognition = cur[j - 1] - .45f
                cur[j] = max(0f, max(diagonal, max(skipReference, skipRecognition)))
                best = max(best, cur[j])
            }
            for (j in 0..m) prev[j] = cur[j]
            java.util.Arrays.fill(cur, 0f)
        }
        return (best / (n * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    private fun tokenSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) return .82f
        val distance = levenshteinLimited(a, b, 2)
        val longest = max(a.length, b.length).coerceAtLeast(1)
        return (1f - distance.toFloat() / longest).coerceIn(0f, 1f)
    }

    private fun levenshteinLimited(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(previous[j] + 1, min(current[j - 1] + 1, previous[j - 1] + cost))
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }

    private fun parseWords(json: String): List<Word> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val raw = item.optString("word").trim()
                val token = normalizeToken(raw)
                if (token.isBlank()) continue
                add(Word(raw, token, item.optLong("startMs").coerceAtLeast(0L), item.optLong("endMs").coerceAtLeast(0L)))
            }
        }.sortedBy { it.startMs }
    }.getOrDefault(emptyList())

    private fun tokenize(value: String): List<String> = value.split(Regex("\\s+"))
        .map(::normalizeToken)
        .filter(String::isNotBlank)

    private fun normalizeToken(value: String): String {
        val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
            .replace("ۀ", "ه").replace("ة", "ه")
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        return normalized.filter { it.isLetterOrDigit() || it == '\'' }
    }

    private fun stripLrcTimestamp(line: String): String = line.replace(Regex("^(?:\\[[^]]+])+") , "")

    private fun proportionalFallback(lines: List<Line>, durationMs: Long): String {
        val duration = durationMs.coerceAtLeast(lines.size * 1_000L)
        return lines.mapIndexed { index, line ->
            val at = duration * index / lines.size.coerceAtLeast(1)
            "[${formatLrc(at)}]${line.raw}"
        }.joinToString("\n")
    }

    private fun formatLrc(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val hundredths = (safe % 1_000L) / 10L
        return "%02d:%02d.%02d".format(Locale.ROOT, minutes, seconds, hundredths)
    }

    private const val SEARCH_AHEAD_WORDS = 140
    private const val MIN_ACCEPT_SCORE = .30f
    private const val EARLY_EXIT_SCORE = .88f
    private const val MIN_LINE_GAP_MS = 90L
}
