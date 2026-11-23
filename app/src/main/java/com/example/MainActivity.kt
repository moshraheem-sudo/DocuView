package com.example

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.documentviewer.DocumentViewerActivity
import com.example.documentviewer.data.SystemFolderItem
import com.example.documentviewer.data.ThemeMode
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.ui.HomeScreen
import com.example.documentviewer.ui.HomeTab
import com.example.documentviewer.ui.HomeViewModel
import com.example.documentviewer.ui.LocalizedAppContainer
import com.example.ui.theme.MyApplicationTheme

private class PersistableOpenDocumentContract : ActivityResultContracts.OpenDocument() {
  override fun createIntent(context: Context, input: Array<String>): Intent {
    return super.createIntent(context, input).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
  }
}

class MainActivity : ComponentActivity() {
  private val homeViewModel: HomeViewModel by viewModels()
  private var autoPromptedStoragePermission = false

  private fun safeStartActivity(
    intent: Intent,
    onActivityNotFound: (() -> Unit)? = null
  ): Boolean {
    return try {
      startActivity(intent)
      true
    } catch (_: SecurityException) {
      Toast.makeText(
        this,
        getString(R.string.file_unavailable_reselect),
        Toast.LENGTH_LONG
      ).show()
      false
    } catch (_: ActivityNotFoundException) {
      if (onActivityNotFound != null) {
        onActivityNotFound()
      } else {
        Toast.makeText(
          this,
          getString(R.string.error_no_external_app),
          Toast.LENGTH_SHORT
        ).show()
      }
      false
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
      var targetFolderForImport by remember { mutableStateOf<SystemFolderItem?>(null) }

      val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) {
        homeViewModel.scanDeviceWithFeedback { folderCount, fileCount ->
          Toast.makeText(
            this@MainActivity,
            getString(R.string.scan_complete_toast, folderCount, fileCount),
            Toast.LENGTH_SHORT
          ).show()
        }
      }

      val requestSystemStoragePermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          if (!Environment.isExternalStorageManager()) {
            val appSettingsIntent = Intent(
              Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
              Uri.parse("package:$packageName")
            )
            safeStartActivity(appSettingsIntent) {
              safeStartActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
          }
        } else {
          try {
            storagePermissionLauncher.launch(
              arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
              )
            )
          } catch (_: SecurityException) {
            Toast.makeText(this@MainActivity, getString(R.string.error_permission_denied), Toast.LENGTH_SHORT).show()
          } catch (_: ActivityNotFoundException) {
            Toast.makeText(this@MainActivity, getString(R.string.error_no_external_app), Toast.LENGTH_SHORT).show()
          }
        }
      }

      val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = PersistableOpenDocumentContract()
      ) { uri ->
        if (uri != null) {
          homeViewModel.openPickedUri(
            uri = uri,
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
          ) { descriptor ->
            safeStartActivity(DocumentViewerActivity.createLaunchIntent(this@MainActivity, descriptor))
          }
        }
      }

      val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
      ) { treeUri ->
        if (treeUri != null) {
          homeViewModel.linkSystemFolderTree(treeUri)
        }
      }

      val addFileToFolderLauncher = rememberLauncherForActivityResult(
        contract = PersistableOpenDocumentContract()
      ) { uri ->
        val folder = targetFolderForImport
        targetFolderForImport = null
        if (uri != null && folder != null) {
          homeViewModel.addDocumentToFolder(uri, folder) {
            Toast.makeText(
              this@MainActivity,
              getString(R.string.file_added_to_folder_success),
              Toast.LENGTH_SHORT
            ).show()
          }
        }
      }

      val darkTheme = when (uiState.settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
      }

      LocalizedAppContainer(appLanguage = uiState.settings.appLanguage) { isArabic ->
        MyApplicationTheme(darkTheme = darkTheme) {
          HomeScreen(
            uiState = uiState,
            isArabic = isArabic,
            onPickDocument = { _ ->
              try {
                openDocumentLauncher.launch(DocumentOpenManager.SUPPORTED_PICKER_MIME_TYPES)
              } catch (_: SecurityException) {
                Toast.makeText(this@MainActivity, getString(R.string.error_permission_denied), Toast.LENGTH_SHORT).show()
              } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, getString(R.string.error_no_external_app), Toast.LENGTH_SHORT).show()
              }
            },
            onOpenDocument = { descriptor ->
              homeViewModel.prepareDescriptorForOpen(
                descriptor = descriptor,
                onReady = { validDescriptor ->
                  safeStartActivity(
                    DocumentViewerActivity.createLaunchIntent(this@MainActivity, validDescriptor)
                  )
                },
                onUnavailable = { message ->
                  Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
              )
            },
            onToggleFavorite = homeViewModel::toggleFavorite,
            onRemoveRecent = homeViewModel::removeRecent,
            onSelectTab = { tab ->
              homeViewModel.selectTab(tab)
              if (tab == HomeTab.FOLDERS && !homeViewModel.hasFullStorageAccess() && !autoPromptedStoragePermission) {
                autoPromptedStoragePermission = true
                requestSystemStoragePermission()
              }
            },
            onSelectCategory = homeViewModel::selectCategory,
            onSearchQueryChange = homeViewModel::updateSearchQuery,
            onFoldersSearchQueryChange = homeViewModel::updateFoldersSearchQuery,
            onThemeModeChange = homeViewModel::setThemeMode,
            onLanguageChange = homeViewModel::setAppLanguage,
            onContinuousPdfChange = homeViewModel::setContinuousPdfScroll,
            onKeepScreenOnChange = homeViewModel::setKeepScreenOn,
            onClearHistory = homeViewModel::clearHistory,
            onErrorConsumed = homeViewModel::clearError,
            onCycleSortOrder = homeViewModel::cycleSortOrder,
            onSelectSortOrder = homeViewModel::selectSortOrder,
            onSelectFolder = homeViewModel::selectFolder,
            onLinkSystemFolder = {
              try {
                openDocumentTreeLauncher.launch(null)
              } catch (_: SecurityException) {
                Toast.makeText(this@MainActivity, getString(R.string.error_permission_denied), Toast.LENGTH_SHORT).show()
              } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, getString(R.string.error_no_external_app), Toast.LENGTH_SHORT).show()
              }
            },
            onRequestStoragePermission = {
              requestSystemStoragePermission()
            },
            onCreateNewFolder = { name, parentFolder ->
              homeViewModel.createNewFolder(name, parentFolder)
              Toast.makeText(
                this@MainActivity,
                getString(R.string.folder_created_success),
                Toast.LENGTH_SHORT
              ).show()
            },
            onAddFileToFolder = { folder ->
              targetFolderForImport = folder
              try {
                addFileToFolderLauncher.launch(DocumentOpenManager.SUPPORTED_PICKER_MIME_TYPES)
              } catch (_: SecurityException) {
                Toast.makeText(this@MainActivity, getString(R.string.error_permission_denied), Toast.LENGTH_SHORT).show()
              } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, getString(R.string.error_no_external_app), Toast.LENGTH_SHORT).show()
              }
            },
            onRescanDevice = {
              if (!homeViewModel.hasFullStorageAccess()) {
                requestSystemStoragePermission()
              }
              homeViewModel.scanDeviceWithFeedback { folderCount, fileCount ->
                Toast.makeText(
                  this@MainActivity,
                  getString(R.string.scan_complete_toast, folderCount, fileCount),
                  Toast.LENGTH_SHORT
                ).show()
              }
            }
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // Automatically scan for any newly created folders or downloaded documents on resume
    homeViewModel.refreshSystemFolders()
  }
}
