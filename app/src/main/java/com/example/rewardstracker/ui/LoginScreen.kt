package com.example.rewardstracker.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    // Host and path of the page currently shown, so it is clear when sign-in has finished.
    var current by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = current,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            val uri = url.toUri()
                            current = "${uri.host}${uri.path}"
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            // Write cookies to disk as soon as each page finishes.
                            CookieManager.getInstance().flush()
                        }
                    }
                    loadUrl("https://rewards.bing.com/")
                }
            }
        )
        Button(
            onClick = {
                CookieManager.getInstance().flush()
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Text("ダッシュボードが表示されたら押す")
        }
    }
}
