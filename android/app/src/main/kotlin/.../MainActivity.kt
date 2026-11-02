package com.desiflix.hindi_movie_stream

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugins.GeneratedPluginRegistrant
import android.content.res.Configuration

class MainActivity: FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // Ensure all plugins are cleanly bound without runtime reflection drops
        GeneratedPluginRegistrant.registerWith(flutterEngine)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Video orientation toggle (Portrait <-> Landscape) ke time state drop hone se roke
    }
}
