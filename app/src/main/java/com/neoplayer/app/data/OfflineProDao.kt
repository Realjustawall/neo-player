package com.neoplayer.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineProDao {
    @Query("SELECT * FROM advanced_audio_analysis WHERE songId = :songId") fun advancedAnalysis(songId: Long): Flow<AdvancedAudioAnalysisEntity?>
    @Query("SELECT * FROM advanced_audio_analysis WHERE songId = :songId") suspend fun advancedAnalysisNow(songId: Long): AdvancedAudioAnalysisEntity?
    @Query("SELECT * FROM advanced_audio_analysis ORDER BY analyzedAt DESC") fun advancedAnalyses(): Flow<List<AdvancedAudioAnalysisEntity>>
    @Query("SELECT * FROM advanced_audio_analysis") suspend fun advancedAnalysisSnapshot(): List<AdvancedAudioAnalysisEntity>
    @Upsert suspend fun saveAdvancedAnalysis(value: AdvancedAudioAnalysisEntity)
    @Query("DELETE FROM advanced_audio_analysis WHERE songId = :songId") suspend fun clearAdvancedAnalysis(songId: Long)
    @Query("DELETE FROM advanced_audio_analysis") suspend fun clearAllAdvancedAnalysis()

    @Query("SELECT * FROM replay_gain_cache ORDER BY scannedAt DESC") fun replayGains(): Flow<List<ReplayGainEntity>>
    @Query("SELECT * FROM replay_gain_cache WHERE songId = :songId") suspend fun replayGain(songId: Long): ReplayGainEntity?
    @Upsert suspend fun saveReplayGain(value: ReplayGainEntity)
    @Query("DELETE FROM replay_gain_cache WHERE songId = :songId") suspend fun clearReplayGain(songId: Long)
    @Query("DELETE FROM replay_gain_cache") suspend fun clearAllReplayGain()

    @Query("SELECT * FROM offline_backup_entries ORDER BY position") fun offlineBackup(): Flow<List<OfflineBackupEntryEntity>>
    @Query("DELETE FROM offline_backup_entries") suspend fun clearOfflineBackup()
    @Upsert suspend fun saveOfflineBackupEntries(values: List<OfflineBackupEntryEntity>)
    @Transaction suspend fun replaceOfflineBackup(values: List<OfflineBackupEntryEntity>) { clearOfflineBackup(); if (values.isNotEmpty()) saveOfflineBackupEntries(values) }
}
