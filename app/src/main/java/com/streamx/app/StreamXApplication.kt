package com.streamx.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the DI graph from here.
 * NewPipeExtractor initialization is added in Phase 2 (data/source).
 */
@HiltAndroidApp
class StreamXApplication : Application()
