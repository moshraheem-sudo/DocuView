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

class PowerPointRendererModule(
  private val context: Context,
  private val securityManager: DocumentSecurityManager,
  private val openManager: DocumentOpenManager
) : DocumentRenderer {

  override val capabilities = RendererCapabilities(
    supportsSearch = true,
    supportsTextCopy = true,
    supportsRotation = false,
    supportsThumbnails = true,
    supportsBookmarks = false,
    supportsPresentationMode = true,
    supportsDirectionToggle = false,
    supportsExportPdf = true,
    supportsNightMode = true,
    supportsContinuousToggle = true
  )

  private var webView: WebView? = null
  private var currentSlide = 1
  private var totalSlides = 1

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
    this.currentSlide = initialPage.coerceAtLeast(1)

    securityManager.hardenWebView(webView)
    val assetLoader = securityManager.buildAssetLoader()
    webView.webViewClient = securityManager.buildStrictWebViewClient(
      assetLoader = assetLoader,
      onRenderProcessGone = { didCrash -> callbacks.onRenderProcessGone(didCrash) }
    )
    webView.removeJavascriptInterface("AndroidBridge")
    webView.addJavascriptInterface(ViewerJsBridge(object : RendererCallbacks by callbacks {
      override fun onDocumentLoaded(totalPages: Int) {
        this@PowerPointRendererModule.totalSlides = totalPages
        callbacks.onDocumentLoaded(totalPages)
      }

      override fun onPageChanged(currentPage: Int, totalPages: Int) {
        this@PowerPointRendererModule.currentSlide = currentPage
        this@PowerPointRendererModule.totalSlides = totalPages
        callbacks.onPageChanged(currentPage, totalPages)
      }
    }), "AndroidBridge")

    val isLegacyPpt = descriptor.format == DocumentFormat.PPT
    val docUrl = if (isLegacyPpt) {
      val jsonBytes = buildLegacyPptPayload(descriptor)
      createSessionUrl(securityManager, descriptor, inlineJsonBytes = jsonBytes)
    } else {
      createSessionUrl(securityManager, descriptor)
    }

    val encodedDoc = Uri.encode(docUrl)
    val viewerUrl = "${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/pptx/pptx_viewer.html" +
      "?doc=$encodedDoc&legacy=${if (isLegacyPpt) 1 else 0}&night=${if (nightMode) 1 else 0}"
    webView.loadUrl(viewerUrl)
  }

  private fun buildLegacyPptPayload(descriptor: DocumentDescriptor): ByteArray {
    val lines = runCatching {
      val cached = descriptor.cachedFilePath?.let { File(it) }?.takeIf { it.exists() }
      val stream = if (cached != null) {
        FileInputStream(cached)
      } else {
        context.contentResolver.openInputStream(Uri.parse(descriptor.uriString))
      }
      stream?.use { Ole2LegacyParser.inspect(it).extractedParagraphs } ?: emptyList()
    }.getOrDefault(emptyList())

    // Group extracted lines into readable slide cards (approx. 6 lines per card)
    val slidesArr = JSONArray()
    if (lines.isNotEmpty()) {
      lines.chunked(6).forEach { chunk ->
        val cardArr = JSONArray()
        chunk.forEach { cardArr.put(it) }
        slidesArr.put(cardArr)
      }
    }
    return JSONObject().put("slides", slidesArr).toString().toByteArray(Charsets.UTF_8)
  }

  override fun goToPage(pageNumber: Int) {
    val target = pageNumber.coerceIn(1, totalSlides.coerceAtLeast(1))
    currentSlide = target
    evalJs("window.DocuPptx && window.DocuPptx.goToSlide($target);")
  }

  override fun nextPage() {
    if (currentSlide < totalSlides) goToPage(currentSlide + 1)
  }

  override fun previousPage() {
    if (currentSlide > 1) goToPage(currentSlide - 1)
  }

  override fun setZoom(zoom: Float) {
    evalJs("window.DocuPptx && window.DocuPptx.setZoom(${zoom.coerceIn(0.5f, 3.0f)});")
  }

  override fun search(query: String) {
    val escaped = JSONObject.quote(query)
    evalJs("window.DocuPptx && window.DocuPptx.search($escaped);")
  }

  override fun findNext() {
    evalJs("window.DocuPptx && window.DocuPptx.findNext();")
  }

  override fun findPrevious() {
    evalJs("window.DocuPptx && window.DocuPptx.findPrev();")
  }

  override fun setNightMode(enabled: Boolean) {
    evalJs("window.DocuPptx && window.DocuPptx.setNightMode($enabled);")
  }

  override fun setContinuousScroll(enabled: Boolean) {
    evalJs("window.DocuPptx && window.DocuPptx.setSingleSlideMode(${!enabled});")
  }

  override fun setPresentationMode(enabled: Boolean) {
    evalJs("window.DocuPptx && window.DocuPptx.setPresentationMode($enabled);")
  }

  override fun copySelectedText() {
    evalJs("window.DocuPptx && window.DocuPptx.getSelectedText();")
  }

  override fun extractPageText(pageNumber: Int) {
    val target = if (pageNumber > 0) pageNumber else currentSlide
    evalJs("window.DocuPptx && window.DocuPptx.extractPageText($target);")
  }

  override fun extractAllPagesText() {
    evalJs("window.DocuPptx && window.DocuPptx.extractAllPagesText();")
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
