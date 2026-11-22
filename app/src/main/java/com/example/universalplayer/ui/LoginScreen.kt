package com.example.universalplayer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JsResult
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
private fun chromeUa(ctx: Context, desktop: Boolean): String {
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
private fun configureWeb(ctx: Context, w: WebView, site: LoginSite) {
    w.setBackgroundColor(android.graphics.Color.WHITE)
    w.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
        mediaPlaybackRequiresUserGesture = false
        userAgentString = chromeUa(ctx, site.desktop)
        useWideViewPort = site.desktop
        loadWithOverviewMode = site.desktop
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        if (Build.VERSION.SDK_INT >= 29) forceDark = WebSettings.FORCE_DARK_OFF
    }
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
}

@Composable
fun LoginScreen(site: LoginSite, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<WebView?>(null) }

    val client = remember {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { error = null }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                if (request.isForMainFrame) error = "${e.description} (${e.errorCode})"
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, r: WebResourceResponse) {
                if (request.isForMainFrame) error = "HTTP ${r.statusCode}"
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (u.startsWith("http://") || u.startsWith("https://")) return false
                // app links (intent://, instagram://): use the web fallback if the page gives one
                if (u.startsWith("intent://")) {
                    runCatching {
                        Intent.parseUri(u, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                            ?.let { view.loadUrl(it) }
                    }
                }
                return true
            }
        }
    }

    val chrome = remember {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }

            // real pop-up windows (verification / phone-number steps) open on top, like in a browser
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val w = WebView(ctx)
                configureWeb(ctx, w, site)
                w.webViewClient = client
                w.webChromeClient = this
                popup = w
                (resultMsg.obj as WebView.WebViewTransport).webView = w
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                if (popup === window) popup = null
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                android.app.AlertDialog.Builder(view.context).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                android.app.AlertDialog.Builder(view.context).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }
    }

    val web = remember {
        WebView(ctx).apply {
            configureWeb(ctx, this, site)
            webViewClient = client
            webChromeClient = chrome
            loadUrl(site.url)
        }
    }
    DisposableEffect(Unit) { onDispose { popup?.destroy(); web.destroy() } }
    BackHandler {
        val p = popup
        when {
            p != null -> { popup = null; p.destroy() }
            web.canGoBack() -> web.goBack()
            else -> onClose()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
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
            IconButton(onClick = { error = null; (popup ?: web).reload() }, modifier = Modifier.size(36.dp)) {
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            popup?.let { p ->
                key(p) { AndroidView(factory = { p }, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}
