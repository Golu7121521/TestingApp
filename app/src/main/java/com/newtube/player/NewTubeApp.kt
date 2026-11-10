package com.newtube.player

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class NewTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize TeamNewPipe Extractor with Indian English localization
        NewPipe.init(
            NewPipeOkHttpDownloader(),
            Localization("en", "IN"),
            ContentCountry("IN")
        )
    }
}
