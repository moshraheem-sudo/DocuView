package com.example.documentviewer.model

enum class DirectionMode(val jsValue: String) {
  AUTO("auto"),
  RTL("rtl"),
  LTR("ltr")
}

enum class PdfAnnotationTool(val jsValue: String) {
  NONE("none"),
  PEN("pen"),
  HIGHLIGHTER("highlighter"),
  UNDERLINE("underline"),
  RECT("rect"),
  ERASER("eraser")
}

data class PdfAnnotationUiState(
  val isToolbarVisible: Boolean = false,
  val activeTool: PdfAnnotationTool = PdfAnnotationTool.NONE,
  val selectedColorHex: String = "#E11D48",
  val strokeSize: Float = 3.5f,
  val strokeCount: Int = 0,
  val isSaving: Boolean = false
)

data class RendererCapabilities(
  val supportsSearch: Boolean,
  val supportsTextCopy: Boolean,
  val supportsRotation: Boolean,
  val supportsThumbnails: Boolean,
  val supportsBookmarks: Boolean,
  val supportsPresentationMode: Boolean,
  val supportsDirectionToggle: Boolean,
  val supportsExportPdf: Boolean,
  val supportsNightMode: Boolean,
  val supportsContinuousToggle: Boolean,
  val supportsPdfAnnotation: Boolean = false
)

data class SearchUiState(
  val isOpen: Boolean = false,
  val query: String = "",
  val currentMatch: Int = 0,
  val totalMatches: Int = 0
)

sealed interface ViewerUiState {
  data class Loading(val displayName: String, val format: DocumentFormat) : ViewerUiState

  data class Ready(
    val descriptor: DocumentDescriptor,
    val capabilities: RendererCapabilities,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val zoom: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val isNightMode: Boolean = false,
    val isFullscreen: Boolean = false,
    val isPresentationMode: Boolean = false,
    val isContinuousScroll: Boolean = true,
    val directionMode: DirectionMode = DirectionMode.AUTO,
    val search: SearchUiState = SearchUiState(),
    val annotation: PdfAnnotationUiState = PdfAnnotationUiState(),
    val outline: List<OutlineEntry> = emptyList(),
    val bookmarks: List<DocumentBookmark> = emptyList(),
    val slideSummaries: List<SlideSummary> = emptyList(),
    val thumbnails: Map<Int, String> = emptyMap(),
    val extractedPageText: String = "",
    val isExtractingText: Boolean = false,
    val hasNoTextLayer: Boolean = false
  ) : ViewerUiState

  data class Error(
    val displayName: String,
    val format: DocumentFormat,
    val message: String
  ) : ViewerUiState
}
