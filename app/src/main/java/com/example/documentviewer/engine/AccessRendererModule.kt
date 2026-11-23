package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.RendererCapabilities
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.security.DocumentSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class AccessRendererModule(
  private val context: Context,
  private val securityManager: DocumentSecurityManager,
  private val openManager: DocumentOpenManager
) : DocumentRenderer {

  override val capabilities = RendererCapabilities(
    supportsSearch = true,
    supportsTextCopy = true,
    supportsRotation = false,
    supportsThumbnails = false,
    supportsBookmarks = false,
    supportsPresentationMode = false,
    supportsDirectionToggle = true,
    supportsExportPdf = true,
    supportsNightMode = true,
    supportsContinuousToggle = false
  )

  private var webView: WebView? = null
  private var scope: CoroutineScope? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var currentTable = 1
  private var totalTables = 1

  override fun attachAndLoad(
    webView: WebView,
    descriptor: DocumentDescriptor,
    initialPage: Int,
    nightMode: Boolean,
    continuousScroll: Boolean,
    directionMode: DirectionMode,
    callbacks: RendererCallbacks
  ) {
    this.webView = webView
    scope?.cancel()
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope = ioScope

    securityManager.hardenWebView(webView)
    val assetLoader = securityManager.buildAssetLoader()
    webView.webViewClient = securityManager.buildStrictWebViewClient(
      assetLoader = assetLoader,
      onRenderProcessGone = { didCrash -> callbacks.onRenderProcessGone(didCrash) }
    )
    webView.removeJavascriptInterface("AndroidBridge")
    webView.addJavascriptInterface(ViewerJsBridge(object : RendererCallbacks by callbacks {
      override fun onDocumentLoaded(totalPages: Int) {
        this@AccessRendererModule.totalTables = totalPages.coerceAtLeast(1)
        callbacks.onDocumentLoaded(totalPages)
        if (initialPage in 2..this@AccessRendererModule.totalTables) {
          goToPage(initialPage)
        }
      }

      override fun onPageChanged(currentPage: Int, totalPages: Int) {
        this@AccessRendererModule.currentTable = currentPage.coerceAtLeast(1)
        this@AccessRendererModule.totalTables = totalPages.coerceAtLeast(1)
        callbacks.onPageChanged(currentPage, totalPages)
      }
    }), "AndroidBridge")

    ioScope.launch {
      val cached = descriptor.cachedFilePath?.let { File(it) }?.takeIf { it.exists() }
      val sourceUri = runCatching { Uri.parse(descriptor.uriString) }.getOrNull()
      when (val extracted = AccessDatabaseExtractor.extractToJson(context, cached, sourceUri, descriptor.displayName)) {
        is AccessExtractionResult.Success -> {
          val docUrl = createSessionUrl(
            securityManager = securityManager,
            descriptor = descriptor,
            inlineJsonBytes = extracted.jsonBytes
          )
          val encodedDoc = Uri.encode(docUrl)
          val viewerUrl = "${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/access/access_viewer.html" +
            "?doc=$encodedDoc&night=${if (nightMode) 1 else 0}&dir=${directionMode.jsValue}"
          mainHandler.post {
            this@AccessRendererModule.webView?.loadUrl(viewerUrl)
          }
        }
        is AccessExtractionResult.Failure -> {
          mainHandler.post {
            callbacks.onDocumentError(extracted.message)
          }
        }
      }
    }
  }

  override fun goToPage(pageNumber: Int) {
    val target = pageNumber.coerceIn(1, totalTables.coerceAtLeast(1))
    currentTable = target
    evalJs("window.DocuAccess && window.DocuAccess.goToPage($target);")
  }

  override fun nextPage() {
    if (currentTable < totalTables) goToPage(currentTable + 1)
  }

  override fun previousPage() {
    if (currentTable > 1) goToPage(currentTable - 1)
  }

  override fun setZoom(zoom: Float) {
    evalJs("window.DocuAccess && window.DocuAccess.setZoom(${zoom.coerceIn(0.5f, 3.0f)});")
  }

  override fun search(query: String) {
    val escaped = JSONObject.quote(query)
    evalJs("window.DocuAccess && window.DocuAccess.search($escaped);")
  }

  override fun findNext() {
    evalJs("window.DocuAccess && window.DocuAccess.findNext();")
  }

  override fun findPrevious() {
    evalJs("window.DocuAccess && window.DocuAccess.findPrev();")
  }

  override fun setNightMode(enabled: Boolean) {
    evalJs("window.DocuAccess && window.DocuAccess.setNightMode($enabled);")
  }

  override fun setDirectionMode(mode: DirectionMode) {
    evalJs("window.DocuAccess && window.DocuAccess.setDirectionMode('${mode.jsValue}');")
  }

  override fun copySelectedText() {
    evalJs("window.DocuAccess && window.DocuAccess.getSelectedText();")
  }

  override fun extractPageText(pageNumber: Int) {
    evalJs("window.DocuAccess && window.DocuAccess.extractPageText();")
  }

  override fun extractAllPagesText() {
    evalJs("window.DocuAccess && window.DocuAccess.extractAllPagesText();")
  }

  override fun printOrExportPdf(activityContext: Context, descriptor: DocumentDescriptor) {
    val wv = webView ?: return
    val adapter = wv.createPrintDocumentAdapter(descriptor.displayName)
    openManager.printAdapter(activityContext, descriptor.displayName, adapter, landscape = true)
  }

  override fun release() {
    scope?.cancel()
    scope = null
    webView?.apply {
      runCatching {
        stopLoading()
        removeJavascriptInterface("AndroidBridge")
        webViewClient = WebViewClient()
        clearHistory()
      }
    }
    webView = null
    securityManager.releaseSession()
  }

  private fun evalJs(script: String) {
    webView?.let { wv ->
      runCatching { wv.evaluateJavascript(script, null) }
    }
  }
}
