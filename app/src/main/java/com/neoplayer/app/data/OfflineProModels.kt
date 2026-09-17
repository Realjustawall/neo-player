package com.neoplayer.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "advanced_audio_analysis", indices = [Index("mood"), Index("camelotKey"), Index("analyzedAt")])
data class AdvancedAudioAnalysisEntity(
    @PrimaryKey val songId: Long,
    val bpm: Float = 0f,
    val beatIntervalMs: Float = 0f,
    val beatOffsetMs: Float = 0f,
    val beatConfidence: Float = 0f,
    val phraseLengthBeats: Int = 16,
    val phraseOffsetMs: Float = 0f,
    val phraseConfidence: Float = 0f,
    val musicalKey: String = "",
    val camelotKey: String = "",
    val keyConfidence: Float = 0f,
    val energy: Float = 0f,
    val valence: Float = .5f,
    val danceability: Float = 0f,
    val spectralCentroidHz: Float = 0f,
    val dynamicRangeDb: Float = 0f,
    val mood: String = "unknown",
    val beatGridJson: String = "[]",
    val analyzedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "replay_gain_cache")
data class ReplayGainEntity(
    @PrimaryKey val songId: Long,
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val r128TrackGainDb: Float? = null,
    val source: String = "none",
    val scannedAt: Long = System.currentTimeMillis()
) {
    val preferredGainDb: Float? get() = r128TrackGainDb ?: trackGainDb ?: albumGainDb
}

@Entity(tableName = "offline_backup_entries", indices = [Index("position"), Index("mood"), Index("generatedAt")])
data class OfflineBackupEntryEntity(
    @PrimaryKey val songId: Long,
    val position: Int,
    val score: Float,
    val reason: String,
    val mood: String = "unknown",
    val genre: String = "",
    val generatedAt: Long = System.currentTimeMillis()
)
