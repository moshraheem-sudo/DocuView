package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.RendererCapabilities
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.resolver.Ole2LegacyParser
import com.example.documentviewer.security.DocumentSecurityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

class WordRendererModule(
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
  private var currentPage = 1
  private var totalPages = 1

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
        this@WordRendererModule.totalPages = totalPages
        callbacks.onDocumentLoaded(totalPages)
      }

      override fun onPageChanged(currentPage: Int, totalPages: Int) {
        this@WordRendererModule.currentPage = currentPage
        this@WordRendererModule.totalPages = totalPages
        callbacks.onPageChanged(currentPage, totalPages)
      }
    }), "AndroidBridge")

    val isLegacyDoc = descriptor.format == DocumentFormat.DOC
    val docUrl = if (isLegacyDoc) {
      val jsonBytes = buildLegacyDocPayload(descriptor)
      createSessionUrl(securityManager, descriptor, inlineJsonBytes = jsonBytes)
    } else {
      createSessionUrl(securityManager, descriptor)
    }

    val encodedDoc = Uri.encode(docUrl)
    val viewerUrl = "${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/word/word_viewer.html" +
      "?doc=$encodedDoc&legacy=${if (isLegacyDoc) 1 else 0}&night=${if (nightMode) 1 else 0}&dir=${directionMode.jsValue}"
    webView.loadUrl(viewerUrl)
  }

  private fun buildLegacyDocPayload(descriptor: DocumentDescriptor): ByteArray {
    val paragraphs = runCatching {
      val cached = descriptor.cachedFilePath?.let { File(it) }?.takeIf { it.exists() }
      val stream = if (cached != null) {
        FileInputStream(cached)
      } else {
        context.contentResolver.openInputStream(Uri.parse(descriptor.uriString))
      }
      stream?.use { Ole2LegacyParser.inspect(it).extractedParagraphs } ?: emptyList()
    }.getOrDefault(emptyList())

    val arr = JSONArray()
    paragraphs.forEach { arr.put(it) }
    return JSONObject().put("paragraphs", arr).toString().toByteArray(Charsets.UTF_8)
  }

  override fun goToPage(pageNumber: Int) {
    val target = pageNumber.coerceIn(1, totalPages.coerceAtLeast(1))
    currentPage = target
    evalJs("window.DocuWord && window.DocuWord.goToPage($target);")
  }

  override fun nextPage() {
    if (currentPage < totalPages) goToPage(currentPage + 1)
  }

  override fun previousPage() {
    if (currentPage > 1) goToPage(currentPage - 1)
  }

  override fun setZoom(zoom: Float) {
    evalJs("window.DocuWord && window.DocuWord.setZoom(${zoom.coerceIn(0.5f, 3.0f)});")
  }

  override fun search(query: String) {
    val escaped = JSONObject.quote(query)
    evalJs("window.DocuWord && window.DocuWord.search($escaped);")
  }

  override fun findNext() {
    evalJs("window.DocuWord && window.DocuWord.findNext();")
  }

  override fun findPrevious() {
    evalJs("window.DocuWord && window.DocuWord.findPrev();")
  }

  override fun setNightMode(enabled: Boolean) {
    evalJs("window.DocuWord && window.DocuWord.setNightMode($enabled);")
  }

  override fun setDirectionMode(mode: DirectionMode) {
    evalJs("window.DocuWord && window.DocuWord.setDirectionMode('${mode.jsValue}');")
  }

  override fun copySelectedText() {
    evalJs("window.DocuWord && window.DocuWord.getSelectedText();")
  }

  override fun extractPageText(pageNumber: Int) {
    val target = if (pageNumber > 0) pageNumber else currentPage
    evalJs("window.DocuWord && window.DocuWord.extractPageText($target);")
  }

  override fun extractAllPagesText() {
    evalJs("window.DocuWord && window.DocuWord.extractAllPagesText();")
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
