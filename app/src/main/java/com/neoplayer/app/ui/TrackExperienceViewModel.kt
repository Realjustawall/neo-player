package com.neoplayer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neoplayer.app.NeoApplication
import com.neoplayer.app.data.LyricsEntity
import com.neoplayer.app.data.OfflineLyricsTranscriptEntity
import com.neoplayer.app.data.OfflineSpeechModelEntity
import com.neoplayer.app.data.RecommendationFeedbackEntity
import com.neoplayer.app.data.SongEntity
import com.neoplayer.app.data.TrackVisualProfileEntity
import com.neoplayer.app.data.TrackExperiencePreferenceEntity
import com.neoplayer.app.domain.LocalRecommendation
import com.neoplayer.app.domain.LocalRecommendationEngine
import com.neoplayer.app.lyrics.LyricsForcedAligner
import com.neoplayer.app.lyrics.OfflineLyricsTranscriber
import com.neoplayer.app.lyrics.OfflineSpeechModelManager
import com.neoplayer.app.lyrics.OfflineTranscriptionProgress
import com.neoplayer.app.visual.ArtworkThemeExtractor
import com.neoplayer.app.visual.WaveformAnalyzer
import com.neoplayer.app.visual.WaveformData
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class TranscriptionUiState(
    val running: Boolean = false,
    val progress: OfflineTranscriptionProgress = OfflineTranscriptionProgress(),
    val error: String? = null
)

/**
 * Additive controller for Canvas, per-track artwork/theme, word-level offline lyrics, waveform and
 * the local recommendation engine. It never replaces MainViewModel or NeoPlusViewModel.
 */
class TrackExperienceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NeoApplication
    private val dao = app.database.trackExperienceDao()
    private val repository = app.repository
    private val transcriber = OfflineLyricsTranscriber(application)
    private val modelManager = OfflineSpeechModelManager(application)
    private val waveformAnalyzer = WaveformAnalyzer(application)
    private val themeExtractor = ArtworkThemeExtractor(application)

    val playback = app.playback.state
    val settings = app.settings.values.stateIn(viewModelScope, SharingStarted.Eagerly, app.initialSettings)
    val songs = repository.rawLibrary.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val speechModels = dao.speechModels().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSong: StateFlow<SongEntity?> = combine(songs, playback) { library, state ->
        val id = state.current?.mediaId?.toLongOrNull()
        library.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentProfile: StateFlow<TrackVisualProfileEntity?> = currentSong
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else dao.visualProfile(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentTranscript: StateFlow<OfflineLyricsTranscriptEntity?> = currentSong
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else dao.transcript(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Existing/manual/provider lyrics remain separate from the AI transcript. */
    val currentClassicLyrics: StateFlow<LyricsEntity?> = currentSong
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.lyrics(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val forcedAlignmentStatus = MutableStateFlow<String?>(null)

    val waveform = MutableStateFlow<WaveformData?>(null)
    val waveformLoading = MutableStateFlow(false)
    val transcription = MutableStateFlow(TranscriptionUiState())
    val recommendations = MutableStateFlow<List<LocalRecommendation>>(emptyList())
    val selectedModelId = MutableStateFlow(OfflineLyricsTranscriber.BUNDLED_MODEL_ID)
    val selectedLanguage = MutableStateFlow("en")
    private var transcriptionJob: Job? = null
    private var waveformJob: Job? = null

    init {
        viewModelScope.launch {
            dao.preferences().collect { saved ->
                if (saved != null) {
                    selectedModelId.value = saved.selectedSpeechModelId
                    selectedLanguage.value = saved.selectedSpeechLanguage
                }
            }
        }

        viewModelScope.launch {
            currentSong.collectLatest { song ->
                waveformJob?.cancel()
                waveform.value = null
                waveformLoading.value = false
                if (song != null) {
                    // Never decode a waveform just because playback advanced. Only restore an
                    // already-cached waveform here; expensive analysis starts when Track+ opens.
                    waveform.value = waveformAnalyzer.loadCached(song)
                }
            }
        }

        viewModelScope.launch {
            combine(
                repository.rawLibrary,
                repository.histories,
                repository.favorites,
                repository.hiddenSongIds().map { it.toSet() },
                currentSong
            ) { library, history, favorites, hidden, seed ->
                RecommendationInput(library, history, favorites.toSet(), hidden, seed)
            }.collectLatest { input ->
                // History and MediaStore may emit in bursts. Collapse them so recommendation work
                // never competes with scrolling or playback transitions.
                delay(250L)
                refreshRecommendations(input)
            }
        }
    }

    private data class RecommendationInput(
        val library: List<SongEntity>,
        val history: List<com.neoplayer.app.data.ListeningHistoryEntity>,
        val favorites: Set<Long>,
        val hidden: Set<Long>,
        val seed: SongEntity?
    )

    private suspend fun refreshRecommendations(input: RecommendationInput) {
        val analyses = withContext(Dispatchers.IO) { dao.audioAnalysisSnapshot() }
        val feedback = withContext(Dispatchers.IO) { dao.recommendationFeedbackSnapshot() }
        recommendations.value = withContext(Dispatchers.Default) {
            LocalRecommendationEngine.recommend(
                songs = input.library.filterNot { it.id in input.hidden },
                seed = input.seed,
                histories = input.history,
                favoriteIds = input.favorites,
                analyses = analyses,
                feedback = feedback,
                limit = 30
            )
        }
    }

    fun setCanvas(uri: String?) = updateProfile { current ->
        current.copy(canvasUri = uri, canvasEnabled = !uri.isNullOrBlank(), updatedAt = System.currentTimeMillis())
    }

    fun setCanvasEnabled(enabled: Boolean) = updateProfile { current ->
        current.copy(canvasEnabled = enabled, updatedAt = System.currentTimeMillis())
    }

    fun setCanvasFit(mode: String) = updateProfile { current ->
        current.copy(canvasFit = if (mode == "fit") "fit" else "crop", updatedAt = System.currentTimeMillis())
    }

    fun setVisualizerMode(mode: String) = updateProfile { current ->
        current.copy(visualizerMode = mode.takeIf { it in setOf("waveform", "bars", "pulse", "off") } ?: "waveform")
    }

    fun setVisualizerSensitivity(value: Float) = updateProfile { current ->
        current.copy(visualizerSensitivity = value.coerceIn(.25f, 3f))
    }

    fun setAnimationIntensity(value: Float) = updateProfile { current ->
        current.copy(animationIntensity = value.coerceIn(0f, 2f))
    }

    fun setTheme(mode: String, accentArgb: Int, backgroundArgb: Int, secondaryArgb: Int) = updateProfile { current ->
        current.copy(
            themeMode = mode,
            accentArgb = accentArgb,
            backgroundArgb = backgroundArgb,
            secondaryArgb = secondaryArgb,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun resetTheme() = updateProfile { current ->
        current.copy(themeMode = "inherit", accentArgb = 0, backgroundArgb = 0, secondaryArgb = 0)
    }

    fun extractThemeFromArtwork() {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            val result = themeExtractor.extract(Uri.parse(song.artworkUri)) ?: return@launch
            setTheme("custom", result.accentArgb, result.backgroundArgb, result.secondaryArgb)
        }
    }

    fun setArtwork(uri: String?) {
        val song = currentSong.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.setSongArtwork(song.id, uri.orEmpty())
        }
    }

    fun clearVisualProfile() {
        val song = currentSong.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearVisualProfile(song.id)
            dao.setSongArtwork(song.id, "")
        }
    }

    private fun updateProfile(transform: (TrackVisualProfileEntity) -> TrackVisualProfileEntity) {
        val song = currentSong.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.visualProfileNow(song.id) ?: TrackVisualProfileEntity(song.id)
            dao.saveVisualProfile(transform(existing))
        }
    }

    fun persistUriPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun ensureWaveform(song: SongEntity? = currentSong.value) {
        val target = song ?: return
        if (waveform.value?.songId == target.id || waveformLoading.value) return
        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            waveformLoading.value = true
            try {
                waveform.value = waveformAnalyzer.analyzeAndCache(target)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                waveform.value = null
            } finally {
                waveformLoading.value = false
            }
        }
    }

    fun rebuildWaveform(song: SongEntity? = currentSong.value) {
        val target = song ?: return
        waveform.value = null
        ensureWaveform(target)
    }

    fun chooseBuiltInEnglish() = selectSpeechModel(OfflineLyricsTranscriber.BUNDLED_ENGLISH_MODEL_ID, "en")

    fun chooseBuiltInPersian() = selectSpeechModel(OfflineLyricsTranscriber.BUNDLED_PERSIAN_MODEL_ID, "fa")

    fun chooseSpeechModel(model: OfflineSpeechModelEntity) = selectSpeechModel(model.id, model.language)

    private fun selectSpeechModel(id: String, language: String) {
        selectedModelId.value = id
        selectedLanguage.value = language
        viewModelScope.launch(Dispatchers.IO) {
            dao.savePreferences(TrackExperiencePreferenceEntity(selectedSpeechModelId = id, selectedSpeechLanguage = language))
        }
    }

    fun importSpeechModel(zipUri: Uri, displayName: String, language: String) {
        persistUriPermission(zipUri)
        viewModelScope.launch {
            try {
                val model = modelManager.importZip(zipUri, displayName, language)
                dao.saveSpeechModel(model)
                selectSpeechModel(model.id, model.language)
            } catch (error: Throwable) {
                transcription.value = transcription.value.copy(error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun deleteSpeechModel(model: OfflineSpeechModelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.delete(model)
            dao.deleteSpeechModel(model.id)
            if (selectedModelId.value == model.id) {
                selectSpeechModel(OfflineLyricsTranscriber.BUNDLED_ENGLISH_MODEL_ID, "en")
            }
        }
    }

    fun generateLyricsOffline() {
        if (transcriptionJob?.isActive == true) return
        val song = currentSong.value ?: return
        transcriptionJob = viewModelScope.launch {
            transcription.value = TranscriptionUiState(running = true)
            try {
                val modelId = selectedModelId.value
                val builtIn = modelId == OfflineLyricsTranscriber.BUNDLED_ENGLISH_MODEL_ID ||
                    modelId == OfflineLyricsTranscriber.BUNDLED_PERSIAN_MODEL_ID
                val model = if (builtIn) null else dao.speechModel(modelId)
                if (!builtIn && model == null) error("Selected offline speech model is unavailable")
                val transcript = transcriber.transcribe(
                    song = song,
                    model = model,
                    bundledModelId = modelId,
                    language = selectedLanguage.value,
                    allowBuiltInModelDownload = !settings.value.strictOfflineMode
                ) { progress -> transcription.value = TranscriptionUiState(running = true, progress = progress) }
                dao.saveTranscript(transcript)
                // Never overwrite a user's existing manual/provider lyrics implicitly. The classic
                // Lyrics surface receives generated LRC automatically only when it has no content
                // (or when the previous value was itself generated by this offline engine).
                val existingLyrics = dao.lyricsNow(song.id)
                if (transcript.lrcText.isNotBlank() && (existingLyrics == null || existingLyrics.source.startsWith("offline-vosk"))) {
                    repository.saveLyrics(
                        LyricsEntity(
                            songId = song.id,
                            original = transcript.lrcText,
                            synchronized = true,
                            source = "offline-${transcript.engine}"
                        )
                    )
                }
                transcription.value = TranscriptionUiState(
                    running = false,
                    progress = OfflineTranscriptionProgress(1f, song.durationMs, song.durationMs, transcript.plainText.takeLast(180))
                )
            } catch (cancelled: CancellationException) {
                transcription.value = transcription.value.copy(running = false)
                throw cancelled
            } catch (error: Throwable) {
                transcription.value = TranscriptionUiState(running = false, error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun cancelTranscription() {
        transcriptionJob?.cancel()
        transcriptionJob = null
        transcription.value = transcription.value.copy(running = false)
    }

    fun clearGeneratedLyrics() {
        val song = currentSong.value ?: return
        viewModelScope.launch(Dispatchers.IO) { dao.clearTranscript(song.id) }
    }

    /**
     * Explicit-only operation: align the user's/reference lyric text to locally recognized words.
     * Nothing is overwritten until this method is called from the UI.
     */
    fun forceAlignExistingLyrics() {
        val song = currentSong.value ?: return
        val transcript = currentTranscript.value ?: return
        val reference = currentClassicLyrics.value?.original?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val result = LyricsForcedAligner.align(reference, transcript.wordTimedJson, song.durationMs)
            if (result.lrc.isBlank()) {
                forcedAlignmentStatus.value = "No lyric lines could be aligned"
                return@launch
            }
            withContext(Dispatchers.IO) {
                dao.saveTranscript(transcript.copy(lrcText = result.lrc, generatedAt = System.currentTimeMillis()))
                repository.saveLyrics(
                    LyricsEntity(
                        songId = song.id,
                        original = result.lrc,
                        translation = currentClassicLyrics.value?.translation.orEmpty(),
                        romanization = currentClassicLyrics.value?.romanization.orEmpty(),
                        synchronized = true,
                        source = "offline-forced-align"
                    )
                )
            }
            forcedAlignmentStatus.value = "Aligned ${result.matchedLines}/${result.totalLines} lines • ${(result.averageConfidence * 100).toInt()}%"
        }
    }

    fun applyGeneratedLyricsToClassic() {
        val song = currentSong.value ?: return
        val transcript = currentTranscript.value ?: return
        if (transcript.lrcText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveLyrics(
                LyricsEntity(
                    songId = song.id,
                    original = transcript.lrcText,
                    synchronized = true,
                    source = "offline-${transcript.engine}"
                )
            )
        }
    }

    fun togglePlayback() = app.playback.toggle()
    fun next() = app.playback.next()
    fun previous() = app.playback.previous()
    fun seekTo(positionMs: Long) = app.playback.seekTo(positionMs)

    fun queueRecommendations(limit: Int = 12) {
        recommendations.value.take(limit.coerceIn(1, 30)).forEach { candidate ->
            app.playback.addToQueue(candidate.song)
        }
    }

    fun playRecommendation(candidate: LocalRecommendation) {
        val ordered = listOf(candidate.song) + recommendations.value.map { it.song }.filter { it.id != candidate.song.id }
        app.playback.play(candidate.song, ordered)
        updateFeedback(candidate.song.id) { current -> current.copy(lastSeededAt = System.currentTimeMillis()) }
    }

    fun boostRecommendation(songId: Long) = updateFeedback(songId) { current ->
        current.copy(boost = (current.boost + 5).coerceAtMost(30), dismissCount = maxOf(0, current.dismissCount - 1))
    }

    fun dismissRecommendation(songId: Long) = updateFeedback(songId) { current ->
        current.copy(boost = (current.boost - 3).coerceAtLeast(-30), dismissCount = current.dismissCount + 1)
    }

    private fun updateFeedback(songId: Long, transform: (RecommendationFeedbackEntity) -> RecommendationFeedbackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.recommendationFeedback(songId) ?: RecommendationFeedbackEntity(songId)
            dao.saveRecommendationFeedback(transform(current))
            val seed = currentSong.value
            val library = dao.allSongsSnapshot()
            val history = dao.historySnapshot()
            val favorites = dao.favoriteIdsSnapshot().toSet()
            val analyses = dao.audioAnalysisSnapshot()
            val feedback = dao.recommendationFeedbackSnapshot()
            recommendations.value = withContext(Dispatchers.Default) {
                LocalRecommendationEngine.recommend(library, seed, history, favorites, analyses, feedback)
            }
        }
    }

    fun findCurrentWordIndex(positionMs: Long): Int {
        val json = currentTranscript.value?.wordTimedJson ?: return -1
        return runCatching {
            val array = org.json.JSONArray(json)
            var answer = -1
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val start = item.optLong("startMs")
                val end = item.optLong("endMs")
                if (positionMs in start..maxOf(start, end)) {
                    answer = i
                    break
                }
                if (start <= positionMs) answer = i
            }
            answer
        }.getOrDefault(-1)
    }

    fun modelStorageBytes(): Long = runCatching {
        File(getApplication<Application>().filesDir, "offline_speech_models")
            .walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    override fun onCleared() {
        transcriptionJob?.cancel()
        waveformJob?.cancel()
        transcriber.close()
        super.onCleared()
    }
}
