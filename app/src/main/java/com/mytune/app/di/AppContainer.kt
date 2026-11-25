package com.mytune.app.di

import android.app.Application
import android.os.Environment
import androidx.room.Room
import com.mytune.app.data.api.JioSaavnApi
import com.mytune.app.data.api.JioSaavnService
import com.mytune.app.data.api.ApiConfig
import com.mytune.app.data.db.AppDatabase
import com.mytune.app.data.repository.LibraryRepository
import com.mytune.app.data.repository.SettingsStore
import com.mytune.app.domain.MusicRepository
import com.mytune.app.download.DownloadRepository
import com.mytune.app.player.PlayerController
import com.mytune.app.player.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Simple manual DI: one lazily wired graph shared by the UI, the player service and workers. */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "MyTune/1.0").build())
        }
        .build()

    val repository: MusicRepository = JioSaavnApi(
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JioSaavnService::class.java)
    )

    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "mytune.db").build()
    val settings = SettingsStore(app, appScope)
    val library = LibraryRepository(db.dao())
    val downloads = DownloadRepository(app, db.dao(), this)
    val resolver = StreamResolver(repository, db.dao(), settings)
    val player = PlayerController(app, appScope, library)

    /** App-specific external storage: no storage permission required. */
    fun musicDir(): File {
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "music")
        dir.mkdirs()
        return dir
    }
}
