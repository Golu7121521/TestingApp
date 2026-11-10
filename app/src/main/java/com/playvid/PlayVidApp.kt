package com.playvid

import android.app.Application
import com.playvid.di.AppContainer

/** Application class. Owns the manual dependency container (no DI framework needed). */
class PlayVidApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
