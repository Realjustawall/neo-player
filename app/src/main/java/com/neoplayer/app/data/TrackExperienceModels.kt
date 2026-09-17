package com.neoplayer.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Additive per-track visual state. Audio metadata and the original UI remain untouched; these
 * values are only applied by the enhanced player surface unless custom artwork is also mirrored
 * into songs.customArtworkUri for compatibility with every existing artwork consumer.
 */
@Entity(tableName = "track_visual_profiles")
data class TrackVisualProfileEntity(
    @PrimaryKey val songId: Long,
    val canvasUri: String? = null,
    val canvasEnabled: Boolean = false,
    val canvasFit: String = "crop",
    @ColumnInfo(defaultValue = "0")
    val canvasStartMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val canvasEndMs: Long = 0L,
    @ColumnInfo(defaultValue = "1.0")
    val canvasPlaybackSpeed: Float = 1f,
    val themeMode: String = "inherit",
    val accentArgb: Int = 0,
    val backgroundArgb: Int = 0,
    val secondaryArgb: Int = 0,
    val visualizerMode: String = "waveform",
    val visualizerSensitivity: Float = 1f,
    val animationIntensity: Float = 1f,
    /** Independent wallpaper/background; separate from album/song artwork and Canvas video. */
    @ColumnInfo(defaultValue = "''")
    val backgroundImageUri: String = "",
    @ColumnInfo(defaultValue = "'overlay'")
    val backgroundMode: String = "overlay",
    @ColumnInfo(defaultValue = "0.28")
    val backgroundOpacity: Float = .28f,
    @ColumnInfo(defaultValue = "18")
    val backgroundBlurDp: Int = 18,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Word-level transcription cache used by the karaoke renderer and the existing LRC layer. */
@Entity(tableName = "offline_lyrics_transcripts", indices = [Index("generatedAt")])
data class OfflineLyricsTranscriptEntity(
    @PrimaryKey val songId: Long,
    val engine: String = "vosk",
    val modelId: String = "bundled-en",
    val language: String = "en",
    val plainText: String = "",
    val lrcText: String = "",
    val wordTimedJson: String = "[]",
    val averageConfidence: Float = 0f,
    val generatedAt: Long = System.currentTimeMillis()
)

/** Imported Vosk model packs are copied to app-private storage, so transcription stays offline. */
@Entity(tableName = "offline_speech_models", indices = [Index("language"), Index("importedAt")])
data class OfflineSpeechModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val language: String,
    val localPath: String,
    val sampleRate: Int = 16_000,
    val importedAt: Long = System.currentTimeMillis()
)

/** Optional local feedback for recommendation tuning. Existing favorites/history remain primary. */
@Entity(tableName = "recommendation_feedback")
data class RecommendationFeedbackEntity(
    @PrimaryKey val songId: Long,
    val boost: Int = 0,
    val dismissCount: Int = 0,
    val lastSeededAt: Long = 0L
)

/** Small singleton preference row so the chosen offline speech model survives process restarts. */
@Entity(tableName = "track_experience_preferences")
data class TrackExperiencePreferenceEntity(
    @PrimaryKey val id: Int = 0,
    val selectedSpeechModelId: String = "bundled-en",
    val selectedSpeechLanguage: String = "en"
)
