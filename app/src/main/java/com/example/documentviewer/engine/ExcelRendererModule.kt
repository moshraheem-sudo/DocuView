package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.RendererCapabilities
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.security.DocumentSecurityManager
import org.json.JSONObject

class ExcelRendererModule(
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
  private var currentSheet = 1
  private var totalSheets = 1

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
    webView.addJavascriptInterface(ViewerJsBridge(object : RendererCallbacks by callbacks {
      override fun onDocumentLoaded(totalPages: Int) {
        this@ExcelRendererModule.totalSheets = totalPages.coerceAtLeast(1)
        callbacks.onDocumentLoaded(totalPages)
        if (initialPage in 2..this@ExcelRendererModule.totalSheets) {
          goToPage(initialPage)
        }
      }

      override fun onPageChanged(currentPage: Int, totalPages: Int) {
        this@ExcelRendererModule.currentSheet = currentPage.coerceAtLeast(1)
        this@ExcelRendererModule.totalSheets = totalPages.coerceAtLeast(1)
        callbacks.onPageChanged(currentPage, totalPages)
      }
    }), "AndroidBridge")

    val docUrl = createSessionUrl(securityManager, descriptor)
    val encodedDoc = Uri.encode(docUrl)
    val ext = descriptor.displayName.substringAfterLast('.', "").lowercase()
    val viewerUrl = "${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/excel/excel_viewer.html" +
      "?doc=$encodedDoc&ext=${Uri.encode(ext)}&night=${if (nightMode) 1 else 0}&dir=${directionMode.jsValue}"
    webView.loadUrl(viewerUrl)
  }

  override fun goToPage(pageNumber: Int) {
    val target = pageNumber.coerceIn(1, totalSheets.coerceAtLeast(1))
    currentSheet = target
    evalJs("window.DocuExcel && window.DocuExcel.goToPage($target);")
  }

  override fun nextPage() {
    if (currentSheet < totalSheets) goToPage(currentSheet + 1)
  }

  override fun previousPage() {
    if (currentSheet > 1) goToPage(currentSheet - 1)
  }

  override fun setZoom(zoom: Float) {
    evalJs("window.DocuExcel && window.DocuExcel.setZoom(${zoom.coerceIn(0.5f, 3.0f)});")
  }

  override fun search(query: String) {
    val escaped = JSONObject.quote(query)
    evalJs("window.DocuExcel && window.DocuExcel.search($escaped);")
  }

  override fun findNext() {
    evalJs("window.DocuExcel && window.DocuExcel.findNext();")
  }

  override fun findPrevious() {
    evalJs("window.DocuExcel && window.DocuExcel.findPrev();")
  }

  override fun setNightMode(enabled: Boolean) {
    evalJs("window.DocuExcel && window.DocuExcel.setNightMode($enabled);")
  }

  override fun setDirectionMode(mode: DirectionMode) {
    evalJs("window.DocuExcel && window.DocuExcel.setDirectionMode('${mode.jsValue}');")
  }

  override fun copySelectedText() {
    evalJs("window.DocuExcel && window.DocuExcel.getSelectedText();")
  }

  override fun extractPageText(pageNumber: Int) {
    val target = if (pageNumber > 0) pageNumber else currentSheet
    evalJs("window.DocuExcel && window.DocuExcel.extractPageText($target);")
  }

  override fun extractAllPagesText() {
    evalJs("window.DocuExcel && window.DocuExcel.extractAllPagesText();")
  }

  override fun printOrExportPdf(activityContext: Context, descriptor: DocumentDescriptor) {
    val wv = webView ?: return
    val adapter = wv.createPrintDocumentAdapter(descriptor.displayName)
    openManager.printAdapter(activityContext, descriptor.displayName, adapter, landscape = true)
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
