package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.PdfAnnotationTool
import com.example.documentviewer.model.RendererCapabilities
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.security.DocumentSecurityManager
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfRendererModule(
  private val securityManager: DocumentSecurityManager,
  private val openManager: DocumentOpenManager
) : DocumentRenderer {

  override val capabilities = RendererCapabilities(
    supportsSearch = true,
    supportsTextCopy = true,
    supportsRotation = true,
    supportsThumbnails = true,
    supportsBookmarks = true,
    supportsPresentationMode = false,
    supportsDirectionToggle = false,
    supportsExportPdf = true,
    supportsNightMode = true,
    supportsContinuousToggle = true,
    supportsPdfAnnotation = true
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
    directionMode: com.example.documentviewer.model.DirectionMode,
    callbacks: RendererCallbacks
  ) {
    this.webView = webView
    this.currentPage = initialPage.coerceAtLeast(1)

    securityManager.hardenWebView(webView)
    val assetLoader = securityManager.buildAssetLoader()
    webView.webViewClient = securityManager.buildStrictWebViewClient(
      assetLoader = assetLoader,
      onRenderProcessGone = { didCrash -> callbacks.onRenderProcessGone(didCrash) }
    )
    webView.removeJavascriptInterface("AndroidBridge")
    webView.addJavascriptInterface(ViewerJsBridge(object : RendererCallbacks by callbacks {
      override fun onDocumentLoaded(totalPages: Int) {
        this@PdfRendererModule.totalPages = totalPages
        callbacks.onDocumentLoaded(totalPages)
      }

      override fun onPageChanged(currentPage: Int, totalPages: Int) {
        this@PdfRendererModule.currentPage = currentPage
        this@PdfRendererModule.totalPages = totalPages
        callbacks.onPageChanged(currentPage, totalPages)
      }
    }), "AndroidBridge")

    val docUrl = createSessionUrl(securityManager, descriptor)
    val encodedDoc = Uri.encode(docUrl)
    val viewerUrl = "${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/pdf/pdf_viewer.html" +
      "?doc=$encodedDoc&page=$currentPage&continuous=${if (continuousScroll) 1 else 0}&night=${if (nightMode) 1 else 0}"
    webView.loadUrl(viewerUrl)
  }

  override fun goToPage(pageNumber: Int) {
    val target = pageNumber.coerceIn(1, totalPages.coerceAtLeast(1))
    currentPage = target
    evalJs("window.DocuPdf && window.DocuPdf.goToPage($target);")
  }

  override fun nextPage() {
    if (currentPage < totalPages) goToPage(currentPage + 1)
  }

  override fun previousPage() {
    if (currentPage > 1) goToPage(currentPage - 1)
  }

  override fun setZoom(zoom: Float) {
    evalJs("window.DocuPdf && window.DocuPdf.setZoom(${zoom.coerceIn(0.5f, 4.0f)});")
  }

  override fun rotateClockwise() {
    evalJs("window.DocuPdf && window.DocuPdf.rotateClockwise();")
  }

  override fun search(query: String) {
    val escaped = JSONObject.quote(query)
    evalJs("window.DocuPdf && window.DocuPdf.search($escaped);")
  }

  override fun findNext() {
    evalJs("window.DocuPdf && window.DocuPdf.findNext();")
  }

  override fun findPrevious() {
    evalJs("window.DocuPdf && window.DocuPdf.findPrev();")
  }

  override fun setNightMode(enabled: Boolean) {
    evalJs("window.DocuPdf && window.DocuPdf.setNightMode($enabled);")
  }

  override fun setContinuousScroll(enabled: Boolean) {
    evalJs("window.DocuPdf && window.DocuPdf.setContinuousMode($enabled);")
  }

  override fun requestThumbnails(startPage: Int, endPage: Int) {
    evalJs("window.DocuPdf && window.DocuPdf.renderThumbnails($startPage, $endPage);")
  }

  override fun copySelectedText() {
    evalJs("window.DocuPdf && window.DocuPdf.getSelectedText();")
  }

  override fun extractPageText(pageNumber: Int) {
    val target = if (pageNumber > 0) pageNumber else currentPage
    evalJs("window.DocuPdf && window.DocuPdf.extractPageText($target);")
  }

  override fun extractAllPagesText() {
    evalJs("window.DocuPdf && window.DocuPdf.extractAllPagesText();")
  }

  override fun setAnnotationTool(tool: PdfAnnotationTool, colorHex: String, strokeSize: Float) {
    val qTool = JSONObject.quote(tool.jsValue)
    val qColor = JSONObject.quote(colorHex)
    evalJs("window.DocuPdf && window.DocuPdf.setAnnotationTool($qTool, $qColor, $strokeSize);")
  }

  override fun undoAnnotation() {
    evalJs("window.DocuPdf && window.DocuPdf.undoAnnotation();")
  }

  override fun clearPageAnnotations() {
    evalJs("window.DocuPdf && window.DocuPdf.clearPageAnnotations();")
  }

  override fun clearAllAnnotations() {
    evalJs("window.DocuPdf && window.DocuPdf.clearAllAnnotations();")
  }

  override fun requestSaveAnnotations() {
    evalJs("window.DocuPdf && window.DocuPdf.requestSaveAnnotations();")
  }

  override fun printOrExportPdf(activityContext: Context, descriptor: DocumentDescriptor) {
    val adapter = PdfStreamPrintAdapter(activityContext, descriptor)
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

  private class PdfStreamPrintAdapter(
    private val context: Context,
    private val descriptor: DocumentDescriptor
  ) : PrintDocumentAdapter() {
    override fun onLayout(
      oldAttributes: PrintAttributes?,
      newAttributes: PrintAttributes,
      cancellationSignal: CancellationSignal?,
      callback: LayoutResultCallback,
      extras: Bundle?
    ) {
      if (cancellationSignal?.isCanceled == true) {
        callback.onLayoutCancelled()
        return
      }
      val info = PrintDocumentInfo.Builder(descriptor.displayName)
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
        .setPageCount(
          if (descriptor.pageCount > 0) descriptor.pageCount else PrintDocumentInfo.PAGE_COUNT_UNKNOWN
        )
        .build()
      callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
      pages: Array<out PageRange>?,
      destination: ParcelFileDescriptor,
      cancellationSignal: CancellationSignal?,
      callback: WriteResultCallback
    ) {
      try {
        val cached = descriptor.cachedFilePath?.let { File(it) }?.takeIf { it.exists() }
        val inputStream = if (cached != null) {
          FileInputStream(cached)
        } else {
          context.contentResolver.openInputStream(Uri.parse(descriptor.uriString))
        }
        if (inputStream == null) {
          callback.onWriteFailed("Cannot open PDF source stream")
          return
        }
        inputStream.use { input ->
          FileOutputStream(destination.fileDescriptor).use { output ->
            input.copyTo(output, bufferSize = 32 * 1024)
          }
        }
        if (cancellationSignal?.isCanceled == true) {
          callback.onWriteCancelled()
        } else {
          callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }
      } catch (e: Exception) {
        callback.onWriteFailed(e.message ?: "Print failed")
      }
    }
  }
}
