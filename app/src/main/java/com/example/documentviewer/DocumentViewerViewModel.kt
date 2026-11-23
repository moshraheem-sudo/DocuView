package com.example.documentviewer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.documentviewer.data.DocumentRepository
import com.example.documentviewer.data.DocumentSettings
import com.example.documentviewer.data.ReaderSettingsState
import com.example.documentviewer.engine.DocumentRenderer
import com.example.documentviewer.engine.PdfAnnotationSaver
import com.example.documentviewer.engine.PdfRendererModule
import com.example.documentviewer.engine.PowerPointRendererModule
import com.example.documentviewer.engine.RendererCallbacks
import com.example.documentviewer.engine.WordRendererModule
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.OutlineEntry
import com.example.documentviewer.model.PdfAnnotationTool
import com.example.documentviewer.model.SearchUiState
import com.example.documentviewer.model.SlideSummary
import com.example.documentviewer.model.ViewerUiState
import com.example.documentviewer.security.DocumentSecurityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ViewerSideEffect {
  data class CopyToClipboard(val text: String) : ViewerSideEffect
  data class ShowToastMessage(val resId: Int, val arg: Int? = null) : ViewerSideEffect
}

class DocumentViewerViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = DocumentRepository(application)
  private val settingsStore = DocumentSettings(application)
  private val securityManager = DocumentSecurityManager(application)

  val settingsState: StateFlow<ReaderSettingsState> = settingsStore.settingsFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = ReaderSettingsState()
  )

  private val _uiState = MutableStateFlow<ViewerUiState>(
    ViewerUiState.Loading(displayName = "", format = DocumentFormat.UNKNOWN)
  )
  val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

  private val _effects = MutableSharedFlow<ViewerSideEffect>(extraBufferCapacity = 8)
  val effects: SharedFlow<ViewerSideEffect> = _effects.asSharedFlow()

  private val _webViewGeneration = MutableStateFlow(0)
  val webViewGeneration: StateFlow<Int> = _webViewGeneration.asStateFlow()

  private var activeRenderer: DocumentRenderer? = null
  private var attachedWebView: WebView? = null
  private var bookmarksJob: Job? = null
  private var textExtractionTimeoutJob: Job? = null
  private var pendingDescriptor: DocumentDescriptor? = null
  private var hasLoadedIntoWebView = false
  private var pendingExportTargetUri: Uri? = null
  private var lastOpenIntent: Intent? = null
  private var rendererCrashRecoveryAttempts = 0

  fun openFromIntent(intent: Intent) {
    lastOpenIntent = Intent(intent)
    rendererCrashRecoveryAttempts = 0
    val uri = repository.openManager.extractIncomingUri(intent)
    val cachedPath = intent.getStringExtra(DocumentViewerActivity.EXTRA_CACHED_PATH)
    val docKey = intent.getStringExtra(DocumentViewerActivity.EXTRA_DOCUMENT_KEY)
    val displayNameExtra = intent.getStringExtra(DocumentViewerActivity.EXTRA_DISPLAY_NAME)
    val formatExtra = intent.getStringExtra(DocumentViewerActivity.EXTRA_FORMAT)
      ?.let { runCatching { DocumentFormat.valueOf(it) }.getOrNull() }
      ?: DocumentFormat.UNKNOWN

    if (uri == null && cachedPath.isNullOrBlank()) {
      _uiState.value = ViewerUiState.Error(
        displayName = displayNameExtra ?: "",
        format = formatExtra,
        message = getApplication<Application>().getString(R.string.file_unavailable_reselect)
      )
      return
    }

    _uiState.value = ViewerUiState.Loading(
      displayName = displayNameExtra ?: uri?.lastPathSegment ?: "Document",
      format = formatExtra
    )

    viewModelScope.launch {
      val settings = settingsStore.settingsFlow.first()
      val effectiveUriString = uri?.toString()
        ?: cachedPath?.let { Uri.fromFile(File(it)).toString() }
        ?: ""
      val result = if (!cachedPath.isNullOrBlank() && docKey != null && effectiveUriString.isNotBlank()) {
        val prelim = DocumentDescriptor(
          documentKey = docKey,
          uriString = effectiveUriString,
          displayName = displayNameExtra ?: File(cachedPath).name,
          mimeType = formatExtra.primaryMimeType,
          format = formatExtra,
          supportLevel = formatExtra.supportLevel,
          sizeBytes = 0L,
          cachedFilePath = cachedPath
        )
        repository.reopenFromDescriptor(prelim)
      } else if (uri != null) {
        repository.prepareDocumentFromUri(uri, intent.flags, docKey)
      } else {
        Result.failure(
          com.example.documentviewer.data.FileUnavailableException(
            getApplication<Application>().getString(R.string.file_unavailable_reselect)
          )
        )
      }

      result.onSuccess { descriptor ->
        setupRendererForDescriptor(descriptor, settings)
      }.onFailure { err ->
        val friendlyMessage = when (err) {
          is SecurityException,
          is com.example.documentviewer.data.FileUnavailableException ->
            getApplication<Application>().getString(R.string.file_unavailable_reselect)
          else -> err.message ?: getApplication<Application>().getString(R.string.file_unavailable_reselect)
        }
        _uiState.value = ViewerUiState.Error(
          displayName = displayNameExtra ?: "",
          format = formatExtra,
          message = friendlyMessage
        )
      }
    }
  }

  private fun setupRendererForDescriptor(
    descriptor: DocumentDescriptor,
    settings: ReaderSettingsState
  ) {
    activeRenderer?.release()
    hasLoadedIntoWebView = false
    pendingDescriptor = descriptor

    val renderer = when (descriptor.format.category) {
      DocumentCategory.PDF -> PdfRendererModule(securityManager, repository.openManager)
      DocumentCategory.WORD -> WordRendererModule(getApplication(), securityManager, repository.openManager)
      DocumentCategory.EXCEL -> com.example.documentviewer.engine.ExcelRendererModule(
        securityManager,
        repository.openManager
      )
      DocumentCategory.ACCESS -> com.example.documentviewer.engine.AccessRendererModule(
        getApplication(),
        securityManager,
        repository.openManager
      )
      DocumentCategory.POWERPOINT -> PowerPointRendererModule(getApplication(), securityManager, repository.openManager)
      DocumentCategory.HTML,
      DocumentCategory.CODE -> com.example.documentviewer.engine.HtmlAndCodeRendererModule(
        securityManager,
        repository.openManager
      )
      DocumentCategory.OTHER -> {
        _uiState.value = ViewerUiState.Error(
          displayName = descriptor.displayName,
          format = descriptor.format,
          message = "Unsupported format: ${descriptor.format.label}"
        )
        return
      }
    }
    activeRenderer = renderer

    _uiState.value = ViewerUiState.Ready(
      descriptor = descriptor,
      capabilities = renderer.capabilities,
      currentPage = descriptor.lastPage.coerceAtLeast(1),
      totalPages = descriptor.pageCount.coerceAtLeast(1),
      isNightMode = settings.defaultNightReader,
      isContinuousScroll = if (descriptor.format.category == DocumentCategory.PDF) settings.continuousPdfScroll else true
    )

    observeBookmarks(descriptor.documentKey)
    tryAttachWebViewIfReady()
  }

  fun attachWebView(webView: WebView) {
    if (attachedWebView === webView && hasLoadedIntoWebView) return
    attachedWebView = webView
    tryAttachWebViewIfReady()
  }

  fun detachAndDestroyWebView(webView: WebView) {
    if (attachedWebView === webView) {
      activeRenderer?.release()
      attachedWebView = null
      hasLoadedIntoWebView = false
    }
    securityManager.safelyDestroyWebView(webView)
  }

  fun retryLastDocument() {
    val desc = pendingDescriptor
    val intent = lastOpenIntent
    rendererCrashRecoveryAttempts = 0
    _webViewGeneration.value += 1
    if (desc != null) {
      viewModelScope.launch {
        val settings = settingsStore.settingsFlow.first()
        setupRendererForDescriptor(desc, settings)
      }
    } else if (intent != null) {
      openFromIntent(intent)
    }
  }

  private fun tryAttachWebViewIfReady() {
    val wv = attachedWebView ?: return
    val desc = pendingDescriptor ?: return
    val renderer = activeRenderer ?: return
    val readyState = _uiState.value as? ViewerUiState.Ready ?: return
    if (hasLoadedIntoWebView) return

    hasLoadedIntoWebView = true
    renderer.attachAndLoad(
      webView = wv,
      descriptor = desc,
      initialPage = readyState.currentPage,
      nightMode = readyState.isNightMode,
      continuousScroll = readyState.isContinuousScroll,
      directionMode = readyState.directionMode,
      callbacks = object : RendererCallbacks {
        override fun onDocumentLoaded(totalPages: Int) {
          updateReady {
            it.copy(
              totalPages = totalPages.coerceAtLeast(1),
              descriptor = it.descriptor.copy(pageCount = totalPages.coerceAtLeast(1))
            )
          }
          viewModelScope.launch {
            repository.updateProgress(desc.documentKey, readyState.currentPage, totalPages)
          }
          if (renderer.capabilities.supportsThumbnails && desc.format == DocumentFormat.PDF) {
            renderer.requestThumbnails(1, minOf(12, totalPages))
          }
        }

        override fun onDocumentError(message: String) {
          _uiState.value = ViewerUiState.Error(
            displayName = desc.displayName,
            format = desc.format,
            message = message
          )
        }

        override fun onPageChanged(currentPage: Int, totalPages: Int) {
          updateReady {
            it.copy(
              currentPage = currentPage.coerceAtLeast(1),
              totalPages = totalPages.coerceAtLeast(1),
              extractedPageText = if (it.currentPage != currentPage) "" else it.extractedPageText,
              isExtractingText = if (it.currentPage != currentPage) false else it.isExtractingText,
              hasNoTextLayer = if (it.currentPage != currentPage) false else it.hasNoTextLayer
            )
          }
          viewModelScope.launch {
            repository.updateProgress(desc.documentKey, currentPage, totalPages)
          }
        }

        override fun onZoomChanged(zoom: Float) {
          updateReady { it.copy(zoom = zoom) }
        }

        override fun onSearchResult(currentMatch: Int, totalMatches: Int, page: Int) {
          updateReady {
            it.copy(
              search = it.search.copy(
                currentMatch = currentMatch,
                totalMatches = totalMatches
              ),
              currentPage = page.coerceAtLeast(1)
            )
          }
        }

        override fun onOutlineLoaded(outline: List<OutlineEntry>) {
          updateReady { it.copy(outline = outline) }
        }

        override fun onSlideSummaries(slides: List<SlideSummary>) {
          updateReady { it.copy(slideSummaries = slides) }
        }

        override fun onThumbnailReady(pageNumber: Int, dataUrl: String) {
          updateReady {
            val updated = it.thumbnails.toMutableMap()
            updated[pageNumber] = dataUrl
            it.copy(thumbnails = updated)
          }
        }

        override fun onTextCopied(text: String) {
          _effects.tryEmit(ViewerSideEffect.CopyToClipboard(text))
        }

        override fun onPageTextExtracted(text: String) {
          textExtractionTimeoutJob?.cancel()
          val isMissing = text == "__DOCUVIEW_NO_TEXT_LAYER__" || text.isBlank()
          updateReady {
            it.copy(
              extractedPageText = if (isMissing) "" else text,
              isExtractingText = false,
              hasNoTextLayer = isMissing
            )
          }
        }

        override fun onAnnotationsChanged(count: Int) {
          updateReady {
            it.copy(
              annotation = it.annotation.copy(strokeCount = count)
            )
          }
        }

        override fun onAnnotationsSaved(json: String) {
          handleAnnotationsSavedJson(json)
        }

        override fun onRenderProcessGone(didCrash: Boolean) {
          handleRenderProcessGone(wv, desc, didCrash)
        }
      }
    )
  }

  private fun handleRenderProcessGone(
    deadWebView: WebView,
    descriptor: DocumentDescriptor,
    didCrash: Boolean
  ) {
    if (attachedWebView !== deadWebView) return
    activeRenderer?.release()
    attachedWebView = null
    hasLoadedIntoWebView = false

    if (rendererCrashRecoveryAttempts < 1) {
      rendererCrashRecoveryAttempts += 1
      _webViewGeneration.value += 1
    } else {
      _uiState.value = ViewerUiState.Error(
        displayName = descriptor.displayName,
        format = descriptor.format,
        message = if (didCrash) {
          "Document renderer stopped unexpectedly. Tap Retry to reload."
        } else {
          "Document renderer was reclaimed by the system. Tap Retry to reload."
        }
      )
    }
  }

  private fun handleAnnotationsSavedJson(json: String) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val exportUri = pendingExportTargetUri
    pendingExportTargetUri = null

    val strokesByPage = PdfAnnotationSaver.parseAnnotationsJson(json)
    val cachedPath = ready.descriptor.cachedFilePath
    if (cachedPath.isNullOrBlank()) {
      updateReady { it.copy(annotation = it.annotation.copy(isSaving = false)) }
      return
    }
    val pdfFile = File(cachedPath)

    viewModelScope.launch {
      updateReady { it.copy(annotation = it.annotation.copy(isSaving = true)) }
      val writableUri = runCatching { Uri.parse(ready.descriptor.uriString) }.getOrNull()

      if (strokesByPage.isNotEmpty()) {
        val bakeRes = PdfAnnotationSaver.bakeAnnotationsIntoPdf(
          context = getApplication(),
          targetPdfFile = pdfFile,
          annotationsByPage = strokesByPage,
          writableUri = writableUri
        )

        bakeRes.onSuccess { savedFile ->
        val updatedDescriptor = ready.descriptor.copy(
          sizeBytes = savedFile.length(),
          lastOpenedAt = System.currentTimeMillis()
        )
          pendingDescriptor = updatedDescriptor
          repository.recordSavedDescriptor(updatedDescriptor)

          activeRenderer?.clearAllAnnotations()
          activeRenderer?.setAnnotationTool(
            PdfAnnotationTool.NONE,
            ready.annotation.selectedColorHex,
            ready.annotation.strokeSize
          )

          updateReady {
            it.copy(
              descriptor = updatedDescriptor,
              annotation = it.annotation.copy(
                activeTool = PdfAnnotationTool.NONE,
                strokeCount = 0,
                isSaving = false
              ),
              thumbnails = emptyMap()
            )
          }

          // Reload the newly baked PDF in WebView so permanent changes are immediately displayed
          hasLoadedIntoWebView = false
          tryAttachWebViewIfReady()

          if (exportUri != null) {
            repository.saveDocumentToDestinationUri(updatedDescriptor, exportUri)
              .onSuccess {
                _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.file_saved_to_device))
              }
              .onFailure {
                _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.pdf_saved_success))
              }
          } else {
            _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.pdf_saved_success))
          }
        }.onFailure {
          updateReady { it.copy(annotation = it.annotation.copy(isSaving = false)) }
        }
      } else {
        updateReady { it.copy(annotation = it.annotation.copy(isSaving = false)) }
        if (exportUri != null) {
          repository.saveDocumentToDestinationUri(ready.descriptor, exportUri)
            .onSuccess {
              _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.file_saved_to_device))
            }
        } else {
          repository.recordSavedDescriptor(ready.descriptor)
          _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.pdf_saved_success))
        }
      }
    }
  }

  private fun observeBookmarks(documentKey: String) {
    bookmarksJob?.cancel()
    bookmarksJob = viewModelScope.launch {
      repository.observeBookmarks(documentKey).collect { list ->
        updateReady { it.copy(bookmarks = list) }
      }
    }
  }

  fun toggleAnnotationToolbar() {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val nextVisible = !ready.annotation.isToolbarVisible
    val nextTool = if (nextVisible) {
      if (ready.annotation.activeTool == PdfAnnotationTool.NONE) PdfAnnotationTool.PEN else ready.annotation.activeTool
    } else {
      PdfAnnotationTool.NONE
    }
    activeRenderer?.setAnnotationTool(
      nextTool,
      ready.annotation.selectedColorHex,
      ready.annotation.strokeSize
    )
    updateReady {
      it.copy(
        annotation = it.annotation.copy(
          isToolbarVisible = nextVisible,
          activeTool = nextTool
        )
      )
    }
  }

  fun selectAnnotationTool(tool: PdfAnnotationTool) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val nextTool = if (ready.annotation.activeTool == tool && tool != PdfAnnotationTool.NONE) {
      PdfAnnotationTool.NONE
    } else {
      tool
    }
    activeRenderer?.setAnnotationTool(
      nextTool,
      ready.annotation.selectedColorHex,
      ready.annotation.strokeSize
    )
    updateReady {
      it.copy(
        annotation = it.annotation.copy(
          isToolbarVisible = true,
          activeTool = nextTool
        )
      )
    }
  }

  fun selectAnnotationColor(colorHex: String) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val tool = if (ready.annotation.activeTool == PdfAnnotationTool.NONE ||
      ready.annotation.activeTool == PdfAnnotationTool.ERASER
    ) {
      PdfAnnotationTool.PEN
    } else {
      ready.annotation.activeTool
    }
    activeRenderer?.setAnnotationTool(tool, colorHex, ready.annotation.strokeSize)
    updateReady {
      it.copy(
        annotation = it.annotation.copy(
          activeTool = tool,
          selectedColorHex = colorHex
        )
      )
    }
  }

  fun undoAnnotation() {
    activeRenderer?.undoAnnotation()
  }

  fun clearPageAnnotations() {
    activeRenderer?.clearPageAnnotations()
  }

  fun savePdfAnnotationsInPlace() {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    if (ready.descriptor.format == DocumentFormat.PDF) {
      pendingExportTargetUri = null
      updateReady { it.copy(annotation = it.annotation.copy(isSaving = true)) }
      activeRenderer?.requestSaveAnnotations()
    } else {
      viewModelScope.launch {
        repository.recordSavedDescriptor(ready.descriptor)
        _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.pdf_saved_success))
      }
    }
  }

  fun saveDocumentCopyToUri(destinationUri: Uri) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    if (ready.descriptor.format == DocumentFormat.PDF && ready.annotation.strokeCount > 0) {
      pendingExportTargetUri = destinationUri
      updateReady { it.copy(annotation = it.annotation.copy(isSaving = true)) }
      activeRenderer?.requestSaveAnnotations()
    } else {
      viewModelScope.launch {
        repository.saveDocumentToDestinationUri(ready.descriptor, destinationUri)
          .onSuccess {
            _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.file_saved_to_device))
          }
      }
    }
  }

  fun goToPage(page: Int) {
    activeRenderer?.goToPage(page)
  }

  fun nextPage() {
    activeRenderer?.nextPage()
  }

  fun previousPage() {
    activeRenderer?.previousPage()
  }

  fun zoomIn() {
    val cur = (_uiState.value as? ViewerUiState.Ready)?.zoom ?: 1.0f
    val next = (cur + 0.25f).coerceAtMost(3.5f)
    activeRenderer?.setZoom(next)
    updateReady { it.copy(zoom = next) }
  }

  fun zoomOut() {
    val cur = (_uiState.value as? ViewerUiState.Ready)?.zoom ?: 1.0f
    val next = (cur - 0.25f).coerceAtLeast(0.5f)
    activeRenderer?.setZoom(next)
    updateReady { it.copy(zoom = next) }
  }

  fun resetZoom() {
    activeRenderer?.setZoom(1.0f)
    attachedWebView?.let { wv ->
      var steps = 0
      while (steps < 12 && wv.zoomOut()) {
        steps++
      }
    }
    updateReady { it.copy(zoom = 1.0f) }
  }

  fun rotateClockwise() {
    activeRenderer?.rotateClockwise()
    updateReady { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
  }

  fun toggleNightMode() {
    val cur = (_uiState.value as? ViewerUiState.Ready)?.isNightMode ?: false
    val next = !cur
    activeRenderer?.setNightMode(next)
    updateReady { it.copy(isNightMode = next) }
    viewModelScope.launch { settingsStore.setDefaultNightReader(next) }
  }

  fun toggleContinuousScroll() {
    val cur = (_uiState.value as? ViewerUiState.Ready)?.isContinuousScroll ?: true
    val next = !cur
    activeRenderer?.setContinuousScroll(next)
    updateReady { it.copy(isContinuousScroll = next) }
  }

  fun toggleFullscreen() {
    updateReady {
      val nextFs = !it.isFullscreen
      if (!nextFs && it.isPresentationMode) {
        activeRenderer?.setPresentationMode(false)
        it.copy(isFullscreen = false, isPresentationMode = false)
      } else {
        it.copy(isFullscreen = nextFs)
      }
    }
  }

  fun togglePresentationMode() {
    val cur = (_uiState.value as? ViewerUiState.Ready)?.isPresentationMode ?: false
    val next = !cur
    activeRenderer?.setPresentationMode(next)
    updateReady { it.copy(isPresentationMode = next, isFullscreen = next) }
  }

  fun cycleDirectionMode() {
    val cur = (_uiState.value as? ViewerUiState.Ready)?.directionMode ?: DirectionMode.AUTO
    val next = when (cur) {
      DirectionMode.AUTO -> DirectionMode.RTL
      DirectionMode.RTL -> DirectionMode.LTR
      DirectionMode.LTR -> DirectionMode.AUTO
    }
    activeRenderer?.setDirectionMode(next)
    updateReady { it.copy(directionMode = next) }
  }

  fun toggleSearchBar(open: Boolean) {
    if (!open) {
      activeRenderer?.search("")
    }
    updateReady {
      it.copy(
        search = if (open) it.search.copy(isOpen = true) else SearchUiState(isOpen = false)
      )
    }
  }

  fun search(query: String) {
    updateReady { it.copy(search = it.search.copy(query = query)) }
    activeRenderer?.search(query)
  }

  fun findNext() {
    activeRenderer?.findNext()
  }

  fun findPrevious() {
    activeRenderer?.findPrevious()
  }

  fun copySelectedText() {
    activeRenderer?.copySelectedText()
  }

  fun requestPageTextExtraction(pageOverride: Int? = null) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val targetPage = (pageOverride ?: ready.currentPage).coerceAtLeast(1)
    textExtractionTimeoutJob?.cancel()
    updateReady {
      it.copy(
        extractedPageText = "",
        isExtractingText = true,
        hasNoTextLayer = false
      )
    }
    activeRenderer?.extractPageText(targetPage)
    textExtractionTimeoutJob = viewModelScope.launch {
      delay(4500L)
      updateReady { cur ->
        if (cur.isExtractingText) {
          cur.copy(
            isExtractingText = false,
            hasNoTextLayer = cur.extractedPageText.isBlank()
          )
        } else {
          cur
        }
      }
    }
  }

  fun requestAllPagesTextExtraction() {
    textExtractionTimeoutJob?.cancel()
    updateReady {
      it.copy(
        extractedPageText = "",
        isExtractingText = true,
        hasNoTextLayer = false
      )
    }
    activeRenderer?.extractAllPagesText()
    textExtractionTimeoutJob = viewModelScope.launch {
      delay(8000L)
      updateReady { cur ->
        if (cur.isExtractingText) {
          cur.copy(
            isExtractingText = false,
            hasNoTextLayer = cur.extractedPageText.isBlank()
          )
        } else {
          cur
        }
      }
    }
  }

  fun copyDirectText(text: String) {
    _effects.tryEmit(ViewerSideEffect.CopyToClipboard(text))
  }

  fun requestThumbnailBatch(startPage: Int, endPage: Int) {
    activeRenderer?.requestThumbnails(startPage, endPage)
  }

  fun toggleFavorite() {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val nextFav = !ready.descriptor.isFavorite
    updateReady { it.copy(descriptor = it.descriptor.copy(isFavorite = nextFav)) }
    viewModelScope.launch {
      repository.toggleFavorite(ready.descriptor.documentKey, nextFav)
    }
  }

  fun addBookmarkForCurrentPage() {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    val page = ready.currentPage
    viewModelScope.launch {
      repository.addBookmark(
        documentKey = ready.descriptor.documentKey,
        pageNumber = page,
        label = "Page $page"
      )
      _effects.tryEmit(ViewerSideEffect.ShowToastMessage(R.string.bookmark_added, page))
    }
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch { repository.deleteBookmark(id) }
  }

  fun shareDocument(activityContext: Context) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    repository.openManager.shareDocument(activityContext, ready.descriptor)
  }

  fun printOrExportPdf(activityContext: Context) {
    val ready = _uiState.value as? ViewerUiState.Ready ?: return
    activeRenderer?.printOrExportPdf(activityContext, ready.descriptor)
  }

  private inline fun updateReady(transform: (ViewerUiState.Ready) -> ViewerUiState.Ready) {
    val cur = _uiState.value
    if (cur is ViewerUiState.Ready) {
      _uiState.value = transform(cur)
    }
  }

  override fun onCleared() {
    super.onCleared()
    activeRenderer?.release()
    activeRenderer = null
    attachedWebView?.let { securityManager.safelyDestroyWebView(it) }
    attachedWebView = null
    hasLoadedIntoWebView = false
  }
}
