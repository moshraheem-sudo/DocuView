package com.example.documentviewer.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.documentviewer.model.CompatibilityWarning
import com.example.documentviewer.model.DocumentBookmark
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.OutlineEntry
import com.example.documentviewer.model.SearchUiState
import com.example.documentviewer.model.SlideSummary
import com.example.ui.theme.SupportPartialAmber

@Composable
fun ViewerSearchBar(
  search: SearchUiState,
  onQueryChange: (String) -> Unit,
  onSearchSubmit: (String) -> Unit,
  onFindPrev: () -> Unit,
  onFindNext: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp,
    shadowElevation = 4.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = search.query,
        onValueChange = {
          onQueryChange(it)
          if (it.length >= 2 || it.isEmpty()) onSearchSubmit(it)
        },
        placeholder = { Text(stringResource(R.string.search_in_doc_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(search.query) }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .testTag("viewer_search_input")
      )
      Spacer(Modifier.width(6.dp))
      if (search.totalMatches > 0) {
        Text(
          text = "${search.currentMatch}/${search.totalMatches}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
      IconButton(
        onClick = onFindPrev,
        enabled = search.totalMatches > 0,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.action_prev))
      }
      IconButton(
        onClick = onFindNext,
        enabled = search.totalMatches > 0,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.action_next))
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("viewer_search_close")
      ) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailsBottomSheet(
  category: DocumentCategory,
  currentPage: Int,
  totalPages: Int,
  thumbnails: Map<Int, String>,
  slideSummaries: List<SlideSummary>,
  onRequestBatch: (Int, Int) -> Unit,
  onSelectPage: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
      Text(
        text = stringResource(R.string.action_thumbnails),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
      Spacer(Modifier.height(10.dp))

      if (category == DocumentCategory.POWERPOINT) {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(380.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 20.dp)
        ) {
          val itemsList = if (slideSummaries.isNotEmpty()) {
            slideSummaries
          } else {
            (1..totalPages).map { SlideSummary(it, "") }
          }
          items(itemsList, key = { it.index }) { slide ->
            val selected = slide.index == currentPage
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
              },
              border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  onSelectPage(slide.index)
                  onDismiss()
                }
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Outlined.Slideshow,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "#${slide.index}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                  )
                  if (slide.snippet.isNotBlank()) {
                    Text(
                      text = slide.snippet,
                      style = MaterialTheme.typography.bodyMedium,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }
      } else {
        LaunchedEffect(totalPages) {
          if (totalPages > 0) {
            onRequestBatch(1, minOf(totalPages, 24))
          }
        }
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 104.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 20.dp)
        ) {
          items((1..totalPages).toList(), key = { it }) { page ->
            val selected = page == currentPage
            val dataUrl = thumbnails[page]
            LaunchedEffect(page, dataUrl) {
              if (dataUrl == null && page > 24) {
                onRequestBatch(page, minOf(totalPages, page + 11))
              }
            }
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelectPage(page)
                  onDismiss()
                }
                .padding(4.dp)
            ) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                  width = if (selected) 2.dp else 1.dp,
                  color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(0.74f)
              ) {
                val bitmap = remember(dataUrl) { decodeDataUrlBitmap(dataUrl) }
                if (bitmap != null) {
                  Image(
                    bitmap = bitmap,
                    contentDescription = "Page $page",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                } else {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                  ) {
                    Text("$page", style = MaterialTheme.typography.titleMedium)
                  }
                }
              }
              Spacer(Modifier.height(4.dp))
              Text(
                text = "$page",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksBottomSheet(
  currentPage: Int,
  bookmarks: List<DocumentBookmark>,
  outline: List<OutlineEntry>,
  onAddCurrentPage: () -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onSelectPage: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(R.string.bookmarks_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Button(onClick = onAddCurrentPage) {
          Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("#$currentPage")
        }
      }
      Spacer(Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
      ) {
        item {
          Text(
            text = stringResource(R.string.saved_bookmarks_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
          )
        }
        if (bookmarks.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.bookmarks_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          items(bookmarks, key = { it.id }) { bm ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelectPage(bm.pageNumber)
                  onDismiss()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(10.dp))
              Text(
                text = "${bm.label} • #${bm.pageNumber}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
              )
              IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_close))
              }
            }
          }
        }

        if (outline.isNotEmpty()) {
          item {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
              text = stringResource(R.string.outline_section),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
          }
          items(outline) { entry ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  onSelectPage(entry.page)
                  onDismiss()
                }
                .padding(start = (8 + entry.depth * 14).dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "${entry.page}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GoToPageDialog(
  currentPage: Int,
  totalPages: Int,
  onGoToPage: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  var value by remember { mutableStateOf(currentPage.toString()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.goto_page_title)) },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { input -> value = input.filter { it.isDigit() } },
        label = { Text(stringResource(R.string.goto_page_label, totalPages)) },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Number,
          imeAction = ImeAction.Done
        ),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("goto_page_input")
      )
    },
    confirmButton = {
      Button(
        onClick = {
          val page = value.toIntOrNull()?.coerceIn(1, totalPages.coerceAtLeast(1))
          if (page != null) onGoToPage(page)
          onDismiss()
        },
        modifier = Modifier.testTag("goto_page_confirm")
      ) {
        Text(stringResource(R.string.goto_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityWarningsSheet(
  warnings: List<CompatibilityWarning>,
  isArabic: Boolean,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = SupportPartialAmber)
        Spacer(Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.compat_warnings_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }
      warnings.forEach { w ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = SupportPartialAmber.copy(alpha = 0.1f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = if (isArabic) w.titleAr else w.titleEn,
              style = MaterialTheme.typography.titleMedium,
              color = SupportPartialAmber
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = if (isArabic) w.detailAr else w.detailEn,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
fun SlideNavigationHud(
  currentSlide: Int,
  totalSlides: Int,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
      IconButton(
        onClick = onPrev,
        enabled = currentSlide > 1,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("slide_prev_button")
      ) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_prev))
      }
      Text(
        text = "$currentSlide / $totalSlides",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 8.dp)
      )
      IconButton(
        onClick = onNext,
        enabled = currentSlide < totalSlides,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("slide_next_button")
      ) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoSheet(
  descriptor: com.example.documentviewer.model.DocumentDescriptor,
  totalPages: Int,
  onDismiss: () -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val formattedSize = remember(descriptor.sizeBytes) {
    if (descriptor.sizeBytes > 0) {
      android.text.format.Formatter.formatShortFileSize(context, descriptor.sizeBytes)
    } else "—"
  }
  val engineLabel = when (descriptor.format) {
    com.example.documentviewer.model.DocumentFormat.PDF -> "Mozilla PDF.js 3.11 (Apache-2.0)"
    com.example.documentviewer.model.DocumentFormat.DOCX -> "docx-preview 0.3 + JSZip (Apache-2.0)"
    com.example.documentviewer.model.DocumentFormat.DOC -> "Ole2LegacyParser CFBF (Built-in Java)"
    com.example.documentviewer.model.DocumentFormat.PPTX,
    com.example.documentviewer.model.DocumentFormat.PPSX -> "PPTXjs 1.21 + D3/NVD3 (MIT)"
    com.example.documentviewer.model.DocumentFormat.PPT -> "Ole2LegacyParser CFBF (Built-in Java)"
    com.example.documentviewer.model.DocumentFormat.XLSX,
    com.example.documentviewer.model.DocumentFormat.XLS -> "SheetJS CE 0.20.3 (Apache-2.0)"
    com.example.documentviewer.model.DocumentFormat.ACCDB,
    com.example.documentviewer.model.DocumentFormat.MDB -> "Jackcess Jet/ACE Engine 4.0.8 (Apache-2.0)"
    com.example.documentviewer.model.DocumentFormat.HTML -> "DocuCode HTML Sandbox Engine"
    com.example.documentviewer.model.DocumentFormat.CODE -> "DocuCode Syntax Highlighter IDE Engine"
    com.example.documentviewer.model.DocumentFormat.UNKNOWN -> "Unknown"
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        FormatBadge(format = descriptor.format)
        Spacer(Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.doc_info_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }
      Text(
        text = descriptor.displayName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
      )
      HorizontalDivider()
      InfoRow(stringResource(R.string.doc_info_format), "${descriptor.format.label} • $engineLabel")
      InfoRow(
        stringResource(R.string.doc_info_support),
        stringResource(
          if (descriptor.supportLevel == com.example.documentviewer.model.SupportLevel.FULL)
            R.string.support_full
          else R.string.support_partial
        )
      )
      InfoRow(stringResource(R.string.doc_info_pages), "$totalPages")
      InfoRow(stringResource(R.string.doc_info_size), formattedSize)
      InfoRow(
        stringResource(R.string.doc_info_security),
        stringResource(R.string.doc_info_security_value)
      )
      HorizontalDivider()
      InfoRow(
        stringResource(R.string.app_name_label),
        "${stringResource(R.string.app_name)} • ${stringResource(R.string.version_value)}"
      )
      InfoRow(
        stringResource(R.string.developer_label),
        stringResource(R.string.developer_name)
      )
      Spacer(Modifier.height(16.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCopyAndExtractSheet(
  extractedText: String,
  currentPage: Int,
  isExtractingText: Boolean = false,
  hasNoTextLayer: Boolean = false,
  totalPages: Int = 1,
  onExtractAllPages: (() -> Unit)? = null,
  onCopyText: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var textFieldValue by remember(extractedText) {
    mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text = extractedText))
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${stringResource(R.string.copy_sheet_title)} (#$currentPage)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = stringResource(R.string.copy_sheet_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (isExtractingText) {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("extract_loading_indicator")
        )
      }

      if (hasNoTextLayer && !isExtractingText) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("extract_no_text_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = stringResource(R.string.extract_no_text_layer),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
      }

      OutlinedTextField(
        value = textFieldValue,
        onValueChange = { updated ->
          // Keep text read-only while allowing full native cursor & finger range selection
          textFieldValue = updated.copy(text = extractedText)
        },
        readOnly = true,
        placeholder = {
          Text(
            text = if (hasNoTextLayer && !isExtractingText) {
              stringResource(R.string.extract_no_text_layer)
            } else {
              stringResource(R.string.extracting_text_placeholder)
            }
          )
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(220.dp)
          .testTag("extract_text_field")
      )

      if (totalPages > 1 && onExtractAllPages != null) {
        OutlinedButton(
          onClick = onExtractAllPages,
          enabled = !isExtractingText,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("extract_all_pages_button")
        ) {
          Text(
            text = stringResource(R.string.extract_all_pages_btn),
            maxLines = 1,
            softWrap = false
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        val selectedSubText = remember(textFieldValue.selection, extractedText) {
          val s = textFieldValue.selection.min.coerceIn(0, extractedText.length)
          val e = textFieldValue.selection.max.coerceIn(0, extractedText.length)
          if (e > s) extractedText.substring(s, e) else ""
        }

        if (selectedSubText.isNotBlank()) {
          Button(
            onClick = {
              onCopyText(selectedSubText)
              onDismiss()
            },
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("copy_selected_part_button")
          ) {
            Text(stringResource(R.string.copy_selected_part_btn), maxLines = 1, softWrap = false)
          }
        }

        Button(
          onClick = {
            onCopyText(extractedText)
            onDismiss()
          },
          enabled = extractedText.isNotBlank() && !isExtractingText,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("copy_all_page_button")
        ) {
          Text(stringResource(R.string.copy_all_page_btn), maxLines = 1, softWrap = false)
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(2.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium
    )
  }
}

private fun decodeDataUrlBitmap(dataUrl: String?): androidx.compose.ui.graphics.ImageBitmap? {
  if (dataUrl.isNullOrBlank()) return null
  val comma = dataUrl.indexOf(',')
  if (comma < 0) return null
  return runCatching {
    val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
  }.getOrNull()
}
