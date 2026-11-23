package com.example.documentviewer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatTextdirectionRToL
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.documentviewer.data.ThemeMode
import com.example.documentviewer.model.DirectionMode
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.PdfAnnotationTool
import com.example.documentviewer.model.PdfAnnotationUiState
import com.example.documentviewer.model.SupportLevel
import com.example.documentviewer.model.ViewerUiState
import com.example.documentviewer.ui.BookmarksBottomSheet
import com.example.documentviewer.ui.CompatibilityWarningsSheet
import com.example.documentviewer.ui.DocumentInfoSheet
import com.example.documentviewer.ui.FormatBadge
import com.example.documentviewer.ui.GoToPageDialog
import com.example.documentviewer.ui.LocalizedAppContainer
import com.example.documentviewer.ui.SlideNavigationHud
import com.example.documentviewer.ui.TextCopyAndExtractSheet
import com.example.documentviewer.ui.ThumbnailsBottomSheet
import com.example.documentviewer.ui.ViewerSearchBar
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SupportPartialAmber

class DocumentViewerActivity : ComponentActivity() {

  companion object {
    const val EXTRA_DOCUMENT_KEY = "extra_document_key"
    const val EXTRA_DISPLAY_NAME = "extra_display_name"
    const val EXTRA_CACHED_PATH = "extra_cached_path"
    const val EXTRA_FORMAT = "extra_format"
    const val EXTRA_SOURCE_URI = "extra_source_uri"

    fun createLaunchIntent(context: Context, descriptor: DocumentDescriptor): Intent =
      Intent(context, DocumentViewerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        val cachedFile = descriptor.cachedFilePath
          ?.let { File(it) }
          ?.takeIf { it.exists() && it.canRead() }
        if (cachedFile != null) {
          data = Uri.fromFile(cachedFile)
        } else if (descriptor.uriString.startsWith("file://")) {
          data = Uri.parse(descriptor.uriString)
        }
        putExtra(EXTRA_SOURCE_URI, descriptor.uriString)
        putExtra(EXTRA_DOCUMENT_KEY, descriptor.documentKey)
        putExtra(EXTRA_DISPLAY_NAME, descriptor.displayName)
        putExtra(EXTRA_CACHED_PATH, descriptor.cachedFilePath)
        putExtra(EXTRA_FORMAT, descriptor.format.name)
      }
  }

  private val viewModel: DocumentViewerViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (savedInstanceState == null) {
      viewModel.openFromIntent(intent)
    }

    setContent {
      val settings by viewModel.settingsState.collectAsStateWithLifecycle()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
      }

