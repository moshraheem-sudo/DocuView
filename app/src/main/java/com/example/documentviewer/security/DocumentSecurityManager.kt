package com.example.documentviewer.security

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

class DocumentSecurityManager(private val context: Context) {

  companion object {
    private const val TAG = "DocumentSecurityManager"
    const val SANDBOX_HOST = "appassets.androidplatform.net"
    const val SANDBOX_ORIGIN = "https://$SANDBOX_HOST"
  }

  data class ActiveSession(
    val token: String,
    val fileName: String,
    val mimeType: String,
    val sourceUri: Uri?,
    val cachedFile: File?,
    val inlinePayloadBytes: ByteArray? = null
  )

  @Volatile private var activeSession: ActiveSession? = null
  private val destroyedWebViews = Collections.synchronizedSet(
    Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
  )

  fun createSession(
    fileName: String,
    mimeType: String,
    sourceUri: Uri?,
    cachedFile: File?,
    inlinePayloadBytes: ByteArray? = null
  ): ActiveSession {
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
    val session = ActiveSession(
      token = UUID.randomUUID().toString(),
      fileName = safeName,
      mimeType = mimeType,
      sourceUri = sourceUri,
      cachedFile = cachedFile,
      inlinePayloadBytes = inlinePayloadBytes
    )
    activeSession = session
    return session
  }

  fun documentUrlFor(session: ActiveSession): String =
    "$SANDBOX_ORIGIN/doc/${session.token}/${session.fileName}"

  fun releaseSession() {
    activeSession = null
  }

  fun buildAssetLoader(): WebViewAssetLoader {
    val baseAssetsHandler = WebViewAssetLoader.AssetsPathHandler(context)
    return WebViewAssetLoader.Builder()
      .setDomain(SANDBOX_HOST)
      .addPathHandler("/assets/", MimeSafeAssetsHandler(baseAssetsHandler))
      .addPathHandler("/doc/", EphemeralDocPathHandler())
      .build()
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun hardenWebView(webView: WebView) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.domStorageEnabled = false
    @Suppress("DEPRECATION")
    settings.databaseEnabled = false
    @Suppress("DEPRECATION")
    settings.setGeolocationEnabled(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.setSupportZoom(true)
    settings.textZoom = 100
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      settings.safeBrowsingEnabled = false
      runCatching {
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
      }
    }
    webView.isFocusable = true
    webView.isFocusableInTouchMode = true
    webView.isLongClickable = true
    webView.isHapticFeedbackEnabled = true
    webView.setOnLongClickListener(null)
    webView.webChromeClient = WebChromeClient()
    webView.isVerticalScrollBarEnabled = true
    webView.isHorizontalScrollBarEnabled = true
  }

  fun safelyDestroyWebView(webView: WebView) {
    if (!destroyedWebViews.add(webView)) return
    runCatching {
      webView.stopLoading()
      webView.removeJavascriptInterface("AndroidBridge")
      webView.webChromeClient = null
      webView.webViewClient = WebViewClient()
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.clearHistory()
      webView.destroy()
    }
  }

  fun buildStrictWebViewClient(
    assetLoader: WebViewAssetLoader,
    onHostPageFinished: () -> Unit = {},
    onRenderProcessGone: (didCrash: Boolean) -> Unit = {}
  ): WebViewClient = object : WebViewClient() {

    override fun shouldInterceptRequest(
      view: WebView,
      request: WebResourceRequest
    ): WebResourceResponse? {
      val url = request.url
      val urlStr = url.toString()
      if (urlStr == "about:blank" || url.scheme == "data") {
        return null
      }
      if (url.scheme == "blob" && url.schemeSpecificPart?.startsWith(SANDBOX_ORIGIN) == true) {
        return null
      }
      if (url.scheme == "https" && url.host == SANDBOX_HOST) {
        return assetLoader.shouldInterceptRequest(url) ?: forbiddenResponse()
      }
      // Strictly block any external or file:// / content:// request initiated from document content
      return forbiddenResponse()
    }

    override fun shouldOverrideUrlLoading(
      view: WebView,
      request: WebResourceRequest
    ): Boolean {
      val url = request.url
      return !(url.scheme == "https" && url.host == SANDBOX_HOST)
    }

    override fun onPageFinished(view: WebView, url: String) {
      super.onPageFinished(view, url)
      onHostPageFinished()
    }

    override fun onRenderProcessGone(
      view: WebView,
      detail: RenderProcessGoneDetail
    ): Boolean {
      val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        detail.didCrash()
      } else {
        true
      }
      Log.w(TAG, "WebView render process gone (didCrash=$didCrash); recovering safely")
      safelyDestroyWebView(view)
      onRenderProcessGone(didCrash)
      return true
    }
  }

  private fun forbiddenResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "UTF-8",
      403,
      "Blocked by offline sandbox",
      emptyMap(),
      ByteArrayInputStream(ByteArray(0))
    )

  private class MimeSafeAssetsHandler(
    private val delegate: WebViewAssetLoader.AssetsPathHandler
  ) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
      val res = delegate.handle(path) ?: return null
      val lower = path.lowercase()
      val (mime, encoding) = when {
        lower.endsWith(".html") -> "text/html" to "UTF-8"
        lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript" to "UTF-8"
        lower.endsWith(".css") -> "text/css" to "UTF-8"
        lower.endsWith(".json") -> "application/json" to "UTF-8"
        lower.endsWith(".svg") -> "image/svg+xml" to "UTF-8"
        lower.endsWith(".png") -> "image/png" to null
        lower.endsWith(".wasm") -> "application/wasm" to null
        lower.endsWith(".ttf") -> "font/ttf" to null
        lower.endsWith(".woff") -> "font/woff" to null
        lower.endsWith(".woff2") -> "font/woff2" to null
        lower.endsWith(".bcmap") || lower.endsWith(".pfb") -> "application/octet-stream" to null
        else -> res.mimeType to res.encoding
      }
      return WebResourceResponse(mime, encoding, res.data)
    }
  }

  private inner class EphemeralDocPathHandler : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
      val session = activeSession ?: return null
      val expectedPrefix = "${session.token}/"
      if (!path.startsWith(expectedPrefix)) return null

      val stream: InputStream? = when {
        session.inlinePayloadBytes != null -> ByteArrayInputStream(session.inlinePayloadBytes)
        session.cachedFile != null && session.cachedFile.exists() -> FileInputStream(session.cachedFile)
        session.sourceUri != null -> runCatching {
          context.contentResolver.openInputStream(session.sourceUri)
        }.getOrNull()
        else -> null
      }

      if (stream == null) return null
      val mime = if (session.inlinePayloadBytes != null) "application/json" else session.mimeType
      return WebResourceResponse(
        mime,
        null,
        200,
        "OK",
        mapOf("Cache-Control" to "no-store"),
        stream
      )
    }
  }
}
