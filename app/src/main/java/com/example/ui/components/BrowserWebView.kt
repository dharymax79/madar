package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.LongPressWebElement
import com.example.ui.WebCommand

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * Interactive WebView Component supporting:
 * - JavaScript, Cookies, DOM Storage, Zoom controls, Desktop/Mobile user-agent switching.
 * - Automatic Download Interception via `setDownloadListener`.
 * - Long-Press detection on Images, Videos, and Links to trigger custom download or open in new tab.
 * - Built-in Interactive Download Test Portal (`https://madar-test.local/download-lab`) for instant testing.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tabId: Long,
    url: String,
    javaScriptEnabled: Boolean,
    cookiesEnabled: Boolean,
    isDesktopMode: Boolean,
    webCommand: WebCommand?,
    longPressElement: LongPressWebElement?,
    onCommandHandled: () -> Unit,
    onPageStarted: (url: String, title: String?) -> Unit,
    onProgressChanged: (progress: Float, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageFinished: (url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onDownloadIntercepted: (
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        sourceLabel: String
    ) -> Unit,
    onLongPressDetected: (LongPressWebElement?) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current

    val webView = remember(tabId) {
        WebView(context).apply {
            settings.apply {
                this.javaScriptEnabled = javaScriptEnabled
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
            }

            runCatching {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(cookiesEnabled)
                cookieManager.setAcceptThirdPartyCookies(this, cookiesEnabled)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val reqUrl = request?.url?.toString() ?: return false
                    if (reqUrl.startsWith("madar://sample/")) {
                        onDownloadIntercepted(
                            reqUrl,
                            view?.settings?.userAgentString.orEmpty(),
                            null,
                            null,
                            -1L,
                            "بوابة اختبار التحميل"
                        )
                        return true
                    }
                    // Detect direct file extensions in links if server doesn't send attachment header
                    val lowerPath = request.url.path?.lowercase().orEmpty()
                    if (lowerPath.endsWith(".pdf") || lowerPath.endsWith(".zip") ||
                        lowerPath.endsWith(".apk") || lowerPath.endsWith(".mp3") ||
                        lowerPath.endsWith(".mp4") || lowerPath.endsWith(".wav")
                    ) {
                        onDownloadIntercepted(
                            reqUrl,
                            view?.settings?.userAgentString.orEmpty(),
                            null,
                            null,
                            -1L,
                            "رابط ملف مباشر"
                        )
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, startedUrl, favicon)
                    if (startedUrl != null) {
                        onPageStarted(startedUrl, view?.title)
                    }
                }

                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    super.onPageFinished(view, finishedUrl)
                    if (finishedUrl != null) {
                        onPageFinished(
                            finishedUrl,
                            view?.title,
                            view?.canGoBack() == true,
                            view?.canGoForward() == true
                        )
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onProgressChanged(
                        newProgress / 100f,
                        view?.canGoBack() == true,
                        view?.canGoForward() == true
                    )
                }
            }

            // Standard DownloadListener for any attachment / downloadable content
            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                onDownloadIntercepted(
                    downloadUrl,
                    userAgent ?: settings.userAgentString,
                    contentDisposition,
                    mimeType,
                    contentLength,
                    "رابط تحميل من الموقع"
                )
            }

            // Long-press listener for images, media, and hyperlinks inside web pages
            setOnLongClickListener {
                val hit = hitTestResult
                val extra = hit.extra
                if (!extra.isNullOrBlank()) {
                    when (hit.type) {
                        WebView.HitTestResult.IMAGE_TYPE,
                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            onLongPressDetected(
                                LongPressWebElement(
                                    url = extra,
                                    titleOrType = "صورة من صفحة الويب",
                                    isMediaOrImage = true
                                )
                            )
                            true
                        }
                        WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                            onLongPressDetected(
                                LongPressWebElement(
                                    url = extra,
                                    titleOrType = "رابط ويب / ملف",
                                    isMediaOrImage = false
                                )
                            )
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        }
    }

    // Update dynamic settings when changed
    LaunchedEffect(javaScriptEnabled, cookiesEnabled, isDesktopMode) {
        webView.settings.javaScriptEnabled = javaScriptEnabled
        webView.settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
        runCatching {
            CookieManager.getInstance().setAcceptCookie(cookiesEnabled)
        }
    }

    // Load initial or changed URL
    LaunchedEffect(url, tabId) {
        if (url == "https://madar-test.local/download-lab") {
            webView.loadDataWithBaseURL(
                "https://madar-test.local/download-lab",
                buildInteractiveTestLabHtml(),
                "text/html",
                "UTF-8",
                null
            )
        } else if (webView.url != url && url.isNotBlank()) {
            webView.loadUrl(url)
        }
    }

    // Handle imperative commands (Reload, Stop, Back, Forward, LoadUrl)
    LaunchedEffect(webCommand) {
        when (val cmd = webCommand) {
            is WebCommand.LoadUrl -> {
                if (cmd.url == "https://madar-test.local/download-lab") {
                    webView.loadDataWithBaseURL(
                        "https://madar-test.local/download-lab",
                        buildInteractiveTestLabHtml(),
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    webView.loadUrl(cmd.url)
                }
                onCommandHandled()
            }
            WebCommand.Reload -> {
                webView.reload()
                onCommandHandled()
            }
            WebCommand.StopLoading -> {
                webView.stopLoading()
                onCommandHandled()
            }
            WebCommand.GoBack -> {
                if (webView.canGoBack()) webView.goBack()
                onCommandHandled()
            }
            WebCommand.GoForward -> {
                if (webView.canGoForward()) webView.goForward()
                onCommandHandled()
            }
            null -> Unit
        }
    }

    DisposableEffect(tabId) {
        onDispose {
            webView.stopLoading()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier
            .fillMaxSize()
            .testTag("browser_webview")
    )

    // Long-press context dialog for downloading images/links or opening in new tab
    if (longPressElement != null) {
        AlertDialog(
            onDismissRequest = { onLongPressDetected(null) },
            title = {
                Text(
                    text = longPressElement.titleOrType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = longPressElement.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Button(
                        onClick = {
                            val elem = longPressElement
                            onLongPressDetected(null)
                            onDownloadIntercepted(
                                elem.url,
                                webView.settings.userAgentString,
                                null,
                                if (elem.isMediaOrImage) "image/png" else null,
                                -1L,
                                elem.titleOrType
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (longPressElement.isMediaOrImage) {
                                "تحميل الصورة مع تخصيص الاسم والمجلد"
                            } else {
                                "تحميل الرابط كملف مع تخصيص الاسم والمجلد"
                            }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val targetUrl = longPressElement.url
                                onLongPressDetected(null)
                                onOpenInNewTab(targetUrl)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تبويب جديد")
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", longPressElement.url))
                                onLongPressDetected(null)
                                onShowMessage("تم نسخ الرابط إلى الحافظة")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ الرابط")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onLongPressDetected(null) }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

/**
 * Generates an interactive RTL Arabic HTML page loaded inside WebView so the user can test
 * clicking direct download links and long-pressing embedded images right inside WebView.
 */
private fun buildInteractiveTestLabHtml(): String {
    return """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>بوابة اختبار التحميل المباشر - متصفح مدار</title>
            <style>
                body {
                    font-family: sans-serif;
                    background: #0B1120;
                    color: #F8FAFC;
                    margin: 0;
                    padding: 20px;
                    line-height: 1.7;
                }
                .card {
                    background: #1E293B;
                    border: 1px solid #334155;
                    border-radius: 16px;
                    padding: 18px;
                    margin-bottom: 16px;
                }
                h2 { color: #38BDF8; margin-top: 0; }
                .btn {
                    display: inline-block;
                    background: #0284C7;
                    color: #FFFFFF;
                    text-decoration: none;
                    padding: 10px 18px;
                    border-radius: 10px;
                    font-weight: bold;
                    margin-top: 8px;
                }
                .badge {
                    background: #0F766E;
                    color: #CCFBF1;
                    padding: 3px 10px;
                    border-radius: 8px;
                    font-size: 12px;
                }
                img.sample-img {
                    width: 100%;
                    max-width: 360px;
                    border-radius: 12px;
                    border: 2px solid #38BDF8;
                    margin-top: 10px;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <h2>🚀 بوابة اختبار مدير التحميلات داخل المتصفح</h2>
                <p>اضغط على أي رابط أدناه لاعتراض التحميل تلقائياً وفتح نافذة تخصيص اسم الملف واختيار مجلد التخزين المخصص:</p>
            </div>
            <div class="card">
                <span class="badge">مستند PDF • 2.4 MB</span>
                <h3>دليل معمارية أندرويد الحديث</h3>
                <p>اختبر تغيير اسم الملف قبل الحفظ وميزة الإيقاف المؤقت والاستئناف.</p>
                <a class="btn" href="madar://sample/madar_android_architecture_guide.pdf">تحميل ملف PDF الآن</a>
            </div>
            <div class="card">
                <span class="badge">صورة عالية الدقة • 3.2 MB</span>
                <h3>خلفية مدار الفضائية (PNG)</h3>
                <p>يمكنك الضغط على زر التحميل أو <b>الضغط المطول على الصورة أدناه</b> لتحميلها مباشرة:</p>
                <img class="sample-img" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" alt="صورة تجريبية للضغط المطول" />
                <br/>
                <a class="btn" href="madar://sample/madar_space_wallpaper_hd.png">تحميل الصورة بدقة كاملة</a>
            </div>
            <div class="card">
                <span class="badge">ملف صوتي WAV • 2.8 MB</span>
                <h3>نغمة مدار الصوتية الهادئة</h3>
                <a class="btn" href="madar://sample/madar_ambient_chime.wav">تحميل الملف الصوتي</a>
            </div>
            <div class="card">
                <span class="badge">أرشيف مضغوط ZIP • 4.0 MB</span>
                <h3>حزمة موارد وأكواد المطورين</h3>
                <a class="btn" href="madar://sample/madar_developer_starter_kit.zip">تحميل الأرشيف المضغوط</a>
            </div>
        </body>
        </html>
    """.trimIndent()
}
