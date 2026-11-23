package com.example.documentviewer.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.documentviewer.data.AppLanguage
import com.example.documentviewer.data.SystemFolderItem
import com.example.documentviewer.data.ThemeMode
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.SupportLevel
import com.example.ui.theme.AccessAccent
import com.example.ui.theme.CodeAccent
import com.example.ui.theme.ExcelAccent
import com.example.ui.theme.HtmlAccent
import com.example.ui.theme.PdfAccent
import com.example.ui.theme.PptAccent
import com.example.ui.theme.SupportFullGreen
import com.example.ui.theme.SupportPartialAmber
import com.example.ui.theme.WordAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  uiState: HomeUiState,
  isArabic: Boolean,
  onPickDocument: (DocumentCategory?) -> Unit,
  onOpenDocument: (DocumentDescriptor) -> Unit,
  onToggleFavorite: (DocumentDescriptor) -> Unit,
  onRemoveRecent: (DocumentDescriptor) -> Unit,
  onSelectTab: (HomeTab) -> Unit,
  onSelectCategory: (DocumentCategory?) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onFoldersSearchQueryChange: (String) -> Unit = {},
  onThemeModeChange: (ThemeMode) -> Unit,
  onLanguageChange: (AppLanguage) -> Unit,
  onContinuousPdfChange: (Boolean) -> Unit,
  onKeepScreenOnChange: (Boolean) -> Unit,
  onClearHistory: () -> Unit,
  onErrorConsumed: () -> Unit,
  onCycleSortOrder: () -> Unit = {},
  onSelectSortOrder: (SortOrder) -> Unit = {},
  onSelectFolder: (String?) -> Unit = {},
  onLinkSystemFolder: () -> Unit = {},
  onRequestStoragePermission: () -> Unit = {},
  onCreateNewFolder: (String, SystemFolderItem?) -> Unit = { _, _ -> },
  onAddFileToFolder: (SystemFolderItem) -> Unit = {},
  onRescanDevice: () -> Unit = {}
) {
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      onErrorConsumed()
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 700.dp
    val gridColumns = if (maxWidth >= 900.dp) 3 else if (isWideScreen) 2 else 1

    Scaffold(
      contentWindowInsets = WindowInsets.statusBars,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
              )
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
              ) {
                Text(
                  text = "v1.3.0",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
              }
            }
          },
          actions = {
            IconButton(
              onClick = {
                val next = if (isArabic) AppLanguage.ENGLISH else AppLanguage.ARABIC
                onLanguageChange(next)
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("language_toggle_button")
            ) {
              Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = stringResource(R.string.action_language)
              )
            }
            IconButton(
              onClick = {
                val nextTheme = when (uiState.settings.themeMode) {
                  ThemeMode.DARK -> ThemeMode.LIGHT
                  else -> ThemeMode.DARK
                }
                onThemeModeChange(nextTheme)
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("theme_toggle_button")
            ) {
              Icon(
                imageVector = Icons.Outlined.Brightness4,
                contentDescription = stringResource(R.string.action_theme)
              )
            }
          }
        )
      },
      floatingActionButton = {
        if (uiState.selectedTab != HomeTab.ENGINES) {
          FloatingActionButton(
            onClick = { onPickDocument(null) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
              .navigationBarsPadding()
              .testTag("open_document_fab")
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = stringResource(R.string.open_document_full)
            )
          }
        }
      },
      bottomBar = {
        if (!isWideScreen) {
          NavigationBar {
            NavigationBarItem(
              selected = uiState.selectedTab == HomeTab.RECENT,
              onClick = { onSelectTab(HomeTab.RECENT) },
              icon = { Icon(Icons.Outlined.History, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_recent), maxLines = 1) },
              modifier = Modifier.testTag("tab_recent")
            )
            NavigationBarItem(
              selected = uiState.selectedTab == HomeTab.FOLDERS,
              onClick = { onSelectTab(HomeTab.FOLDERS) },
              icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_folders), maxLines = 1) },
              modifier = Modifier.testTag("tab_folders")
            )
            NavigationBarItem(
              selected = uiState.selectedTab == HomeTab.FAVORITES,
              onClick = { onSelectTab(HomeTab.FAVORITES) },
              icon = { Icon(Icons.Outlined.StarBorder, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_favorites), maxLines = 1) },
              modifier = Modifier.testTag("tab_favorites")
            )
            NavigationBarItem(
              selected = uiState.selectedTab == HomeTab.ENGINES,
              onClick = { onSelectTab(HomeTab.ENGINES) },
              icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_settings), maxLines = 1) },
              modifier = Modifier.testTag("tab_formats")
            )
          }
        }
      }
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        if (isWideScreen) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface
          ) {
            Spacer(Modifier.height(12.dp))
            NavigationRailItem(
              selected = uiState.selectedTab == HomeTab.RECENT,
              onClick = { onSelectTab(HomeTab.RECENT) },
              icon = { Icon(Icons.Outlined.History, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_recent)) },
              modifier = Modifier.testTag("tab_recent")
            )
            NavigationRailItem(
              selected = uiState.selectedTab == HomeTab.FOLDERS,
              onClick = { onSelectTab(HomeTab.FOLDERS) },
              icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_folders)) },
              modifier = Modifier.testTag("tab_folders")
            )
            NavigationRailItem(
              selected = uiState.selectedTab == HomeTab.FAVORITES,
              onClick = { onSelectTab(HomeTab.FAVORITES) },
              icon = { Icon(Icons.Outlined.StarBorder, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_favorites)) },
              modifier = Modifier.testTag("tab_favorites")
            )
            NavigationRailItem(
              selected = uiState.selectedTab == HomeTab.ENGINES,
              onClick = { onSelectTab(HomeTab.ENGINES) },
              icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
              label = { Text(stringResource(R.string.tab_settings)) },
              modifier = Modifier.testTag("tab_formats")
            )
          }
        }

        Column(modifier = Modifier.fillMaxSize()) {
          if (uiState.isOpeningDocument) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }

          when (uiState.selectedTab) {
            HomeTab.ENGINES -> {
              SettingsTabContent(
                settings = uiState.settings,
                onThemeChange = onThemeModeChange,
                onLanguageChange = onLanguageChange,
                onContinuousPdfChange = onContinuousPdfChange,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onClearHistory = onClearHistory,
                modifier = Modifier.fillMaxSize()
              )
            }
            HomeTab.FOLDERS -> {
              SystemFoldersExplorerView(
                folders = uiState.systemFolders,
                allDeviceFiles = uiState.allSystemFiles,
                selectedFolderId = uiState.selectedFolderId,
                hasStoragePermission = uiState.hasStoragePermission,
                searchQuery = uiState.foldersSearchQuery,
                sortOrder = uiState.sortOrder,
                gridColumns = gridColumns,
                onSearchQueryChange = onFoldersSearchQueryChange,
                onSelectSortOrder = onSelectSortOrder,
                onSelectFolder = onSelectFolder,
                onOpenDocument = onOpenDocument,
                onToggleFavorite = onToggleFavorite,
                onLinkSystemFolder = onLinkSystemFolder,
                onRequestStoragePermission = onRequestStoragePermission,
                onCreateNewFolder = onCreateNewFolder,
                onAddFileToFolder = onAddFileToFolder,
                onRescanDevice = onRescanDevice,
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f)
              )
            }
            HomeTab.RECENT, HomeTab.FAVORITES -> {
              val docs = if (uiState.selectedTab == HomeTab.FAVORITES) {
                uiState.favoriteDocuments
              } else {
                uiState.recentDocuments
              }

              LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 92.dp, top = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f)
              ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  LibraryHeaderSection(
                    uiState = uiState,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectCategory = onSelectCategory,
                    onPickDocument = onPickDocument,
                    onCycleSortOrder = onCycleSortOrder,
                    onSelectSortOrder = onSelectSortOrder
                  )
                }

                if (docs.isEmpty()) {
                  item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibraryState(
                      isFavorites = uiState.selectedTab == HomeTab.FAVORITES,
                      onPickDocument = { onPickDocument(null) },
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                } else {
                  items(docs, key = { it.documentKey }) { doc ->
                    DocumentCardItem(
                      descriptor = doc,
                      onClick = { onOpenDocument(doc) },
                      onToggleFavorite = { onToggleFavorite(doc) },
                      onRemove = { onRemoveRecent(doc) }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryHeaderSection(
  uiState: HomeUiState,
  onSearchQueryChange: (String) -> Unit,
  onSelectCategory: (DocumentCategory?) -> Unit,
  onPickDocument: (DocumentCategory?) -> Unit,
  onCycleSortOrder: () -> Unit,
  onSelectSortOrder: (SortOrder) -> Unit
) {
  val searchQuery = uiState.searchQuery
  val selectedCategory = uiState.selectedCategory

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 2.dp, bottom = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    // Compact row: Search field + Open Document button side-by-side
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
          Text(
            text = stringResource(R.string.search_library_hint),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        leadingIcon = {
          Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(
                Icons.Outlined.Clear,
                contentDescription = stringResource(R.string.action_close),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .testTag("library_search_input")
      )

      Button(
        onClick = { onPickDocument(null) },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("open_document_button")
      ) {
        Icon(
          Icons.Outlined.FolderOpen,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.open_document),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false
        )
      }
    }

    // Category + Newest/Oldest Filter Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("filter_chips_row")
    ) {
      item {
        val countSuffix = if (uiState.totalDocumentCount > 0) " (${uiState.totalDocumentCount})" else ""
        FilterChip(
          selected = selectedCategory == null,
          onClick = { onSelectCategory(null) },
          label = { Text(stringResource(R.string.filter_all) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_all")
        )
      }
      item {
        FilterChip(
          selected = uiState.sortOrder == SortOrder.RECENT_OPENED,
          onClick = { onSelectSortOrder(SortOrder.RECENT_OPENED) },
          leadingIcon = {
            Icon(
              Icons.Outlined.ArrowDownward,
              contentDescription = null,
              modifier = Modifier.size(15.dp)
            )
          },
          label = { Text(stringResource(R.string.sort_recent), maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("sort_newest_chip")
        )
      }
      item {
        FilterChip(
          selected = uiState.sortOrder == SortOrder.OLDEST_OPENED,
          onClick = { onSelectSortOrder(SortOrder.OLDEST_OPENED) },
          leadingIcon = {
            Icon(
              Icons.Outlined.ArrowUpward,
              contentDescription = null,
              modifier = Modifier.size(15.dp)
            )
          },
          label = { Text(stringResource(R.string.sort_oldest), maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("sort_oldest_chip")
        )
      }
      item {
        val countSuffix = if (uiState.pdfCount > 0) " (${uiState.pdfCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.PDF,
          onClick = {
            onSelectCategory(if (selectedCategory == DocumentCategory.PDF) null else DocumentCategory.PDF)
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.PictureAsPdf,
              contentDescription = null,
              tint = PdfAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_pdf) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_pdf")
        )
      }
      item {
        val countSuffix = if (uiState.wordCount > 0) " (${uiState.wordCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.WORD,
          onClick = {
            onSelectCategory(if (selectedCategory == DocumentCategory.WORD) null else DocumentCategory.WORD)
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.Description,
              contentDescription = null,
              tint = WordAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_word) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_word")
        )
      }
      item {
        val countSuffix = if (uiState.pptCount > 0) " (${uiState.pptCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.POWERPOINT,
          onClick = {
            onSelectCategory(
              if (selectedCategory == DocumentCategory.POWERPOINT) null else DocumentCategory.POWERPOINT
            )
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.Slideshow,
              contentDescription = null,
              tint = PptAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_powerpoint) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_powerpoint")
        )
      }
      item {
        val countSuffix = if (uiState.excelCount > 0) " (${uiState.excelCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.EXCEL,
          onClick = {
            onSelectCategory(
              if (selectedCategory == DocumentCategory.EXCEL) null else DocumentCategory.EXCEL
            )
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.TableChart,
              contentDescription = null,
              tint = ExcelAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_excel) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_excel")
        )
      }
      item {
        val countSuffix = if (uiState.accessCount > 0) " (${uiState.accessCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.ACCESS,
          onClick = {
            onSelectCategory(
              if (selectedCategory == DocumentCategory.ACCESS) null else DocumentCategory.ACCESS
            )
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.Storage,
              contentDescription = null,
              tint = AccessAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_access) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_access")
        )
      }
      item {
        val countSuffix = if (uiState.htmlCount > 0) " (${uiState.htmlCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.HTML,
          onClick = {
            onSelectCategory(
              if (selectedCategory == DocumentCategory.HTML) null else DocumentCategory.HTML
            )
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.Language,
              contentDescription = null,
              tint = HtmlAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_html) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_html")
        )
      }
      item {
        val countSuffix = if (uiState.codeCount > 0) " (${uiState.codeCount})" else ""
        FilterChip(
          selected = selectedCategory == DocumentCategory.CODE,
          onClick = {
            onSelectCategory(
              if (selectedCategory == DocumentCategory.CODE) null else DocumentCategory.CODE
            )
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.Code,
              contentDescription = null,
              tint = CodeAccent,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(R.string.filter_code) + countSuffix, maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("filter_code")
        )
      }
      item {
        val sortLabelRes = when (uiState.sortOrder) {
          SortOrder.NAME_ASC -> R.string.sort_name
          SortOrder.SIZE_DESC -> R.string.sort_size
          else -> R.string.sort_name
        }
        AssistChip(
          onClick = onCycleSortOrder,
          leadingIcon = {
            Icon(
              Icons.AutoMirrored.Outlined.Sort,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
          },
          label = { Text(stringResource(sortLabelRes), maxLines = 1, softWrap = false) },
          modifier = Modifier.testTag("sort_order_chip")
        )
      }
    }
  }
}

@Composable
private fun DocumentCardItem(
  descriptor: DocumentDescriptor,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRemove: () -> Unit
) {
  val context = LocalContext.current
  val formattedSize = remember(descriptor.sizeBytes) {
    if (descriptor.sizeBytes > 0) Formatter.formatShortFileSize(context, descriptor.sizeBytes) else ""
  }
  val relativeTime = remember(descriptor.lastOpenedAt) {
    DateUtils.getRelativeTimeSpanString(
      descriptor.lastOpenedAt,
      System.currentTimeMillis(),
      DateUtils.MINUTE_IN_MILLIS
    ).toString()
  }
  val exactDate = remember(descriptor.lastOpenedAt) {
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(descriptor.lastOpenedAt))
  }
  val isRecentFile = remember(descriptor.lastOpenedAt) {
    (System.currentTimeMillis() - descriptor.lastOpenedAt) <= 24L * 60L * 60L * 1000L
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    tonalElevation = 1.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag("document_card_${descriptor.documentKey}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = descriptor.format.tintColor,
        modifier = Modifier.size(44.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = when (descriptor.format.category) {
              DocumentCategory.PDF -> Icons.Outlined.PictureAsPdf
              DocumentCategory.WORD -> Icons.Outlined.Description
              DocumentCategory.POWERPOINT -> Icons.Outlined.Slideshow
              DocumentCategory.EXCEL -> Icons.Outlined.TableChart
              DocumentCategory.ACCESS -> Icons.Outlined.Storage
              DocumentCategory.HTML -> Icons.Outlined.Language
              DocumentCategory.CODE -> Icons.Outlined.Code
              DocumentCategory.OTHER -> Icons.Outlined.Description
            },
            contentDescription = descriptor.format.label,
            tint = descriptor.format.accentColor,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Spacer(Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = descriptor.displayName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          FormatBadge(format = descriptor.format)

          // New / Older file age status badge
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isRecentFile) {
              SupportFullGreen.copy(alpha = 0.14f)
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            }
          ) {
            Text(
              text = stringResource(if (isRecentFile) R.string.badge_new_file else R.string.badge_old_file),
              style = MaterialTheme.typography.labelSmall,
              color = if (isRecentFile) SupportFullGreen else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          val isPartial = descriptor.supportLevel == SupportLevel.PARTIAL
          if (isPartial) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = SupportPartialAmber.copy(alpha = 0.14f)
            ) {
              Text(
                text = stringResource(R.string.support_partial),
                style = MaterialTheme.typography.labelSmall,
                color = SupportPartialAmber,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          } else if (descriptor.pageCount > 0) {
            Text(
              text = "${descriptor.lastPage}/${descriptor.pageCount}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              softWrap = false
            )
          }
        }
        Spacer(Modifier.height(3.dp))
        Text(
          text = listOfNotNull(
            formattedSize.takeIf { it.isNotEmpty() },
            relativeTime,
            exactDate
          ).joinToString(" • "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      IconButton(
        onClick = onToggleFavorite,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(
          imageVector = if (descriptor.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = stringResource(
            if (descriptor.isFavorite) R.string.action_unfavorite else R.string.action_favorite
          ),
          tint = if (descriptor.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      IconButton(
        onClick = onRemove,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = stringResource(R.string.action_remove_recent),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun EmptyLibraryState(
  isFavorites: Boolean,
  onPickDocument: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_empty_vault_1794641446501),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxWidth(0.84f)
        .height(155.dp)
        .clip(RoundedCornerShape(18.dp))
    )
    Spacer(Modifier.height(14.dp))
    Text(
      text = stringResource(
        if (isFavorites) R.string.empty_favorites_title else R.string.empty_recent_title
      ),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = stringResource(
        if (isFavorites) R.string.empty_favorites_desc else R.string.empty_recent_desc
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!isFavorites) {
      Spacer(Modifier.height(14.dp))
      Button(
        onClick = onPickDocument,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("empty_open_document_button")
      ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.open_document_full))
      }
    }
  }
}
