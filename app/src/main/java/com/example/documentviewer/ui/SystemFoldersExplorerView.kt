package com.example.documentviewer.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.documentviewer.data.SystemFolderItem
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import com.example.ui.theme.AccessAccent
import com.example.ui.theme.CodeAccent
import com.example.ui.theme.ExcelAccent
import com.example.ui.theme.HtmlAccent
import com.example.ui.theme.PdfAccent
import com.example.ui.theme.PptAccent
import com.example.ui.theme.SupportFullGreen
import com.example.ui.theme.WordAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExplorerDisplayMode {
  BY_FOLDERS,
  ALL_DEVICE_FILES
}

@Composable
fun SystemFoldersExplorerView(
  folders: List<SystemFolderItem>,
  allDeviceFiles: List<DocumentDescriptor>,
  selectedFolderId: String?,
  hasStoragePermission: Boolean,
  searchQuery: String,
  sortOrder: SortOrder,
  gridColumns: Int,
  onSearchQueryChange: (String) -> Unit,
  onSelectSortOrder: (SortOrder) -> Unit,
  onSelectFolder: (String?) -> Unit,
  onOpenDocument: (DocumentDescriptor) -> Unit,
  onToggleFavorite: (DocumentDescriptor) -> Unit,
  onLinkSystemFolder: () -> Unit,
  onRequestStoragePermission: () -> Unit,
  onCreateNewFolder: (String, SystemFolderItem?) -> Unit,
  onAddFileToFolder: (SystemFolderItem) -> Unit,
  onRescanDevice: () -> Unit,
  modifier: Modifier = Modifier
) {
  var displayMode by remember { mutableStateOf(ExplorerDisplayMode.BY_FOLDERS) }
  var showCreateFolderDialog by remember { mutableStateOf(false) }

  val activeFolder = remember(folders, selectedFolderId) {
    folders.firstOrNull { it.folderId == selectedFolderId }
  }

  BackHandler(enabled = activeFolder != null) {
    onSelectFolder(null)
  }

  val filteredFolders = remember(folders, searchQuery, sortOrder) {
    val q = searchQuery.trim().lowercase()
    val list = if (q.isEmpty()) {
      folders
    } else {
      folders.filter { folder ->
        folder.folderName.lowercase().contains(q) ||
          folder.folderPathDisplay.lowercase().contains(q) ||
          folder.files.any { it.displayName.lowercase().contains(q) }
      }
    }
    when (sortOrder) {
      SortOrder.RECENT_OPENED -> list.sortedByDescending { it.lastModifiedAt }
      SortOrder.OLDEST_OPENED -> list.sortedBy { it.lastModifiedAt }
      SortOrder.NAME_ASC -> list.sortedBy { it.folderName.lowercase() }
      SortOrder.SIZE_DESC -> list.sortedByDescending { it.totalFiles }
    }
  }

  val filteredFiles = remember(allDeviceFiles, activeFolder, searchQuery, sortOrder) {
    val sourceList = activeFolder?.files ?: allDeviceFiles
    val q = searchQuery.trim().lowercase()
    val list = if (q.isEmpty()) {
      sourceList
    } else {
      sourceList.filter {
        it.displayName.lowercase().contains(q) || it.format.label.lowercase().contains(q)
      }
    }
    when (sortOrder) {
      SortOrder.RECENT_OPENED -> list.sortedByDescending { it.lastOpenedAt }
      SortOrder.OLDEST_OPENED -> list.sortedBy { it.lastOpenedAt }
      SortOrder.NAME_ASC -> list.sortedBy { it.displayName.lowercase() }
      SortOrder.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
    }
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(gridColumns),
    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp, top = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(7.dp),
    modifier = modifier.fillMaxSize()
  ) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Search box + Create Folder quick button
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
              Text(
                text = stringResource(R.string.folders_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            leadingIcon = {
              Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp))
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                  Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(17.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("folders_search_input")
          )

          Button(
            onClick = { showCreateFolderDialog = true },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("create_folder_button")
          ) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.create_new_folder),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        // Compact single-row chips: Permission (if needed), Scan, Link Folder, Folders/Files toggle, Sort
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (!hasStoragePermission) {
            AssistChip(
              onClick = onRequestStoragePermission,
              colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
              ),
              leadingIcon = {
                Icon(
                  Icons.Outlined.LockOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(15.dp)
                )
              },
              label = {
                Text(
                  text = stringResource(R.string.grant_storage_permission_btn),
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false
                )
              },
              modifier = Modifier.testTag("grant_storage_permission_chip")
            )
          }

          AssistChip(
            onClick = onRescanDevice,
            leadingIcon = {
              Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
              )
            },
            label = {
              Text(
                text = stringResource(R.string.scan_device_files),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
              )
            },
            modifier = Modifier.testTag("scan_device_button")
          )

          AssistChip(
            onClick = onLinkSystemFolder,
            leadingIcon = {
              Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
              )
            },
            label = {
              Text(
                text = stringResource(R.string.link_system_folder),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
              )
            },
            modifier = Modifier.testTag("link_system_folder_button")
          )

          if (activeFolder == null) {
            FilterChip(
              selected = displayMode == ExplorerDisplayMode.BY_FOLDERS,
              onClick = { displayMode = ExplorerDisplayMode.BY_FOLDERS },
              leadingIcon = {
                Icon(Icons.Outlined.FolderCopy, contentDescription = null, modifier = Modifier.size(14.dp))
              },
              label = {
                Text(
                  text = stringResource(R.string.view_by_folders, folders.size),
                  maxLines = 1,
                  softWrap = false
                )
              },
              modifier = Modifier.testTag("mode_folders_chip")
            )

            FilterChip(
              selected = displayMode == ExplorerDisplayMode.ALL_DEVICE_FILES,
              onClick = { displayMode = ExplorerDisplayMode.ALL_DEVICE_FILES },
              leadingIcon = {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp))
              },
              label = {
                Text(
                  text = stringResource(R.string.view_all_device_files, allDeviceFiles.size),
                  maxLines = 1,
                  softWrap = false
                )
              },
              modifier = Modifier.testTag("mode_all_files_chip")
            )
          }

          FilterChip(
            selected = sortOrder == SortOrder.RECENT_OPENED,
            onClick = { onSelectSortOrder(SortOrder.RECENT_OPENED) },
            leadingIcon = {
              Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            label = { Text(stringResource(R.string.sort_recent), maxLines = 1, softWrap = false) }
          )

          FilterChip(
            selected = sortOrder == SortOrder.OLDEST_OPENED,
            onClick = { onSelectSortOrder(SortOrder.OLDEST_OPENED) },
            leadingIcon = {
              Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            label = { Text(stringResource(R.string.sort_oldest), maxLines = 1, softWrap = false) }
          )
        }

        // Breadcrumb header only when inside a specific folder
        if (activeFolder != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = { onSelectFolder(null) },
                modifier = Modifier
                  .size(34.dp)
                  .testTag("back_to_folders_button")
              ) {
                Icon(
                  Icons.AutoMirrored.Outlined.ArrowBack,
                  contentDescription = stringResource(R.string.back_to_all_folders),
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(Modifier.width(6.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = activeFolder.folderName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${activeFolder.folderPathDisplay} • ${stringResource(R.string.folder_files_count, activeFolder.totalFiles)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Button(
                onClick = { onAddFileToFolder(activeFolder) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("add_file_to_folder_button")
              ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                  text = stringResource(R.string.add_file_to_folder),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }
      }
    }

    if (activeFolder != null) {
      if (filteredFiles.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
              )
              Text(
                text = stringResource(R.string.folder_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Button(
                onClick = { onAddFileToFolder(activeFolder) },
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_file_to_folder))
              }
            }
          }
        }
      } else {
        items(filteredFiles, key = { it.documentKey }) { doc ->
          ExplorerDocumentFileRow(
            descriptor = doc,
            onClick = { onOpenDocument(doc) },
            onToggleFavorite = { onToggleFavorite(doc) }
          )
        }
      }
    } else if (displayMode == ExplorerDisplayMode.BY_FOLDERS) {
      items(filteredFolders, key = { it.folderId }) { folder ->
        SystemFolderCard(
          folder = folder,
          onClick = { onSelectFolder(folder.folderId) },
          onAddFileClick = { onAddFileToFolder(folder) }
        )
      }
    } else {
      items(filteredFiles, key = { it.documentKey }) { doc ->
        ExplorerDocumentFileRow(
          descriptor = doc,
          onClick = { onOpenDocument(doc) },
          onToggleFavorite = { onToggleFavorite(doc) }
        )
      }
    }
  }

  if (showCreateFolderDialog) {
    var folderNameInput by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateFolderDialog = false },
      title = { Text(stringResource(R.string.new_folder_dialog_title)) },
      text = {
        OutlinedTextField(
          value = folderNameInput,
          onValueChange = { folderNameInput = it },
          placeholder = { Text(stringResource(R.string.new_folder_name_hint)) },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("new_folder_name_input")
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (folderNameInput.isNotBlank()) {
              onCreateNewFolder(folderNameInput.trim(), activeFolder)
              showCreateFolderDialog = false
            }
          },
          enabled = folderNameInput.isNotBlank(),
          modifier = Modifier.testTag("confirm_create_folder_button")
        ) {
          Text(stringResource(R.string.create_folder_btn))
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateFolderDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      }
    )
  }
}

