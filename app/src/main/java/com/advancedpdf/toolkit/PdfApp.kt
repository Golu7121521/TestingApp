package com.advancedpdf.toolkit

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class PdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Store.themeMode(this))
    }
}
