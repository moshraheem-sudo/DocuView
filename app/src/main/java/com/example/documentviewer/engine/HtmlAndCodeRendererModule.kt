package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.RendererCapabilities
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.security.DocumentSecurityManager
import org.json.JSONObject

class HtmlAndCodeRendererModule(
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
    supportsContinuousToggle = false,
    supportsPdfAnnotation = false
  )

  private var webView: WebView? = null

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
    securityManager.hardenWebView(webView)
    val assetLoader = securityManager.buildAssetLoader()
    webView.webViewClient = securityManager.buildStrictWebViewClient(
      assetLoader = assetLoader,
      onRenderProcessGone = { didCrash -> callbacks.onRenderProcessGone(didCrash) }
    )
    webView.removeJavascriptInterface("AndroidBridge")
    webView.addJavascriptInterface(ViewerJsBridge(callbacks), "AndroidBridge")

    val docUrl = createSessionUrl(securityManager, descriptor)
    val encodedDoc = Uri.encode(docUrl)
    val ext = descriptor.displayName.substringAfterLast('.', "").lowercase()
    val isHtml = descriptor.format == DocumentFormat.HTML ||
      descriptor.format.category == DocumentCategory.HTML ||
      ext in DocumentFormat.HTML_EXTENSIONS

    val viewerUrl = "${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/code/code_viewer.html" +
      "?doc=$encodedDoc&html=${if (isHtml) 1 else 0}&ext=${Uri.encode(ext)}&night=${if (nightMode) 1 else 0}&dir=${directionMode.jsValue}"
    webView.loadUrl(viewerUrl)
  }

  override fun goToPage(pageNumber: Int) = Unit
  override fun nextPage() = Unit
  override fun previousPage() = Unit

  override fun setZoom(zoom: Float) {
    evalJs("window.DocuCode && window.DocuCode.setZoom(${zoom.coerceIn(0.5f, 3.0f)});")
  }

  override fun search(query: String) {
    val escaped = JSONObject.quote(query)
    evalJs("window.DocuCode && window.DocuCode.search($escaped);")
  }

  override fun findNext() {
    evalJs("window.DocuCode && window.DocuCode.findNext();")
  }

  override fun findPrevious() {
    evalJs("window.DocuCode && window.DocuCode.findPrev();")
  }

  override fun setNightMode(enabled: Boolean) {
    evalJs("window.DocuCode && window.DocuCode.setNightMode($enabled);")
  }

  override fun setDirectionMode(mode: DirectionMode) {
    evalJs("window.DocuCode && window.DocuCode.setDirectionMode('${mode.jsValue}');")
  }

  override fun copySelectedText() {
    evalJs("window.DocuCode && window.DocuCode.getSelectedText();")
  }

  override fun extractPageText(pageNumber: Int) {
    evalJs("window.DocuCode && window.DocuCode.extractPageText();")
  }

  override fun extractAllPagesText() {
    evalJs("window.DocuCode && window.DocuCode.extractAllPagesText();")
  }

  override fun printOrExportPdf(activityContext: Context, descriptor: DocumentDescriptor) {
    val wv = webView ?: return
    val adapter = wv.createPrintDocumentAdapter(descriptor.displayName)
    openManager.printAdapter(activityContext, descriptor.displayName, adapter, landscape = false)
  }

  override fun release() {
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
