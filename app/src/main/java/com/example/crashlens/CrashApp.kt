package com.example.crashlens

import android.app.Application

class CrashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crash handler yahin lagta hai
        CrashReporter.install(this)
    }
}
