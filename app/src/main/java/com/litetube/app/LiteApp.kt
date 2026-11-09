package com.litetube.app

import android.app.Application
import com.litetube.app.net.NpDownloader
import org.schabi.newpipe.extractor.NewPipe

class LiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // NewPipeExtractor ko apna downloader dena zaroori hai
        NewPipe.init(NpDownloader.instance)
    }
}
