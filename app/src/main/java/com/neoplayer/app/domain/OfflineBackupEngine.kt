package com.neoplayer.app.domain

import com.neoplayer.app.data.AdvancedAudioAnalysisEntity
import com.neoplayer.app.data.ListeningHistoryEntity
import com.neoplayer.app.data.OfflineBackupEntryEntity
import com.neoplayer.app.data.RecommendationFeedbackEntity
import com.neoplayer.app.data.SongEntity
import java.util.Locale
import kotlin.math.ln

/** Deterministic local-only equivalent of an Offline Backup playlist. */
object OfflineBackupEngine {
    fun generate(
        songs: List<SongEntity>,
        histories: List<ListeningHistoryEntity>,
        favorites: Set<Long>,
        advanced: List<AdvancedAudioAnalysisEntity>,
        feedback: List<RecommendationFeedbackEntity>,
        hidden: Set<Long>,
        moodFilter: String = "all",
        genreFilter: String = "all",
        limit: Int = 100,
        nowMs: Long = System.currentTimeMillis()
    ): List<OfflineBackupEntryEntity> {
        val historyById = histories.associateBy { it.songId }
        val advancedById = advanced.associateBy { it.songId }
        val feedbackById = feedback.associateBy { it.songId }
        val mood = moodFilter.trim().lowercase(Locale.ROOT)
        val genre = genreFilter.trim().lowercase(Locale.ROOT)
        val generatedAt = nowMs

        return songs.asSequence()
            .filterNot { it.id in hidden }
            .filter { song -> mood == "all" || advancedById[song.id]?.mood?.lowercase(Locale.ROOT) == mood }
            .filter { song -> genre == "all" || song.genre.lowercase(Locale.ROOT) == genre }
            .map { song ->
                val h = historyById[song.id]
                val a = advancedById[song.id]
                val f = feedbackById[song.id]
                var score = 0f
                val reasons = ArrayList<String>(6)
                if (song.id in favorites) { score += 18f; reasons += "liked" }
                if (h == null) {
                    score += 8f; reasons += "fresh"
                } else {
                    val ageDays = if (h.lastPlayedAt > 0L) ((nowMs - h.lastPlayedAt).coerceAtLeast(0L) / 86_400_000f) else 999f
                    val recency = (22f - ageDays.coerceAtMost(22f)).coerceAtLeast(0f)
                    score += recency
                    if (recency > 8f) reasons += "recent"
                    score += (ln(1.0 + h.playCount.toDouble()) * 5.0).toFloat().coerceAtMost(15f)
                    score -= (h.skipCount * 3.5f).coerceAtMost(28f)
                    if (h.skipCount == 0 && h.playCount > 0) { score += 4f; reasons += "completed often" }
                }
                if (a != null) {
                    score += a.danceability * 4f + a.beatConfidence * 3f
                    if (a.mood != "unknown") reasons += a.mood
                }
                if (f != null) {
                    score += f.boost.toFloat()
                    score -= (f.dismissCount * 5f).coerceAtMost(30f)
                }
                if (song.dateAdded > 0L) {
                    val addedAgeDays = ((nowMs / 1000L - song.dateAdded).coerceAtLeast(0L) / 86_400f)
                    if (addedAgeDays < 14f) { score += 5f; reasons += "recently added" }
                }
                Triple(song, score, reasons.distinct())
            }
            .sortedWith(compareByDescending<Triple<SongEntity, Float, List<String>>> { it.second }.thenBy { it.first.title.lowercase(Locale.ROOT) })
            .take(limit.coerceIn(20, 500))
            .mapIndexed { index, (song, score, reasons) ->
                OfflineBackupEntryEntity(
                    songId = song.id,
                    position = index,
                    score = score,
                    reason = reasons.take(4).joinToString(" • ").ifBlank { "local library" },
                    mood = advancedById[song.id]?.mood ?: "unknown",
                    genre = song.genre,
                    generatedAt = generatedAt
                )
            }.toList()
    }
}
