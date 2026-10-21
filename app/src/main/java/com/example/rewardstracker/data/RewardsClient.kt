package com.example.rewardstracker.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

sealed interface FetchResult {
    val trace: String

    data class Ok(val available: Int, val lifetime: Int, override val trace: String) : FetchResult
    data class NotLoggedIn(override val trace: String) : FetchResult
    data class Error(val message: String, override val trace: String) : FetchResult
}

// Reads the available points straight from the rendered Rewards dashboard in a hidden
// WebView. The old /api/getuserinfo endpoint now answers 401 even to the dashboard
// itself, so the number is taken from what the page shows.
object RewardsClient {
    private const val HOME = "https://rewards.bing.com/"
    private const val TIMEOUT_MS = 60_000L
    private const val POLL_INTERVAL_MS = 1_000L
    private const val MAX_POLLS = 25

    // Returns "method|12,345" or an empty string when the number is not rendered yet.
    // Method B (label followed by a number line) is checked first; method A (an element
    // carrying an "AvailablePoints" instrumentation attribute) is a language-independent fallback.
    private const val SCRAPE = """
        (function () {
            var lines = (document.body ? document.body.innerText : '').split('\n')
                .map(function (l) { return l.trim(); })
                .filter(function (l) { return l.length > 0; });
            var label = /(使用可能な|利用可能な|available)\s*(ポイント|points?)/i;
            for (var k = 0; k < lines.length - 1; k++) {
                if (label.test(lines[k]) && /^\d[\d,]*$/.test(lines[k + 1])) {
                    return 'label|' + lines[k + 1];
                }
            }
            try {
                var all = document.querySelectorAll('*');
                for (var i = 0; i < all.length; i++) {
                    var el = all[i], attrs = el.attributes;
                    for (var j = 0; j < attrs.length; j++) {
                        if (attrs[j].value.indexOf('AvailablePoints') >= 0) {
                            var t = el.innerText || '';
                            var m = t.match(/\d[\d,]*/);
                            if (m && t.length < 40) return 'attr|' + m[0];
                        }
                    }
                }
            } catch (x) {}
            return '';
        })();
    """

    // Diagnostic used only when the number could not be found: lines with digits and
    // the line before them, so the labels show how the page is laid out.
    private const val PROBE = """
        (function () {
            var out = {};
            out.path = location.pathname;
            var lines = (document.body ? document.body.innerText : '').split('\n')
                .map(function (l) { return l.trim(); })
                .filter(function (l) { return l.length > 0; });
            var rows = [];
            for (var i = 0; i < lines.length && rows.length < 14; i++) {
                if (/\d{3}/.test(lines[i])) {
                    rows.push((i > 0 ? lines[i - 1].slice(0, 25) : '') + ' | ' + lines[i].slice(0, 25));
                }
            }
            out.rows = rows;
            return JSON.stringify(out);
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetch(context: Context): FetchResult = withContext(Dispatchers.Main) {
        val trace = StringBuilder()
        val handler = Handler(Looper.getMainLooper())

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<FetchResult> { cont ->
                val webView = WebView(context.applicationContext)
                var finished = false
                var destroyed = false
                var polling = false
                var polls = 0

                fun release() {
                    if (destroyed) return
                    destroyed = true
                    webView.stopLoading()
                    webView.destroy()
                }

                fun complete(r: FetchResult) {
                    if (finished) return
                    finished = true
                    // Destroying the WebView inside its own callback can crash, so post it.
                    handler.post { release() }
                    if (cont.isActive) cont.resume(r)
                }

                fun probeAndFail() {
                    webView.evaluateJavascript(PROBE) { raw ->
                        trace.append("probe: ${unquote(raw)}\n")
                        complete(FetchResult.Error("points not found on page", trace.toString()))
                    }
                }

                // The dashboard renders client-side, so poll until the number shows up.
                fun poll() {
                    if (finished) return
                    webView.evaluateJavascript(SCRAPE) { raw ->
                        if (finished) return@evaluateJavascript
                        val found = unquote(raw)
                        val points = found.substringAfter('|', "")
                            .filter { it.isDigit() }.toIntOrNull()
                        polls++
                        when {
                            points != null -> {
                                trace.append("scrape -> ${found.substringBefore('|')} after $polls polls\n")
                                complete(FetchResult.Ok(points, -1, trace.toString()))
                            }
                            polls >= MAX_POLLS -> probeAndFail()
                            else -> handler.postDelayed({ poll() }, POLL_INTERVAL_MS)
                        }
                    }
                }

                cont.invokeOnCancellation { handler.post { release() } }

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val uri = url.toUri()
                        val host = uri.host.orEmpty()
                        val path = uri.path.orEmpty()
                        trace.append("page $host$path\n")
                        when {
                            // Landing on a sign-in host means there is no valid session.
                            host.startsWith("login.") ->
                                complete(FetchResult.NotLoggedIn(trace.toString()))
                            // /Signin is only a redirect stop; wait for the real page.
                            host.startsWith("rewards.") && !polling &&
                                !path.contains("signin", ignoreCase = true) -> {
                                polling = true
                                poll()
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) {
                            trace.append("load error ${error.errorCode} ${error.description}\n")
                            complete(FetchResult.Error("load error ${error.description}", trace.toString()))
                        }
                    }
                }

                trace.append("load ${HOME.toUri().host}\n")
                webView.loadUrl(HOME)
            }
        }
        result ?: FetchResult.Error("timeout", trace.toString())
    }

    // evaluateJavascript hands back the result as a JSON literal ("..." with escapes).
    private fun unquote(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try {
            JSONTokener(raw).nextValue() as? String ?: raw
        } catch (e: Exception) {
            raw
        }
    }
}