      val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
      ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
          val destUri = result.data?.data
          if (destUri != null) {
            viewModel.saveDocumentCopyToUri(destUri)
          }
        }
      }

      fun launchSaveCopyDialog(descriptor: DocumentDescriptor) {
        val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type = descriptor.mimeType.ifBlank { descriptor.format.primaryMimeType }
          putExtra(Intent.EXTRA_TITLE, descriptor.displayName)
        }
        try {
          saveDocumentLauncher.launch(createIntent)
        } catch (_: SecurityException) {
          Toast.makeText(
            this@DocumentViewerActivity,
            getString(R.string.error_permission_denied),
            Toast.LENGTH_SHORT
          ).show()
        } catch (_: ActivityNotFoundException) {
          Toast.makeText(
            this@DocumentViewerActivity,
            getString(R.string.error_no_external_app),
            Toast.LENGTH_SHORT
          ).show()
        }
      }

      LaunchedEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) {
          window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
      }

      val isFullscreen = (uiState as? ViewerUiState.Ready)?.isFullscreen == true
      LaunchedEffect(isFullscreen) {
        applySystemBarsFullscreen(isFullscreen)
      }

      LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
          when (effect) {
            is ViewerSideEffect.CopyToClipboard -> {
              if (effect.text.isNotBlank()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Document Text", effect.text))
                Toast.makeText(
                  this@DocumentViewerActivity,
                  getString(R.string.copied_to_clipboard),
                  Toast.LENGTH_SHORT
                ).show()
              } else {
                Toast.makeText(
                  this@DocumentViewerActivity,
                  getString(R.string.nothing_selected),
                  Toast.LENGTH_SHORT
                ).show()
              }
            }
            is ViewerSideEffect.ShowToastMessage -> {
              val msg = if (effect.arg != null) {
                getString(effect.resId, effect.arg)
              } else {
                getString(effect.resId)
              }
              Toast.makeText(this@DocumentViewerActivity, msg, Toast.LENGTH_SHORT).show()
            }
          }
        }
      }

      val webViewGeneration by viewModel.webViewGeneration.collectAsStateWithLifecycle()

      LocalizedAppContainer(appLanguage = settings.appLanguage) { isArabic ->
        MyApplicationTheme(darkTheme = darkTheme) {
          DocumentViewerScreen(
            uiState = uiState,
            webViewGeneration = webViewGeneration,
            isArabic = isArabic,
            onAttachWebView = viewModel::attachWebView,
            onDetachWebView = viewModel::detachAndDestroyWebView,
            onRetry = viewModel::retryLastDocument,
            onNavigateBack = { finish() },
            onShare = { viewModel.shareDocument(this) },
            onPrintPdf = { viewModel.printOrExportPdf(this) },
            onSaveCopyToDevice = { desc -> launchSaveCopyDialog(desc) },
            viewModel = viewModel
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    viewModel.openFromIntent(intent)
  }

  private fun applySystemBarsFullscreen(fullscreen: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (fullscreen) {
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentViewerScreen(
  uiState: ViewerUiState,
  webViewGeneration: Int,
  isArabic: Boolean,
  onAttachWebView: (WebView) -> Unit,
  onDetachWebView: (WebView) -> Unit,
  onRetry: () -> Unit,
  onNavigateBack: () -> Unit,
  onShare: () -> Unit,
  onPrintPdf: () -> Unit,
  onSaveCopyToDevice: (DocumentDescriptor) -> Unit,
  viewModel: DocumentViewerViewModel
) {
  var showThumbnails by remember { mutableStateOf(false) }
  var showBookmarks by remember { mutableStateOf(false) }
  var showGoToPage by remember { mutableStateOf(false) }
  var showWarnings by remember { mutableStateOf(false) }
  var showDocInfo by remember { mutableStateOf(false) }
  var showTextExtractor by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }

  val readyState = uiState as? ViewerUiState.Ready

  BackHandler(
    enabled = readyState != null &&
      (readyState.isFullscreen ||
        readyState.isPresentationMode ||
        readyState.search.isOpen ||
        readyState.annotation.isToolbarVisible)
  ) {
    when {
      readyState?.isFullscreen == true -> viewModel.toggleFullscreen()
      readyState?.annotation?.isToolbarVisible == true -> viewModel.toggleAnnotationToolbar()
      readyState?.search?.isOpen == true -> viewModel.toggleSearchBar(false)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      if (readyState == null || !readyState.isFullscreen) {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          navigationIcon = {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("viewer_back_button")
            ) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
          },
          title = {
            val title = when (uiState) {
              is ViewerUiState.Loading -> uiState.displayName
              is ViewerUiState.Ready -> uiState.descriptor.displayName
              is ViewerUiState.Error -> uiState.displayName
            }
            Column {
              Text(
                text = title.ifBlank { stringResource(R.string.viewer_activity_title) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (readyState != null) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  FormatBadge(format = readyState.descriptor.format)
                  if (readyState.descriptor.format.category != DocumentCategory.HTML &&
                    readyState.descriptor.format.category != DocumentCategory.CODE
                  ) {
                    Text(
                      text = "${readyState.currentPage} / ${readyState.totalPages}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      softWrap = false,
                      modifier = Modifier.clickable { showGoToPage = true }
                    )
                  }
                }
              }
            }
          },
          actions = {
            if (readyState != null) {
              if (readyState.descriptor.compatibilityWarnings.isNotEmpty() ||
                readyState.descriptor.supportLevel == SupportLevel.PARTIAL
              ) {
                IconButton(
                  onClick = { showWarnings = true },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("viewer_warnings_button")
                ) {
                  Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = stringResource(R.string.compat_warnings_title),
                    tint = SupportPartialAmber
                  )
                }
              }
              if (readyState.capabilities.supportsSearch) {
                IconButton(
                  onClick = { viewModel.toggleSearchBar(!readyState.search.isOpen) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("viewer_search_button")
                ) {
                  Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                }
              }
              IconButton(
                onClick = { viewModel.toggleFavorite() },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("viewer_favorite_button")
              ) {
                Icon(
                  imageVector = if (readyState.descriptor.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                  contentDescription = stringResource(R.string.action_favorite),
                  tint = if (readyState.descriptor.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurface
                )
              }
              Box {
                IconButton(
                  onClick = { showMoreMenu = true },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("viewer_more_menu")
                ) {
                  Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_settings))
                }
                DropdownMenu(
                  expanded = showMoreMenu,
                  onDismissRequest = { showMoreMenu = false }
                ) {
                  if (readyState.capabilities.supportsPdfAnnotation) {
                    DropdownMenuItem(
                      leadingIcon = { Icon(Icons.Outlined.Draw, contentDescription = null) },
                      text = { Text(stringResource(R.string.action_annotate)) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.toggleAnnotationToolbar()
                      }
                    )
                    DropdownMenuItem(
                      leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                      text = { Text(stringResource(R.string.action_save_file)) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.savePdfAnnotationsInPlace()
                      }
                    )
                  }
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_save_copy_device)) },
                    onClick = {
                      showMoreMenu = false
                      onSaveCopyToDevice(readyState.descriptor)
                    }
                  )
                  if (readyState.capabilities.supportsTextCopy) {
                    DropdownMenuItem(
                      leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                      text = { Text(stringResource(R.string.action_extract_text)) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.requestPageTextExtraction()
                        showTextExtractor = true
                      }
                    )
                  }
                  if (readyState.capabilities.supportsExportPdf) {
                    DropdownMenuItem(
                      leadingIcon = { Icon(Icons.Outlined.Print, contentDescription = null) },
                      text = { Text(stringResource(R.string.action_print_pdf)) },
                      onClick = {
                        showMoreMenu = false
                        onPrintPdf()
                      }
                    )
                  }
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_share)) },
                    onClick = {
                      showMoreMenu = false
                      onShare()
                    }
                  )
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_night_mode)) },
                    onClick = {
                      showMoreMenu = false
                      viewModel.toggleNightMode()
                    }
                  )
                  if (readyState.capabilities.supportsContinuousToggle) {
                    DropdownMenuItem(
                      leadingIcon = { Icon(Icons.Outlined.ViewDay, contentDescription = null) },
                      text = { Text(stringResource(R.string.action_scroll_mode)) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.toggleContinuousScroll()
                      }
                    )
                  }
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Fullscreen, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_fullscreen)) },
                    onClick = {
                      showMoreMenu = false
                      viewModel.toggleFullscreen()
                    }
                  )
                  DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_doc_info)) },
                    onClick = {
                      showMoreMenu = false
                      showDocInfo = true
                    }
                  )
                }
              }
            }
          }
        )
      }
    },
    bottomBar = {
      if (readyState != null && !readyState.isFullscreen) {
        FormatAdaptiveBottomToolbar(
          state = readyState,
          onOpenThumbnails = { showThumbnails = true },
          onOpenBookmarks = { showBookmarks = true },
          onOpenGoToPage = { showGoToPage = true },
          onOpenTextExtractor = {
            viewModel.requestPageTextExtraction()
            showTextExtractor = true
          },
          onToggleAnnotate = viewModel::toggleAnnotationToolbar,
          onSavePdf = viewModel::savePdfAnnotationsInPlace,
          onRotate = viewModel::rotateClockwise,
          onZoomOut = viewModel::zoomOut,
          onZoomIn = viewModel::zoomIn,
          onResetZoom = viewModel::resetZoom,
          onToggleNightMode = viewModel::toggleNightMode,
          onToggleDirection = viewModel::cycleDirectionMode,
          onTogglePresentation = viewModel::togglePresentationMode,
          onPrevPage = viewModel::previousPage,
          onNextPage = viewModel::nextPage,
          onPrintPdf = onPrintPdf
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = readyState?.search?.isOpen == true) {
          if (readyState != null) {
            ViewerSearchBar(
              search = readyState.search,
              onQueryChange = { q -> viewModel.search(q) },
              onSearchSubmit = { q -> viewModel.search(q) },
              onFindPrev = viewModel::findPrevious,
              onFindNext = viewModel::findNext,
              onClose = { viewModel.toggleSearchBar(false) }
            )
          }
        }

        AnimatedVisibility(
          visible = readyState != null &&
            readyState.capabilities.supportsPdfAnnotation &&
            readyState.annotation.isToolbarVisible
        ) {
          if (readyState != null) {
            PdfAnnotationToolbarBar(
              annotation = readyState.annotation,
              onSelectTool = viewModel::selectAnnotationTool,
              onSelectColor = viewModel::selectAnnotationColor,
              onUndo = viewModel::undoAnnotation,
              onClearPage = viewModel::clearPageAnnotations,
              onSaveInPlace = viewModel::savePdfAnnotationsInPlace,
              onSaveCopyToDevice = { onSaveCopyToDevice(readyState.descriptor) },
              onClose = viewModel::toggleAnnotationToolbar
            )
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          if (uiState !is ViewerUiState.Error) {
            key(webViewGeneration) {
              AndroidView(
                factory = { ctx ->
                  WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                      ViewGroup.LayoutParams.MATCH_PARENT,
                      ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnTouchListener { v, event ->
                      if (event.pointerCount >= 2) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                      }
                      false
                    }
                    onAttachWebView(this)
                  }
                },
                update = { wv -> onAttachWebView(wv) },
                onRelease = { wv -> onDetachWebView(wv) },
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("viewer_webview")
              )
            }
          }

          if (uiState is ViewerUiState.Loading || readyState?.annotation?.isSaving == true) {
            Surface(
              color = MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
              modifier = Modifier.fillMaxSize()
            ) {
              Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text(
                  text = if (readyState?.annotation?.isSaving == true) {
                    stringResource(R.string.action_save_file)
                  } else {
                    stringResource(R.string.loading_document)
                  },
                  style = MaterialTheme.typography.bodyLarge
                )
              }
            }
          }

          if (uiState is ViewerUiState.Error) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
              )
              Spacer(Modifier.height(12.dp))
              Text(
                text = stringResource(R.string.error_open_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(Modifier.height(18.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                  onClick = onRetry,
                  modifier = Modifier.testTag("viewer_retry_button")
                ) {
                  Text(stringResource(R.string.retry))
                }
                Button(
                  onClick = onNavigateBack,
                  colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                  Text(stringResource(R.string.action_back))
                }
              }
            }
          }

          // Slide navigation HUD for PowerPoint
          if (readyState != null &&
            readyState.descriptor.format.category == DocumentCategory.POWERPOINT &&
            readyState.totalPages > 0
          ) {
            SlideNavigationHud(
              currentSlide = readyState.currentPage,
              totalSlides = readyState.totalPages,
              onPrev = viewModel::previousPage,
              onNext = viewModel::nextPage,
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .navigationBarsPadding()
            )
          }

          // Exit fullscreen / presentation floating button
          if (readyState?.isFullscreen == true) {
            FloatingActionButton(
              onClick = viewModel::toggleFullscreen,
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .testTag("exit_fullscreen_button")
            ) {
              Icon(
                Icons.Outlined.FullscreenExit,
                contentDescription = stringResource(R.string.action_exit_fullscreen)
              )
            }
          }
        }
      }
    }
  }

  if (readyState != null && showThumbnails) {
    ThumbnailsBottomSheet(
      category = readyState.descriptor.format.category,
      currentPage = readyState.currentPage,
      totalPages = readyState.totalPages,
      thumbnails = readyState.thumbnails,
      slideSummaries = readyState.slideSummaries,
      onRequestBatch = viewModel::requestThumbnailBatch,
      onSelectPage = viewModel::goToPage,
      onDismiss = { showThumbnails = false }
    )
  }

  if (readyState != null && showBookmarks) {
    BookmarksBottomSheet(
      currentPage = readyState.currentPage,
      bookmarks = readyState.bookmarks,
      outline = readyState.outline,
      onAddCurrentPage = viewModel::addBookmarkForCurrentPage,
      onDeleteBookmark = viewModel::deleteBookmark,
      onSelectPage = viewModel::goToPage,
      onDismiss = { showBookmarks = false }
    )
  }

  if (readyState != null && showGoToPage) {
    GoToPageDialog(
      currentPage = readyState.currentPage,
      totalPages = readyState.totalPages,
      onGoToPage = viewModel::goToPage,
      onDismiss = { showGoToPage = false }
    )
  }

  if (readyState != null && showWarnings) {
    CompatibilityWarningsSheet(
      warnings = readyState.descriptor.compatibilityWarnings,
      isArabic = isArabic,
      onDismiss = { showWarnings = false }
    )
  }

  if (readyState != null && showDocInfo) {
    DocumentInfoSheet(
      descriptor = readyState.descriptor,
      totalPages = readyState.totalPages,
      onDismiss = { showDocInfo = false }
    )
  }

  if (readyState != null && showTextExtractor) {
    TextCopyAndExtractSheet(
      extractedText = readyState.extractedPageText,
      currentPage = readyState.currentPage,
      isExtractingText = readyState.isExtractingText,
      hasNoTextLayer = readyState.hasNoTextLayer,
      totalPages = readyState.totalPages,
      onExtractAllPages = { viewModel.requestAllPagesTextExtraction() },
      onCopyText = viewModel::copyDirectText,
      onDismiss = { showTextExtractor = false }
    )
  }
}

