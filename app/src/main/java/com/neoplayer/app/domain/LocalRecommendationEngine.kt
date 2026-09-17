package com.neoplayer.app.domain

import com.neoplayer.app.data.AudioAnalysisEntity
import com.neoplayer.app.data.ListeningHistoryEntity
import com.neoplayer.app.data.RecommendationFeedbackEntity
import com.neoplayer.app.data.SongEntity
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max


data class LocalRecommendation(
    val song: SongEntity,
    val score: Float,
    val reasons: List<String>
)

/**
 * Deterministic, local-only recommender. It uses metadata, favorites, skip/play behavior and cached
 * BPM analysis. No account, cloud catalog, network request or device identifier participates.
 */
object LocalRecommendationEngine {
    fun recommend(
        songs: List<SongEntity>,
        seed: SongEntity?,
        histories: List<ListeningHistoryEntity>,
        favoriteIds: Set<Long>,
        analyses: List<AudioAnalysisEntity>,
        feedback: List<RecommendationFeedbackEntity>,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 24
    ): List<LocalRecommendation> {
        if (songs.isEmpty()) return emptyList()
        val historyById = histories.associateBy { it.songId }
        val analysisById = analyses.associateBy { it.songId }
        val feedbackById = feedback.associateBy { it.songId }
        val seedAnalysis = seed?.let { analysisById[it.id] }

        val scored = songs.asSequence()
            .filter { it.id != seed?.id }
            .map { song ->
                val reasons = ArrayList<String>(6)
                var score = 0f
                val history = historyById[song.id]
                val localFeedback = feedbackById[song.id]

                if (seed != null) {
                    if (song.artist.equals(seed.artist, ignoreCase = true) && song.artist.isNotBlank()) {
                        score += 32f
                        reasons += "same artist"
                    }
                    if (song.genre.equals(seed.genre, ignoreCase = true) && song.genre.isNotBlank()) {
                        score += 20f
                        reasons += "same genre"
                    }
                    if (song.albumId == seed.albumId && song.albumId > 0L) {
                        score += 7f
                        reasons += "same album"
                    }
                    if (song.year > 0 && seed.year > 0) {
                        val yearGap = abs(song.year - seed.year)
                        val yearScore = (6f - yearGap.coerceAtMost(6)).coerceAtLeast(0f)
                        if (yearScore > 0f) {
                            score += yearScore
                            reasons += "nearby year"
                        }
                    }
                    val durationGap = abs(song.durationMs - seed.durationMs)
                    if (seed.durationMs > 0L && durationGap < 90_000L) {
                        score += 4f * (1f - durationGap / 90_000f)
                        reasons += "similar duration"
                    }

                    val bpm = analysisById[song.id]?.bpm ?: 0f
                    val seedBpm = seedAnalysis?.bpm ?: 0f
                    if (bpm > 0f && seedBpm > 0f) {
                        val bpmGap = abs(bpm - seedBpm)
                        if (bpmGap <= 30f) {
                            score += 16f * (1f - bpmGap / 30f)
                            reasons += "tempo match"
                        }
                    }
                }

                if (song.id in favoriteIds) {
                    score += 15f
                    reasons += "liked"
                }

                if (history == null) {
                    score += 9f
                    reasons += "not played yet"
                } else {
                    score += (ln(1.0 + history.playCount.toDouble()) * 4.5).toFloat().coerceAtMost(12f)
                    val skipPenalty = (history.skipCount * 4f).coerceAtMost(24f)
                    score -= skipPenalty
                    if (history.skipCount > 0) reasons += "skip penalty"

                    if (history.totalListeningMs > 0L) {
                        val completionProxy = (history.totalListeningMs.toDouble() /
                            max(1L, song.durationMs).toDouble()).coerceIn(0.0, 12.0)
                        score += completionProxy.toFloat().coerceAtMost(10f)
                    }

                    if (history.lastPlayedAt > 0L) {
                        val ageHours = (nowMs - history.lastPlayedAt).coerceAtLeast(0L) / 3_600_000f
                        val recentPenalty = when {
                            ageHours < 1f -> 22f
                            ageHours < 6f -> 14f
                            ageHours < 24f -> 8f
                            ageHours < 72f -> 3f
                            else -> 0f
                        }
                        score -= recentPenalty
                        if (recentPenalty > 0f) reasons += "recently played penalty"
                    }
                }

                localFeedback?.let {
                    score += it.boost.coerceIn(-30, 30)
                    score -= (it.dismissCount * 5).coerceAtMost(25)
                    if (it.boost > 0) reasons += "local boost"
                    if (it.lastSeededAt > 0L) {
                        val seedAgeHours = (nowMs - it.lastSeededAt).coerceAtLeast(0L) / 3_600_000f
                        val seedPenalty = when {
                            seedAgeHours < 1f -> 12f
                            seedAgeHours < 6f -> 7f
                            seedAgeHours < 24f -> 3f
                            else -> 0f
                        }
                        score -= seedPenalty
                        if (seedPenalty > 0f) reasons += "recent recommendation penalty"
                    }
                }

                // Stable tiny jitter prevents huge tie blocks without making recommendations random.
                val stableJitter = ((song.id xor (seed?.id ?: 0L)) and 0xFF).toFloat() / 255f
                score += stableJitter
                LocalRecommendation(song, score, reasons.distinct())
            }
            .sortedByDescending { it.score }
            .toList()

        // Diversify with a lightweight MMR-style pass: avoid an endless run of one artist/album.
        val chosen = ArrayList<LocalRecommendation>(limit)
        val artistCounts = HashMap<String, Int>()
        val albumCounts = HashMap<Long, Int>()
        val pool = scored.toMutableList()
        while (chosen.size < limit && pool.isNotEmpty()) {
            var bestIndex = 0
            var bestAdjusted = Float.NEGATIVE_INFINITY
            pool.forEachIndexed { index, candidate ->
                val artistPenalty = (artistCounts[candidate.song.artist] ?: 0) * 7f
                val albumPenalty = (albumCounts[candidate.song.albumId] ?: 0) * 5f
                val adjusted = candidate.score - artistPenalty - albumPenalty
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted
                    bestIndex = index
                }
            }
            val selected = pool.removeAt(bestIndex)
            chosen += selected.copy(score = bestAdjusted)
            artistCounts[selected.song.artist] = (artistCounts[selected.song.artist] ?: 0) + 1
            albumCounts[selected.song.albumId] = (albumCounts[selected.song.albumId] ?: 0) + 1
        }
        return chosen
    }
}