@Composable
private fun SystemFolderCard(
  folder: SystemFolderItem,
  onClick: () -> Unit,
  onAddFileClick: () -> Unit
) {
  val formattedDate = remember(folder.lastModifiedAt) {
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(folder.lastModifiedAt))
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    tonalElevation = 1.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .testTag("folder_card_${folder.folderId}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.size(40.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = folder.folderName,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
        }
      }

      Spacer(Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = folder.folderName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(2.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          Text(
            text = stringResource(R.string.folder_files_count, folder.totalFiles),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
          if (folder.pdfCount > 0) {
            MiniCountChip("PDF: ${folder.pdfCount}", PdfAccent)
          }
          if (folder.wordCount > 0) {
            MiniCountChip("Word: ${folder.wordCount}", WordAccent)
          }
          if (folder.pptCount > 0) {
            MiniCountChip("PPT: ${folder.pptCount}", PptAccent)
          }
          if (folder.excelCount > 0) {
            MiniCountChip("Excel: ${folder.excelCount}", ExcelAccent)
          }
          if (folder.accessCount > 0) {
            MiniCountChip("Access: ${folder.accessCount}", AccessAccent)
          }
          if (folder.htmlCount > 0) {
            MiniCountChip("HTML: ${folder.htmlCount}", HtmlAccent)
          }
          if (folder.codeCount > 0) {
            MiniCountChip("Code: ${folder.codeCount}", CodeAccent)
          }
        }

        Spacer(Modifier.height(2.dp))

        Text(
          text = "${folder.folderPathDisplay} • $formattedDate",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      IconButton(
        onClick = onAddFileClick,
        modifier = Modifier
          .size(36.dp)
          .testTag("add_file_to_${folder.folderId}")
      ) {
        Icon(
          imageVector = Icons.Outlined.Add,
          contentDescription = stringResource(R.string.add_file_to_folder),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun MiniCountChip(text: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(4.dp),
    color = color.copy(alpha = 0.14f)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
    )
  }
}

@Composable
private fun ExplorerDocumentFileRow(
  descriptor: DocumentDescriptor,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit
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
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    tonalElevation = 1.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .testTag("explorer_file_${descriptor.documentKey}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = RoundedCornerShape(9.dp),
        color = descriptor.format.tintColor,
        modifier = Modifier.size(38.dp)
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
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(Modifier.width(9.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = descriptor.displayName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          FormatBadge(format = descriptor.format)
          Surface(
            shape = RoundedCornerShape(5.dp),
            color = if (isRecentFile) SupportFullGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
          ) {
            Text(
              text = stringResource(if (isRecentFile) R.string.badge_new_file else R.string.badge_old_file),
              style = MaterialTheme.typography.labelSmall,
              color = if (isRecentFile) SupportFullGreen else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
          }
        }
        Spacer(Modifier.height(2.dp))
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
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = if (descriptor.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = null,
          tint = if (descriptor.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(19.dp)
        )
      }
    }
  }
}