@Composable
private fun PdfAnnotationToolbarBar(
  annotation: PdfAnnotationUiState,
  onSelectTool: (PdfAnnotationTool) -> Unit,
  onSelectColor: (String) -> Unit,
  onUndo: () -> Unit,
  onClearPage: () -> Unit,
  onSaveInPlace: () -> Unit,
  onSaveCopyToDevice: () -> Unit,
  onClose: () -> Unit
) {
  val palette = listOf(
    "#E11D48" to Color(0xFFE11D48), // Crimson Red
    "#FACC15" to Color(0xFFFACC15), // Highlight Yellow
    "#2563EB" to Color(0xFF2563EB), // Royal Blue
    "#16A34A" to Color(0xFF16A34A), // Emerald Green
    "#9333EA" to Color(0xFF9333EA)  // Vivid Purple
  )

  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 5.dp,
    shadowElevation = 4.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("pdf_annotation_toolbar")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Top row: Tools + Undo + Clear + Close
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilterChip(
          selected = annotation.activeTool == PdfAnnotationTool.PEN,
          onClick = { onSelectTool(PdfAnnotationTool.PEN) },
          leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.tool_pen)) },
          modifier = Modifier.testTag("tool_pen_chip")
        )
        FilterChip(
          selected = annotation.activeTool == PdfAnnotationTool.HIGHLIGHTER,
          onClick = { onSelectTool(PdfAnnotationTool.HIGHLIGHTER) },
          leadingIcon = { Icon(Icons.Outlined.BorderColor, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.tool_highlighter)) },
          modifier = Modifier.testTag("tool_highlighter_chip")
        )
        FilterChip(
          selected = annotation.activeTool == PdfAnnotationTool.UNDERLINE,
          onClick = { onSelectTool(PdfAnnotationTool.UNDERLINE) },
          leadingIcon = { Icon(Icons.Outlined.HorizontalRule, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.tool_underline)) },
          modifier = Modifier.testTag("tool_line_chip")
        )
        FilterChip(
          selected = annotation.activeTool == PdfAnnotationTool.RECT,
          onClick = { onSelectTool(PdfAnnotationTool.RECT) },
          leadingIcon = { Icon(Icons.Outlined.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.tool_rect)) },
          modifier = Modifier.testTag("tool_rect_chip")
        )
        FilterChip(
          selected = annotation.activeTool == PdfAnnotationTool.ERASER,
          onClick = { onSelectTool(PdfAnnotationTool.ERASER) },
          leadingIcon = { Icon(Icons.Outlined.AutoFixNormal, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.tool_eraser)) },
          modifier = Modifier.testTag("tool_eraser_chip")
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

      // Bottom row: Color swatches + Undo + Clear + Save buttons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        palette.forEach { (hex, color) ->
          val isSelected = annotation.selectedColorHex.equals(hex, ignoreCase = true)
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(color)
              .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.2f),
                shape = CircleShape
              )
              .clickable { onSelectColor(hex) }
          ) {
            if (isSelected) {
              Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = if (hex == "#FACC15") Color.Black else Color.White,
                modifier = Modifier.size(15.dp)
              )
            }
          }
        }

        Spacer(Modifier.width(4.dp))

        IconButton(
          onClick = onUndo,
          enabled = annotation.strokeCount > 0,
          modifier = Modifier
            .size(36.dp)
            .testTag("annotation_undo_button")
        ) {
          Icon(
            Icons.AutoMirrored.Outlined.Undo,
            contentDescription = stringResource(R.string.action_undo),
            modifier = Modifier.size(20.dp)
          )
        }

        IconButton(
          onClick = onClearPage,
          enabled = annotation.strokeCount > 0,
          modifier = Modifier
            .size(36.dp)
            .testTag("annotation_clear_button")
        ) {
          Icon(
            Icons.Outlined.DeleteSweep,
            contentDescription = stringResource(R.string.action_clear_annotations),
            modifier = Modifier.size(20.dp)
          )
        }

        Button(
          onClick = onSaveInPlace,
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .height(34.dp)
            .testTag("annotation_save_pdf_button")
        ) {
          Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(5.dp))
          Text(
            text = stringResource(R.string.action_save_file),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Button(
          onClick = onSaveCopyToDevice,
          colors = ButtonDefaults.filledTonalButtonColors(),
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier
            .height(34.dp)
            .testTag("annotation_save_copy_button")
        ) {
          Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.action_save_copy_device),
            style = MaterialTheme.typography.labelMedium
          )
        }

        IconButton(
          onClick = onClose,
          modifier = Modifier.size(34.dp)
        ) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(R.string.action_close),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun FormatAdaptiveBottomToolbar(
  state: ViewerUiState.Ready,
  onOpenThumbnails: () -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenGoToPage: () -> Unit,
  onOpenTextExtractor: () -> Unit,
  onToggleAnnotate: () -> Unit,
  onSavePdf: () -> Unit,
  onRotate: () -> Unit,
  onZoomOut: () -> Unit,
  onZoomIn: () -> Unit,
  onResetZoom: () -> Unit,
  onToggleNightMode: () -> Unit,
  onToggleDirection: () -> Unit,
  onTogglePresentation: () -> Unit,
  onPrevPage: () -> Unit,
  onNextPage: () -> Unit,
  onPrintPdf: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 3.dp,
    shadowElevation = 6.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
      HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp)
          .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        when (state.descriptor.format.category) {
          DocumentCategory.PDF -> {
            IconButton(
              onClick = onOpenThumbnails,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.GridView,
                contentDescription = stringResource(R.string.action_thumbnails),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onToggleAnnotate,
              modifier = Modifier
                .size(38.dp)
                .testTag("bottom_annotate_button")
            ) {
              Icon(
                Icons.Outlined.Draw,
                contentDescription = stringResource(R.string.action_annotate),
                tint = if (state.annotation.isToolbarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onSavePdf,
              modifier = Modifier
                .size(38.dp)
                .testTag("bottom_save_pdf_button")
            ) {
              Icon(
                Icons.Outlined.Save,
                contentDescription = stringResource(R.string.action_save_file),
                tint = if (state.annotation.strokeCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onOpenTextExtractor,
              modifier = Modifier
                .size(38.dp)
                .testTag("toolbar_copy_text_button")
            ) {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.action_extract_text),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onOpenBookmarks,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.Bookmarks,
                contentDescription = stringResource(R.string.action_bookmarks),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onRotate,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.RotateRight,
                contentDescription = stringResource(R.string.action_rotate),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onToggleNightMode,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.DarkMode,
                contentDescription = stringResource(R.string.action_night_mode),
                tint = if (state.isNightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
          }
          DocumentCategory.WORD,
          DocumentCategory.HTML,
          DocumentCategory.CODE -> {
            IconButton(
              onClick = onToggleDirection,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.FormatTextdirectionRToL,
                contentDescription = stringResource(R.string.action_rtl_toggle),
                tint = if (state.directionMode != DirectionMode.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onOpenTextExtractor,
              modifier = Modifier
                .size(38.dp)
                .testTag("toolbar_copy_text_button")
            ) {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.action_extract_text),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onZoomOut,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.ZoomOut,
                contentDescription = stringResource(R.string.action_zoom_out),
                modifier = Modifier.size(20.dp)
              )
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onResetZoom() }
            ) {
              Text(
                text = "${(state.zoom * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            IconButton(
              onClick = onZoomIn,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.ZoomIn,
                contentDescription = stringResource(R.string.action_zoom_in),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onToggleNightMode,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.DarkMode,
                contentDescription = stringResource(R.string.action_night_mode),
                tint = if (state.isNightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onPrintPdf,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.Print,
                contentDescription = stringResource(R.string.action_print_pdf),
                modifier = Modifier.size(20.dp)
              )
            }
          }
          DocumentCategory.POWERPOINT -> {
            IconButton(
              onClick = onOpenThumbnails,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.GridView,
                contentDescription = stringResource(R.string.action_thumbnails),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onTogglePresentation,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.Slideshow,
                contentDescription = stringResource(R.string.action_presentation),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onOpenTextExtractor,
              modifier = Modifier
                .size(38.dp)
                .testTag("toolbar_copy_text_button")
            ) {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.action_extract_text),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onZoomOut,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.ZoomOut,
                contentDescription = stringResource(R.string.action_zoom_out),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onZoomIn,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.ZoomIn,
                contentDescription = stringResource(R.string.action_zoom_in),
                modifier = Modifier.size(20.dp)
              )
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenGoToPage() }
            ) {
              Text(
                text = "${state.currentPage}/${state.totalPages}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            IconButton(
              onClick = onPrintPdf,
              modifier = Modifier.size(38.dp)
            ) {
              Icon(
                Icons.Outlined.Print,
                contentDescription = stringResource(R.string.action_print_pdf),
                modifier = Modifier.size(20.dp)
              )
            }
          }
          DocumentCategory.EXCEL,
          DocumentCategory.ACCESS -> {
            IconButton(
              onClick = onPrevPage,
              enabled = state.currentPage > 1,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.action_prev),
                modifier = Modifier.size(20.dp)
              )
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenGoToPage() }
            ) {
              Text(
                text = "${state.currentPage}/${state.totalPages}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            IconButton(
              onClick = onNextPage,
              enabled = state.currentPage < state.totalPages,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onToggleDirection,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.Outlined.FormatTextdirectionRToL,
                contentDescription = stringResource(R.string.action_rtl_toggle),
                tint = if (state.directionMode != DirectionMode.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onOpenTextExtractor,
              modifier = Modifier
                .size(36.dp)
                .testTag("toolbar_copy_text_button")
            ) {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.action_extract_text),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onZoomOut,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.Outlined.ZoomOut,
                contentDescription = stringResource(R.string.action_zoom_out),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onZoomIn,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.Outlined.ZoomIn,
                contentDescription = stringResource(R.string.action_zoom_in),
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onToggleNightMode,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.Outlined.DarkMode,
                contentDescription = stringResource(R.string.action_night_mode),
                tint = if (state.isNightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
              )
            }
            IconButton(
              onClick = onPrintPdf,
              modifier = Modifier.size(36.dp)
            ) {
              Icon(
                Icons.Outlined.Print,
                contentDescription = stringResource(R.string.action_print_pdf),
                modifier = Modifier.size(20.dp)
              )
            }
          }
          DocumentCategory.OTHER -> Unit
        }
      }
    }
  }
}
