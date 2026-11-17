package com.example.universalplayer.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.universalplayer.data.CookieVault
import com.example.universalplayer.data.LoginSite
import com.example.universalplayer.data.USER_AGENTS

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(site: LoginSite, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    val web = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = USER_AGENTS[1].second
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            loadUrl(site.url)
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            Text("Sign in to ${site.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = {
                CookieManager.getInstance().flush()
                val c = CookieManager.getInstance().getCookie("https://www.${site.domain}")
                if (c.isNullOrBlank()) {
                    Toast.makeText(ctx, "Please sign in first", Toast.LENGTH_SHORT).show()
                } else {
                    CookieVault.save(site.domain, c)
                    Toast.makeText(ctx, "Saved. Try the video again.", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            }) { Text("Done") }
            Spacer(Modifier.width(8.dp))
        }
        AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}
