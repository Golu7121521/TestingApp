package com.mytune.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.mytune.app.di.AppContainer

class MyTuneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(DOWNLOAD_CHANNEL, getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val DOWNLOAD_CHANNEL = "downloads"
    }
}
