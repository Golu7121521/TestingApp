package com.playnova.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.playnova.app.data.AppDb
import com.playnova.app.data.MediaRepo
import com.playnova.app.data.SettingsStore
import com.playnova.app.i18n.Lang
import com.playnova.app.player.HeadsetController
import com.playnova.app.player.PlayerHolder

class PlayNovaApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()

    val db by lazy { AppDb.get(this) }
    val settings by lazy { SettingsStore(this) }
    val repo by lazy { MediaRepo(this) }
    val holder by lazy { PlayerHolder(this, db.dao(), settings) }
    val headset by lazy { HeadsetController(this, holder, settings) }

    override fun onCreate() {
        super.onCreate()
        Lang.init(Lang.saved(this))
    }
}
