package com.neoplayer.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.neoplayer.app.data.MediaStoreScanner
import com.neoplayer.app.data.MusicRepository
import com.neoplayer.app.data.NeoDatabase
import com.neoplayer.app.lyrics.ConfiguredJsonLyricsProvider
import com.neoplayer.app.lyrics.LyricsProviderRegistry
import com.neoplayer.app.lyrics.SidecarLyricsLoader
import com.neoplayer.app.playback.AudioEffectsEngine
import com.neoplayer.app.playback.PlaybackConnection
import com.neoplayer.app.radio.LocalRadioManager
import com.neoplayer.app.settings.AppSettings
import com.neoplayer.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NeoApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rescanJob: Job? = null
    @Volatile private var currentMinDurationMs: Long = 10_000L

    lateinit var database: NeoDatabase
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var initialSettings: AppSettings
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var localRadio: LocalRadioManager
        private set
    lateinit var lyricsProviders: LyricsProviderRegistry
        private set
    val audioEffects = AudioEffectsEngine()

    private val mediaObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scheduleLibraryRefresh() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = NeoDatabase.create(this)
        lyricsProviders = LyricsProviderRegistry(buildList {
            if (BuildConfig.LYRICS_API_BASE.isNotBlank()) {
                add(ConfiguredJsonLyricsProvider(BuildConfig.LYRICS_API_BASE, BuildConfig.LYRICS_API_KEY))
            }
        })
        repository = MusicRepository(
            database.musicDao(),
            MediaStoreScanner(this),
            lyricsProviders,
            SidecarLyricsLoader(this)
        )
        settings = SettingsRepository(this)
        // Resolve the persisted palette before Compose draws its first frame. DataStore is tiny,
        // and doing this during the system splash prevents a dark/orange placeholder from flashing
        // before a saved light theme or custom accent becomes available.
        initialSettings = runBlocking(Dispatchers.IO) { settings.values.first() }
        playback = PlaybackConnection(this)
        playback.connect()
        localRadio = LocalRadioManager(this, repository, playback)

        appScope.launch {
            settings.values
                .map { it.minDurationMs }
                .distinctUntilChanged()
                .collect { currentMinDurationMs = it.coerceAtLeast(0L) }
        }

        appScope.launch {
            settings.values
                .map { it.strictOfflineMode }
                .distinctUntilChanged()
                .collect { strict -> lyricsProviders.setNetworkEnabled(!strict) }
        }

        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
    }

    private fun scheduleLibraryRefresh() {
        rescanJob?.cancel()
        rescanJob = appScope.launch {
            delay(1_500L)
            runCatching { repository.rescan(currentMinDurationMs) }
        }
    }

    override fun onTerminate() {
        runCatching { contentResolver.unregisterContentObserver(mediaObserver) }
        rescanJob?.cancel()
        playback.release()
        localRadio.release()
        audioEffects.release()
        database.close()
        appScope.cancel()
        super.onTerminate()
    }
}
