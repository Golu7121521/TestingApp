package com.example.universalplayer.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.universalplayer.data.CookieVault
import com.example.universalplayer.data.LoginSite

/** Looks like real Chrome (no "wv" marker) and matches the installed WebView version. */
private fun chromeUa(ctx: android.content.Context, desktop: Boolean): String {
    val def = WebSettings.getDefaultUserAgent(ctx)
    val ver = Regex("Chrome/([\\d.]+)").find(def)?.groupValues?.get(1) ?: "126.0.0.0"
    return if (desktop) {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$ver Safari/537.36"
    } else {
        def.replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun LoginScreen(site: LoginSite, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = chromeUa(ctx, true)      // desktop site by default
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportMultipleWindows(false)
                if (Build.VERSION.SDK_INT >= 29) forceDark = WebSettings.FORCE_DARK_OFF
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    error = null
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                    if (request.isForMainFrame) error = "${e.description} (${e.errorCode})"
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, r: WebResourceResponse) {
                    if (request.isForMainFrame) error = "HTTP ${r.statusCode}"
                }

                // ignore "open in app" links (instagram://, intent://) - they would leave a blank page
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString()
                    return !(u.startsWith("http://") || u.startsWith("https://"))
                }
            }
            loadUrl(site.url)
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    BackHandler { if (web.canGoBack()) web.goBack() else onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // slim top bar
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Close", Modifier.size(20.dp))
            }
            Text(
                "Sign in to ${site.name}", style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { error = null; web.reload() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Refresh, "Reload", Modifier.size(20.dp))
            }
            Spacer(Modifier.width(2.dp))
            Button(
                onClick = {
                    CookieManager.getInstance().flush()
                    val c = CookieManager.getInstance().getCookie("https://www.${site.domain}")
                    if (c.isNullOrBlank()) {
                        Toast.makeText(ctx, "Please sign in first", Toast.LENGTH_SHORT).show()
                    } else {
                        CookieVault.save(site.domain, c)
                        Toast.makeText(ctx, "Saved. Try the video again.", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                },
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) { Text("Done", fontSize = 13.sp) }
            Spacer(Modifier.width(6.dp))
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        error?.let {
            Text(
                "Page error: $it. Check internet, then tap reload.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}
