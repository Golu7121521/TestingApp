package com.universal.videoplayer

import android.app.Application
import com.universal.videoplayer.data.Repository

class App : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
    }
}
