package com.newtube.player

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class NewTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalStore.init(this)
        NewPipe.init(
            NewPipeOkHttpDownloader.instance,
            Localization("en", "IN"),
            ContentCountry("IN")
        )
    }
}
