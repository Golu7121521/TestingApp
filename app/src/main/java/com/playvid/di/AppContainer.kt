package com.playvid.di

import android.content.Context
import androidx.room.Room
import com.playvid.data.local.AppDatabase
import com.playvid.data.local.HistoryRepositoryImpl
import com.playvid.data.local.MIGRATION_2_3
import com.playvid.data.local.MIGRATION_3_4
import com.playvid.data.local.PlaylistRepositoryImpl
import com.playvid.data.settings.SettingsRepositoryImpl
import com.playvid.domain.repository.HistoryRepository
import com.playvid.domain.repository.PlaylistRepository
import com.playvid.domain.repository.SettingsRepository
import com.playvid.domain.usecase.ValidateStreamUrlUseCase
import com.playvid.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container: the one place that wires every layer together. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Outlives ViewModels; used to persist playback progress when the player closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "playvid.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    val historyRepository: HistoryRepository = HistoryRepositoryImpl(database.historyDao())
    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(database.playlistDao())
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext)
    val validateUrl = ValidateStreamUrlUseCase()

    /** The single app-wide playback session (player screen + mini player). */
    val playback: PlaybackController by lazy {
        PlaybackController(appContext, httpClient, historyRepository, settingsRepository, appScope)
    }

    /** Shared HTTP client for streaming, with explicit timeouts so bad links fail fast. */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
