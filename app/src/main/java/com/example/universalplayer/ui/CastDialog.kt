package com.example.universalplayer.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.universalplayer.player.CastHelper
import com.google.android.gms.cast.CastMediaControlIntent

/** Device picker for Chromecast, plus fallbacks (system cast screen, open in another app). */
@Composable
fun CastDialog(link: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val available = remember { CastHelper.context(ctx) != null }
    var routes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }

    if (available) {
        DisposableEffect(Unit) {
            val router = MediaRouter.getInstance(ctx)
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(
                    CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APP_ID)
                ).build()
            fun refresh() {
                routes = router.routes.filter { it.matchesSelector(selector) && !it.isDefault && it.isEnabled }
            }
            val cb = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            }
            router.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            refresh()
            onDispose { router.removeCallback(cb) }
        }
    }

    CompactDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cast to TV") },
        text = {
            Column {
                if (!available) {
                    Text("Chromecast is not available on this phone (Google Play services needed).")
                } else if (routes.isEmpty()) {
                    Text("Searching for Chromecast devices on your Wi-Fi...")
                }
                routes.forEach { r ->
                    TextButton(
                        onClick = {
                            runCatching { MediaRouter.getInstance(ctx).selectRoute(r) }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(r.name) }
                }
                TextButton(
                    onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { Toast.makeText(ctx, "Cast screen not supported", Toast.LENGTH_SHORT).show() }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cast screen (mirror)") }
                if (!link.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            runCatching {
                                val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(link), "video/*")
                                ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open in another app") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
