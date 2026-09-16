package com.neoplayer.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.neoplayer.app.data.MediaStoreScanner
import com.neoplayer.app.data.MusicRepository
import com.neoplayer.app.data.NeoDatabase
import com.neoplayer.app.playback.PlaybackConnection
import com.neoplayer.app.playback.AudioEffectsEngine
import com.neoplayer.app.settings.SettingsRepository
import com.neoplayer.app.lyrics.ConfiguredJsonLyricsProvider
import com.neoplayer.app.lyrics.LyricsProviderRegistry
import com.neoplayer.app.lyrics.SidecarLyricsLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NeoApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rescanJob: Job? = null
    lateinit var database: NeoDatabase
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var lyricsProviders: LyricsProviderRegistry
        private set
    val audioEffects = AudioEffectsEngine()

    override fun onCreate() {
        super.onCreate()
        database = NeoDatabase.create(this)
        lyricsProviders = LyricsProviderRegistry(buildList {
            if (BuildConfig.LYRICS_API_BASE.isNotBlank()) add(ConfiguredJsonLyricsProvider(BuildConfig.LYRICS_API_BASE, BuildConfig.LYRICS_API_KEY))
        })
        repository = MusicRepository(database.musicDao(), MediaStoreScanner(this), lyricsProviders, SidecarLyricsLoader(this))
        settings = SettingsRepository(this)
        playback = PlaybackConnection(this)
        playback.connect()
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                rescanJob?.cancel()
                rescanJob = appScope.launch { delay(1_500); runCatching { repository.rescan() } }
            }
        })
    }
}
