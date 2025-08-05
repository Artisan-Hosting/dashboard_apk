package net.artisanhosting.dashboard

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        val settings = webView.settings

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack() && webView.isEnabled) {
                    webView.goBack()
                } else {
                    webView.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Enable JS and local storage
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true

        // Use local cache when offline
        settings.cacheMode = WebSettings.LOAD_DEFAULT


        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()

                val basePath = "www"
                val relativePath = url.substringAfter("file:///android_asset/www/")

                // 1️⃣ Always serve _next assets from root
                if (url.contains("/_next/")) {
                    val assetPath = basePath + "/" + url.substringAfter("_next/")
                    return try {
                        val stream = assets.open(assetPath)
                        WebResourceResponse("application/javascript", "UTF-8", stream)
                    } catch (_: Exception) {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                // 2️⃣ Serve static files (CSS, JS, images, etc.)
                if (
                    url.endsWith(".js") || url.endsWith(".css") ||
                    url.endsWith(".png") || url.endsWith(".jpg") ||
                    url.endsWith(".jpeg") || url.endsWith(".webp") ||
                    url.endsWith(".svg") || url.endsWith(".ico")
                ) {
                    return try {
                        val stream = assets.open("$basePath/$relativePath")
                        val mimeType = when {
                            relativePath.endsWith(".css") -> "text/css"
                            relativePath.endsWith(".js") -> "application/javascript"
                            relativePath.endsWith(".png") -> "image/png"
                            relativePath.endsWith(".jpg") || relativePath.endsWith(".jpeg") -> "image/jpeg"
                            relativePath.endsWith(".webp") -> "image/webp"
                            relativePath.endsWith(".svg") -> "image/svg+xml"
                            relativePath.endsWith(".ico") -> "image/x-icon"
                            else -> "application/octet-stream"
                        }
                        WebResourceResponse(mimeType, "UTF-8", stream)
                    } catch (_: Exception) {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                // 3️⃣ Serve specific flat HTML files if they exist
                try {
                    assets.open("$basePath/$relativePath").use { stream ->
                        return WebResourceResponse("text/html", "UTF-8", stream)
                    }
                } catch (_: Exception) { /* no direct file */ }

                // 4️⃣ Handle /apps/<id> → apps/[id].html
                if (relativePath.startsWith("apps/") && relativePath != "apps.html") {
                    return try {
                        val stream = assets.open("$basePath/apps/[id].html")
                        WebResourceResponse("text/html", "UTF-8", stream)
                    } catch (_: Exception) {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                // 5️⃣ Handle /apps → apps.html
                if (relativePath == "apps" || relativePath == "apps/") {
                    return try {
                        val stream = assets.open("$basePath/apps.html")
                        WebResourceResponse("text/html", "UTF-8", stream)
                    } catch (_: Exception) {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                // 6️⃣ Final fallback → root index.html (SPA routes)
                return try {
                    val stream = assets.open("$basePath/index.html")
                    WebResourceResponse("text/html", "UTF-8", stream)
                } catch (_: Exception) {
                    super.shouldInterceptRequest(view, request)
                }
            }

        }

        // Intercept all requests for logging / debugging
        val htmlString = assets.open("www/index.html").bufferedReader().use { it.readText() }

        // Load with fake origin for cookie compatibility
        webView.loadDataWithBaseURL(
            "https://dashboard.artisanhosting.net",
            htmlString,
            "text/html",
            "UTF-8",
            null
        )


        WebView.setWebContentsDebuggingEnabled(true)
        // Handle downloads
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", webView.settings.userAgentString)
            request.setDescription("Downloading file...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType))
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }

        // Load your app from assets
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.loadUrl("file:///android_asset/www/index.html")

        setContentView(webView)
    }
}