package com.vibemusic.app

import android.app.Application
import com.vibemusic.app.data.preferences.PreferencesRepository
import com.vibemusic.app.data.repository.MusicRepository
import com.vibemusic.app.data.scanner.MediaStoreScanner
import com.vibemusic.app.database.VibeDatabase
import com.vibemusic.app.player.AudioEffectsManager
import com.vibemusic.app.player.VibePlayerController

class VibeMusicApp : Application() {

    lateinit var database: VibeDatabase
        private set
    lateinit var preferencesRepository: PreferencesRepository
        private set
    lateinit var musicRepository: MusicRepository
        private set
    lateinit var playerController: VibePlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        database = VibeDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        val scanner = MediaStoreScanner(this)
        musicRepository = MusicRepository(
            context = this,
            scanner = scanner,
            dao = database.vibeDao(),
            prefsRepo = preferencesRepository
        )
        val effectsManager = AudioEffectsManager()
        playerController = VibePlayerController(
            context = this,
            repository = musicRepository,
            prefsRepo = preferencesRepository,
            effectsManager = effectsManager
        )
        playerController.initialize()
    }
}
