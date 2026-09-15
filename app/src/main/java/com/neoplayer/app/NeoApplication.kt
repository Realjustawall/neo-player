package com.neoplayer.app

import android.app.Application
import com.neoplayer.app.data.MediaStoreScanner
import com.neoplayer.app.data.MusicRepository
import com.neoplayer.app.data.NeoDatabase
import com.neoplayer.app.playback.PlaybackConnection
import com.neoplayer.app.settings.SettingsRepository

class NeoApplication : Application() {
    lateinit var database: NeoDatabase
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var playback: PlaybackConnection
        private set

    override fun onCreate() {
        super.onCreate()
        database = NeoDatabase.create(this)
        repository = MusicRepository(database.musicDao(), MediaStoreScanner(this))
        settings = SettingsRepository(this)
        playback = PlaybackConnection(this)
        playback.connect()
    }
}
