package com.example.universalplayer.player

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession

/** Thin, crash-safe wrapper around the Google Cast SDK (devices without Play services just get no Cast). */
object CastHelper {
    private var cast: CastContext? = null
    private var failed = false

    /** true once the Cast SDK has been started (it is started lazily, when the user opens the Cast list). */
    val isReady: Boolean get() = cast != null

    fun context(ctx: Context): CastContext? {
        cast?.let { return it }
        if (failed) return null
        return try {
            CastContext.getSharedInstance(ctx.applicationContext).also { cast = it }
        } catch (t: Throwable) {
            failed = true
            null
        }
    }

    fun session(): CastSession? = runCatching {
        cast?.sessionManager?.currentCastSession?.takeIf { it.isConnected }
    }.getOrNull()

    fun endSession() {
        runCatching { cast?.sessionManager?.endCurrentSession(true) }
    }
}
