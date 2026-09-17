package com.neoplayer.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackExperienceDao {
    @Query("SELECT * FROM track_visual_profiles WHERE songId = :songId")
    fun visualProfile(songId: Long): Flow<TrackVisualProfileEntity?>

    @Query("SELECT * FROM track_experience_preferences WHERE id = 0")
    fun preferences(): Flow<TrackExperiencePreferenceEntity?>

    @Upsert
    suspend fun savePreferences(value: TrackExperiencePreferenceEntity)

    @Query("SELECT * FROM track_visual_profiles WHERE songId = :songId")
    suspend fun visualProfileNow(songId: Long): TrackVisualProfileEntity?

    @Upsert
    suspend fun saveVisualProfile(value: TrackVisualProfileEntity)

    @Query("DELETE FROM track_visual_profiles WHERE songId = :songId")
    suspend fun clearVisualProfile(songId: Long)

    @Query("UPDATE songs SET customArtworkUri = :uri WHERE id = :songId")
    suspend fun setSongArtwork(songId: Long, uri: String)

    @Query("SELECT * FROM offline_lyrics_transcripts WHERE songId = :songId")
    fun transcript(songId: Long): Flow<OfflineLyricsTranscriptEntity?>

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun lyricsNow(songId: Long): LyricsEntity?

    @Query("SELECT * FROM offline_lyrics_transcripts WHERE songId = :songId")
    suspend fun transcriptNow(songId: Long): OfflineLyricsTranscriptEntity?

    @Upsert
    suspend fun saveTranscript(value: OfflineLyricsTranscriptEntity)

    @Query("DELETE FROM offline_lyrics_transcripts WHERE songId = :songId")
    suspend fun clearTranscript(songId: Long)

    @Query("SELECT * FROM offline_speech_models ORDER BY importedAt DESC")
    fun speechModels(): Flow<List<OfflineSpeechModelEntity>>

    @Query("SELECT * FROM offline_speech_models WHERE id = :id")
    suspend fun speechModel(id: String): OfflineSpeechModelEntity?

    @Upsert
    suspend fun saveSpeechModel(value: OfflineSpeechModelEntity)

    @Query("DELETE FROM offline_speech_models WHERE id = :id")
    suspend fun deleteSpeechModel(id: String)

    @Query("SELECT * FROM recommendation_feedback")
    suspend fun recommendationFeedbackSnapshot(): List<RecommendationFeedbackEntity>

    @Query("SELECT * FROM recommendation_feedback WHERE songId = :songId")
    suspend fun recommendationFeedback(songId: Long): RecommendationFeedbackEntity?

    @Upsert
    suspend fun saveRecommendationFeedback(value: RecommendationFeedbackEntity)

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    suspend fun allSongsSnapshot(): List<SongEntity>

    @Query("SELECT * FROM listening_history")
    suspend fun historySnapshot(): List<ListeningHistoryEntity>

    @Query("SELECT songId FROM favorites")
    suspend fun favoriteIdsSnapshot(): List<Long>

    @Query("SELECT * FROM audio_analysis")
    suspend fun audioAnalysisSnapshot(): List<AudioAnalysisEntity>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun song(songId: Long): SongEntity?
}
