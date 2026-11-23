package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.OutlineEntry
import com.example.documentviewer.model.PdfAnnotationTool
import com.example.documentviewer.model.RendererCapabilities
import com.example.documentviewer.model.SlideSummary
import com.example.documentviewer.security.DocumentSecurityManager
import org.json.JSONArray

interface RendererCallbacks {
  fun onDocumentLoaded(totalPages: Int)
  fun onDocumentError(message: String)
  fun onPageChanged(currentPage: Int, totalPages: Int)
  fun onZoomChanged(zoom: Float)
  fun onSearchResult(currentMatch: Int, totalMatches: Int, page: Int)
  fun onOutlineLoaded(outline: List<OutlineEntry>)
  fun onSlideSummaries(slides: List<SlideSummary>)
  fun onThumbnailReady(pageNumber: Int, dataUrl: String)
  fun onTextCopied(text: String)
  fun onPageTextExtracted(text: String) {}
  fun onAnnotationsChanged(count: Int) {}
  fun onAnnotationsSaved(json: String) {}
  fun onRenderProcessGone(didCrash: Boolean) {}
}

interface DocumentRenderer {
  val capabilities: RendererCapabilities

  fun attachAndLoad(
    webView: WebView,
    descriptor: DocumentDescriptor,
    initialPage: Int,
    nightMode: Boolean,
    continuousScroll: Boolean,
    directionMode: DirectionMode,
    callbacks: RendererCallbacks
  )

  fun goToPage(pageNumber: Int)
  fun nextPage()
  fun previousPage()
  fun setZoom(zoom: Float)
  fun rotateClockwise() {}
  fun search(query: String)
  fun findNext()
  fun findPrevious()
  fun setNightMode(enabled: Boolean)
  fun setContinuousScroll(enabled: Boolean) {}
  fun setPresentationMode(enabled: Boolean) {}
  fun setDirectionMode(mode: DirectionMode) {}
  fun requestThumbnails(startPage: Int, endPage: Int) {}
  fun copySelectedText()
  fun extractPageText(pageNumber: Int = 0) {}
  fun extractAllPagesText() {}
  fun setAnnotationTool(tool: PdfAnnotationTool, colorHex: String, strokeSize: Float) {}
  fun undoAnnotation() {}
  fun clearPageAnnotations() {}
  fun clearAllAnnotations() {}
  fun requestSaveAnnotations() {}
  fun printOrExportPdf(activityContext: Context, descriptor: DocumentDescriptor)
  fun release()
}

internal class ViewerJsBridge(private val callbacks: RendererCallbacks) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun onDocumentLoaded(totalPages: Int) {
    mainHandler.post { callbacks.onDocumentLoaded(totalPages.coerceAtLeast(1)) }
  }

  @JavascriptInterface
  fun onDocumentError(message: String?) {
    mainHandler.post { callbacks.onDocumentError(message ?: "Unknown rendering error") }
  }

  @JavascriptInterface
  fun onPageChanged(currentPage: Int, totalPages: Int) {
    mainHandler.post {
      callbacks.onPageChanged(currentPage.coerceAtLeast(1), totalPages.coerceAtLeast(1))
    }
  }

  @JavascriptInterface
  fun onZoomChanged(zoom: Double) {
    mainHandler.post { callbacks.onZoomChanged(zoom.toFloat()) }
  }

  @JavascriptInterface
  fun onSearchResult(currentMatch: Int, totalMatches: Int, page: Int) {
    mainHandler.post { callbacks.onSearchResult(currentMatch, totalMatches, page) }
  }

  @JavascriptInterface
  fun onOutlineLoaded(json: String?) {
    val entries = parseOutline(json)
    mainHandler.post { callbacks.onOutlineLoaded(entries) }
  }

  @JavascriptInterface
  fun onSlideSummaries(json: String?) {
    val slides = parseSlides(json)
    mainHandler.post { callbacks.onSlideSummaries(slides) }
  }

  @JavascriptInterface
  fun onThumbnailReady(pageNumber: Int, dataUrl: String?) {
    if (!dataUrl.isNullOrBlank()) {
      mainHandler.post { callbacks.onThumbnailReady(pageNumber, dataUrl) }
    }
  }

  @JavascriptInterface
  fun onTextCopied(text: String?) {
    mainHandler.post { callbacks.onTextCopied(text ?: "") }
  }

  @JavascriptInterface
  fun onPageTextExtracted(text: String?) {
    mainHandler.post { callbacks.onPageTextExtracted(text ?: "") }
  }

  @JavascriptInterface
  fun onAnnotationsChanged(count: Int) {
    mainHandler.post { callbacks.onAnnotationsChanged(count.coerceAtLeast(0)) }
  }

  @JavascriptInterface
  fun onAnnotationsSaved(json: String?) {
    mainHandler.post { callbacks.onAnnotationsSaved(json ?: "{}") }
  }

  private fun parseOutline(json: String?): List<OutlineEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(json)
      List(arr.length()) { i ->
        val obj = arr.getJSONObject(i)
        OutlineEntry(
          title = obj.optString("title", "Section"),
          page = obj.optInt("page", 1).coerceAtLeast(1),
          depth = obj.optInt("depth", 0)
        )
      }
    }.getOrDefault(emptyList())
  }

  private fun parseSlides(json: String?): List<SlideSummary> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(json)
      List(arr.length()) { i ->
        val obj = arr.getJSONObject(i)
        SlideSummary(
          index = obj.optInt("index", i + 1),
          snippet = obj.optString("snippet", "")
        )
      }
    }.getOrDefault(emptyList())
  }
}

internal fun createSessionUrl(
  securityManager: DocumentSecurityManager,
  descriptor: DocumentDescriptor,
  inlineJsonBytes: ByteArray? = null
): String {
  val cached = descriptor.cachedFilePath?.let { java.io.File(it) }?.takeIf { it.exists() }
  val sourceUri = runCatching { Uri.parse(descriptor.uriString) }.getOrNull()
  val session = securityManager.createSession(
    fileName = descriptor.displayName,
    mimeType = descriptor.mimeType,
    sourceUri = sourceUri,
    cachedFile = cached,
    inlinePayloadBytes = inlineJsonBytes
  )
  return securityManager.documentUrlFor(session)
}
