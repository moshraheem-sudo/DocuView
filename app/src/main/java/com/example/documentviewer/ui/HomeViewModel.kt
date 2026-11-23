package com.example.documentviewer.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.FileObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.documentviewer.data.AppLanguage
import com.example.documentviewer.data.DocumentRepository
import com.example.documentviewer.data.DocumentSettings
import com.example.documentviewer.data.ReaderSettingsState
import com.example.documentviewer.data.SystemFolderItem
import com.example.documentviewer.data.SystemFoldersManager
import com.example.documentviewer.data.SystemScanResult
import com.example.documentviewer.data.ThemeMode
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class HomeTab { RECENT, FOLDERS, FAVORITES, ENGINES }

enum class SortOrder {
  RECENT_OPENED,
  OLDEST_OPENED,
  NAME_ASC,
  SIZE_DESC
}

data class HomeUiState(
  val selectedTab: HomeTab = HomeTab.RECENT,
  val selectedCategory: DocumentCategory? = null,
  val sortOrder: SortOrder = SortOrder.RECENT_OPENED,
  val searchQuery: String = "",
  val foldersSearchQuery: String = "",
  val recentDocuments: List<DocumentDescriptor> = emptyList(),
  val favoriteDocuments: List<DocumentDescriptor> = emptyList(),
  val systemFolders: List<SystemFolderItem> = emptyList(),
  val allSystemFiles: List<DocumentDescriptor> = emptyList(),
  val selectedFolderId: String? = null,
  val hasStoragePermission: Boolean = true,
  val totalDocumentCount: Int = 0,
  val pdfCount: Int = 0,
  val wordCount: Int = 0,
  val pptCount: Int = 0,
  val excelCount: Int = 0,
  val accessCount: Int = 0,
  val htmlCount: Int = 0,
  val codeCount: Int = 0,
  val settings: ReaderSettingsState = ReaderSettingsState(),
  val isOpeningDocument: Boolean = false,
  val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = DocumentRepository(application)
  private val settingsStore = DocumentSettings(application)
  private val foldersManager = SystemFoldersManager(application)

  private val selectedTab = MutableStateFlow(HomeTab.RECENT)
  private val selectedCategory = MutableStateFlow<DocumentCategory?>(null)
  private val sortOrder = MutableStateFlow(SortOrder.RECENT_OPENED)
  private val searchQuery = MutableStateFlow("")
  private val foldersSearchQuery = MutableStateFlow("")
  private val selectedFolderId = MutableStateFlow<String?>(null)
  private val scanResultFlow = MutableStateFlow(SystemScanResult(emptyList(), emptyList(), true))
  private val isOpening = MutableStateFlow(false)
  private val errorMessage = MutableStateFlow<String?>(null)

  private var mediaObserver: ContentObserver? = null
  private var fileObservers: List<FileObserver> = emptyList()
  private val scanMutex = Mutex()
  private val scanTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  init {
    viewModelScope.launch {
      val cached = withContext(Dispatchers.IO) { foldersManager.loadCachedScanSnapshot() }
      if (cached != null && scanResultFlow.value.folders.isEmpty() && cached.folders.isNotEmpty()) {
        scanResultFlow.value = cached
      }
      val current = repository.recentDocuments.first()
      if (current.isEmpty()) {
        repository.seedSampleWorkspace()
      }
      performSafeScan(forceShowLoading = false)
    }

    viewModelScope.launch {
      scanTriggerFlow
        .debounce(350L)
        .collect {
          performSafeScan(forceShowLoading = false)
        }
    }

    mediaObserver = foldersManager.registerMediaStoreObserver {
      scanTriggerFlow.tryEmit(Unit)
    }
    fileObservers = foldersManager.registerDirectoryFileObservers {
      scanTriggerFlow.tryEmit(Unit)
    }
  }

  private val searchQueriesFlow = combine(searchQuery, foldersSearchQuery) { libQ, foldQ ->
    libQ to foldQ
  }

  val uiState: StateFlow<HomeUiState> = combine(
    selectedTab,
    selectedCategory,
    sortOrder,
    searchQueriesFlow,
    repository.recentDocuments,
    repository.favoriteDocuments,
    settingsStore.settingsFlow,
    isOpening,
    errorMessage,
    scanResultFlow,
    selectedFolderId
  ) { values ->
    val tab = values[0] as HomeTab
    val cat = values[1] as DocumentCategory?
    val sort = values[2] as SortOrder
    @Suppress("UNCHECKED_CAST")
    val queries = values[3] as Pair<String, String>
    val query = queries.first
    val folderQuery = queries.second
    @Suppress("UNCHECKED_CAST")
    val recents = values[4] as List<DocumentDescriptor>
    @Suppress("UNCHECKED_CAST")
    val favs = values[5] as List<DocumentDescriptor>
    val settings = values[6] as ReaderSettingsState
    val opening = values[7] as Boolean
    val error = values[8] as String?
    val scanRes = values[9] as SystemScanResult
    val activeFolderId = values[10] as String?

    val mergedAllDocs = (recents + scanRes.allFiles).distinctBy { it.documentKey }

    HomeUiState(
      selectedTab = tab,
      selectedCategory = cat,
      sortOrder = sort,
      searchQuery = query,
      foldersSearchQuery = folderQuery,
      recentDocuments = filterAndSortDocs(mergedAllDocs, cat, query, sort),
      favoriteDocuments = filterAndSortDocs(favs, cat, query, sort),
      systemFolders = scanRes.folders,
      allSystemFiles = scanRes.allFiles,
      selectedFolderId = activeFolderId,
      hasStoragePermission = scanRes.hasStoragePermission,
      totalDocumentCount = mergedAllDocs.size,
      pdfCount = mergedAllDocs.count { it.format.category == DocumentCategory.PDF },
      wordCount = mergedAllDocs.count { it.format.category == DocumentCategory.WORD },
      pptCount = mergedAllDocs.count { it.format.category == DocumentCategory.POWERPOINT },
      excelCount = mergedAllDocs.count { it.format.category == DocumentCategory.EXCEL },
      accessCount = mergedAllDocs.count { it.format.category == DocumentCategory.ACCESS },
      htmlCount = mergedAllDocs.count { it.format.category == DocumentCategory.HTML },
      codeCount = mergedAllDocs.count { it.format.category == DocumentCategory.CODE },
      settings = settings,
      isOpeningDocument = opening,
      errorMessage = error
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = HomeUiState()
  )

  private fun filterAndSortDocs(
    list: List<DocumentDescriptor>,
    category: DocumentCategory?,
    query: String,
    sort: SortOrder
  ): List<DocumentDescriptor> {
    val q = query.trim().lowercase()
    val filtered = list.filter { doc ->
      val catMatch = category == null || doc.format.category == category
      val textMatch = q.isEmpty() || doc.displayName.lowercase().contains(q) ||
        doc.format.label.lowercase().contains(q)
      catMatch && textMatch
    }
    return when (sort) {
      SortOrder.RECENT_OPENED -> filtered.sortedByDescending { it.lastOpenedAt }
      SortOrder.OLDEST_OPENED -> filtered.sortedBy { it.lastOpenedAt }
      SortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
      SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
    }
  }

  fun hasFullStorageAccess(): Boolean = foldersManager.hasFullStorageAccess()

  private suspend fun performSafeScan(forceShowLoading: Boolean): SystemScanResult {
    return scanMutex.withLock {
      if (forceShowLoading) isOpening.value = true
      try {
        val recents = repository.recentDocuments.first()
        val result = foldersManager.scanAllSystemFoldersAndDocuments(recents)
        val previous = scanResultFlow.value
        val effective = if (result.folders.isEmpty() && previous.folders.isNotEmpty()) {
          previous.copy(hasStoragePermission = result.hasStoragePermission)
        } else {
          result
        }
        scanResultFlow.value = effective
        foldersManager.saveCachedScanSnapshot(effective)
        effective
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        scanResultFlow.value
      } finally {
        if (forceShowLoading) isOpening.value = false
      }
    }
  }

  fun refreshSystemFolders() {
    viewModelScope.launch {
      performSafeScan(forceShowLoading = false)
    }
  }

  fun scanDeviceWithFeedback(onComplete: (Int, Int) -> Unit = { _, _ -> }) {
    viewModelScope.launch {
      val result = performSafeScan(forceShowLoading = true)
      onComplete(result.folders.size, result.allFiles.size)
    }
  }

  fun selectTab(tab: HomeTab) {
    selectedTab.value = tab
    if (tab == HomeTab.FOLDERS) {
      refreshSystemFolders()
    }
  }

  fun selectFolder(folderId: String?) {
    selectedFolderId.value = folderId
  }

  fun linkSystemFolderTree(treeUri: Uri) {
    viewModelScope.launch {
      isOpening.value = true
      runCatching { foldersManager.persistTreePermission(treeUri) }
      performSafeScan(forceShowLoading = false)
      selectedTab.value = HomeTab.FOLDERS
      isOpening.value = false
    }
  }

  fun createNewFolder(folderName: String, parentFolder: SystemFolderItem?) {
    viewModelScope.launch {
      isOpening.value = true
      foldersManager.createFolder(folderName, parentFolder)
        .onSuccess { created ->
          performSafeScan(forceShowLoading = false)
          selectedFolderId.value = created.folderId
        }
        .onFailure { err ->
          errorMessage.value = err.message
        }
      isOpening.value = false
    }
  }

  fun addDocumentToFolder(sourceUri: Uri, targetFolder: SystemFolderItem, onReady: (DocumentDescriptor) -> Unit = {}) {
    viewModelScope.launch {
      isOpening.value = true
      foldersManager.addDocumentToFolder(sourceUri, targetFolder)
        .onSuccess { descriptor ->
          repository.recordSavedDescriptor(descriptor)
          performSafeScan(forceShowLoading = false)
          selectedFolderId.value = targetFolder.folderId
          onReady(descriptor)
        }
        .onFailure { err ->
          errorMessage.value = err.message ?: "Unable to add file to folder"
        }
      isOpening.value = false
    }
  }

  fun selectCategory(category: DocumentCategory?) {
    selectedCategory.value = category
  }

  fun selectSortOrder(order: SortOrder) {
    sortOrder.value = order
  }

  fun cycleSortOrder() {
    sortOrder.value = when (sortOrder.value) {
      SortOrder.RECENT_OPENED -> SortOrder.OLDEST_OPENED
      SortOrder.OLDEST_OPENED -> SortOrder.NAME_ASC
      SortOrder.NAME_ASC -> SortOrder.SIZE_DESC
      SortOrder.SIZE_DESC -> SortOrder.RECENT_OPENED
    }
  }

  fun updateSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun updateFoldersSearchQuery(query: String) {
    foldersSearchQuery.value = query
  }

  fun toggleFavorite(doc: DocumentDescriptor) {
    viewModelScope.launch {
      repository.toggleFavorite(doc.documentKey, !doc.isFavorite)
      refreshSystemFolders()
    }
  }

  fun removeRecent(doc: DocumentDescriptor) {
    viewModelScope.launch {
      repository.removeRecent(doc)
      refreshSystemFolders()
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      repository.clearHistory()
      refreshSystemFolders()
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      settingsStore.setThemeMode(mode)
    }
  }

  fun setAppLanguage(language: AppLanguage) {
    viewModelScope.launch {
      settingsStore.setAppLanguage(language)
    }
  }

  fun setContinuousPdfScroll(enabled: Boolean) {
    viewModelScope.launch {
      settingsStore.setContinuousPdfScroll(enabled)
    }
  }

  fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch {
      settingsStore.setKeepScreenOn(enabled)
    }
  }

  fun openPickedUri(uri: Uri, flags: Int, onReady: (DocumentDescriptor) -> Unit) {
    viewModelScope.launch {
      isOpening.value = true
      errorMessage.value = null
      val result = repository.prepareDocumentFromUri(uri, flags)
      isOpening.value = false
      result.onSuccess { descriptor ->
        refreshSystemFolders()
        onReady(descriptor)
      }.onFailure { err ->
        errorMessage.value = err.message
          ?: getApplication<Application>().getString(com.example.R.string.file_unavailable_reselect)
      }
    }
  }

  fun prepareDescriptorForOpen(
    descriptor: DocumentDescriptor,
    onReady: (DocumentDescriptor) -> Unit,
    onUnavailable: (String) -> Unit
  ) {
    viewModelScope.launch {
      errorMessage.value = null
      val result = repository.prepareDescriptorForOpen(descriptor)
      result.onSuccess { validDescriptor ->
        onReady(validDescriptor)
      }.onFailure {
        onUnavailable(
          getApplication<Application>().getString(com.example.R.string.file_unavailable_reselect)
        )
      }
    }
  }

  fun clearError() {
    errorMessage.value = null
  }

  override fun onCleared() {
    super.onCleared()
    foldersManager.unregisterMediaStoreObserver(mediaObserver)
    foldersManager.unregisterDirectoryFileObservers(fileObservers)
  }
}
