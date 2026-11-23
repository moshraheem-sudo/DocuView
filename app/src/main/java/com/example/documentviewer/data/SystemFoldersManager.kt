package com.example.documentviewer.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.documentviewer.model.DocumentCategory
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.SupportLevel
import com.example.documentviewer.resolver.DocumentTypeResolver
import com.example.documentviewer.sample.RealSampleDocumentsGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

enum class FolderSourceKind {
  SYSTEM_STORAGE,
  LINKED_SAF_TREE,
  LOCAL_WORKSPACE
}

data class SystemFolderItem(
  val folderId: String,
  val folderName: String,
  val folderPathDisplay: String,
  val sourceKind: FolderSourceKind,
  val files: List<DocumentDescriptor>,
  val lastModifiedAt: Long = System.currentTimeMillis(),
  val treeUriString: String? = null,
  val documentId: String? = null,
  val localDirectoryPath: String? = null
) {
  val totalFiles: Int get() = files.size
  val pdfCount: Int get() = files.count { it.format.category == DocumentCategory.PDF }
  val wordCount: Int get() = files.count { it.format.category == DocumentCategory.WORD }
  val pptCount: Int get() = files.count { it.format.category == DocumentCategory.POWERPOINT }
  val excelCount: Int get() = files.count { it.format.category == DocumentCategory.EXCEL }
  val accessCount: Int get() = files.count { it.format.category == DocumentCategory.ACCESS }
  val htmlCount: Int get() = files.count { it.format.category == DocumentCategory.HTML }
  val codeCount: Int get() = files.count { it.format.category == DocumentCategory.CODE }
}

data class SystemScanResult(
  val folders: List<SystemFolderItem>,
  val allFiles: List<DocumentDescriptor>,
  val hasStoragePermission: Boolean = false
)

class SystemFoldersManager(
  private val context: Context,
  private val typeResolver: DocumentTypeResolver = DocumentTypeResolver()
) {

  private val userFoldersRoot: File
    get() = File(context.filesDir, "user_folders").apply { mkdirs() }

  private val importedDocsRoot: File
    get() = File(context.filesDir, "imported_docs").apply { mkdirs() }

  private val foldersCacheFile: File
    get() = File(context.filesDir, "folders_cache_v1.json")

  private val pinnedFoldersFile: File
    get() = File(context.filesDir, "pinned_folders_v1.json")

  fun hasFullStorageAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun registerMediaStoreObserver(onChanged: () -> Unit): ContentObserver? {
    return runCatching {
      val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
          onChanged()
        }
      }
      context.contentResolver.registerContentObserver(
        MediaStore.Files.getContentUri("external"),
        true,
        observer
      )
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.registerContentObserver(
          MediaStore.Downloads.EXTERNAL_CONTENT_URI,
          true,
          observer
        )
      }
      observer
    }.getOrNull()
  }

  fun unregisterMediaStoreObserver(observer: ContentObserver?) {
    if (observer != null) {
      runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }
  }

  @Suppress("DEPRECATION")
  fun registerDirectoryFileObservers(onChanged: () -> Unit): List<FileObserver> {
    val mask = FileObserver.CREATE or
      FileObserver.MOVED_TO or
      FileObserver.DELETE or
      FileObserver.MOVED_FROM or
      FileObserver.CLOSE_WRITE

    val dirsToWatch = linkedSetOf<File>()
    dirsToWatch.add(userFoldersRoot)
    dirsToWatch.add(importedDocsRoot)
    userFoldersRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach {
      dirsToWatch.add(it)
    }
    runCatching {
      val extRoot = Environment.getExternalStorageDirectory()
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { dirsToWatch.add(it) }
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { dirsToWatch.add(it) }
      if (extRoot != null) {
        listOf(
          "Telegram",
          "Telegram/Telegram Documents",
          "WhatsApp/Media/WhatsApp Documents",
          "Android/media/org.telegram.messenger/Telegram/Telegram Documents",
          "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"
        ).forEach { rel ->
          dirsToWatch.add(File(extRoot, rel))
        }
      }
    }

    val created = mutableListOf<FileObserver>()
    for (dir in dirsToWatch) {
      if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
      runCatching {
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) {
              if (path != null && !path.startsWith(".")) {
                onChanged()
              }
            }
          }
        } else {
          object : FileObserver(dir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
              if (path != null && !path.startsWith(".")) {
                onChanged()
              }
            }
          }
        }
        observer.startWatching()
        created.add(observer)
      }
    }
    return created
  }

  fun unregisterDirectoryFileObservers(observers: List<FileObserver>) {
    observers.forEach { obs ->
      runCatching { obs.stopWatching() }
    }
  }

  fun saveCachedScanSnapshot(result: SystemScanResult) {
    runCatching {
      val rootObj = JSONObject().apply {
        put("hasStoragePermission", result.hasStoragePermission)
        val foldersArr = JSONArray()
        result.folders.forEach { folder ->
          foldersArr.put(serializeFolder(folder))
        }
        put("folders", foldersArr)
      }
      val temp = File(foldersCacheFile.parentFile, "${foldersCacheFile.name}.tmp")
      temp.writeText(rootObj.toString(), Charsets.UTF_8)
      if (!temp.renameTo(foldersCacheFile)) {
        foldersCacheFile.writeText(rootObj.toString(), Charsets.UTF_8)
      }
    }
  }

  fun loadCachedScanSnapshot(): SystemScanResult? {
    val file = foldersCacheFile
    if (!file.exists() || file.length() == 0L) return null
    return runCatching {
      val root = JSONObject(file.readText(Charsets.UTF_8))
      val foldersArr = root.optJSONArray("folders") ?: return null
      val folders = mutableListOf<SystemFolderItem>()
      for (i in 0 until foldersArr.length()) {
        deserializeFolder(foldersArr.optJSONObject(i) ?: continue)?.let { folders.add(it) }
      }
      if (folders.isEmpty()) return null
      val allFiles = folders.flatMap { it.files }
        .distinctBy { it.documentKey }
        .sortedByDescending { it.lastOpenedAt }
      SystemScanResult(
        folders = folders,
        allFiles = allFiles,
        hasStoragePermission = hasFullStorageAccess()
      )
    }.getOrNull()
  }

  private fun savePinnedFolder(folder: SystemFolderItem) {
    runCatching {
      val existing = loadPinnedFolders().associateBy { it.folderId }.toMutableMap()
      existing[folder.folderId] = folder
      val arr = JSONArray()
      existing.values.forEach { arr.put(serializeFolder(it.copy(files = emptyList()))) }
      pinnedFoldersFile.writeText(arr.toString(), Charsets.UTF_8)
    }
  }

  private fun loadPinnedFolders(): List<SystemFolderItem> {
    val file = pinnedFoldersFile
    if (!file.exists() || file.length() == 0L) return emptyList()
    return runCatching {
      val arr = JSONArray(file.readText(Charsets.UTF_8))
      val list = mutableListOf<SystemFolderItem>()
      for (i in 0 until arr.length()) {
        deserializeFolder(arr.optJSONObject(i) ?: continue)?.let { list.add(it) }
      }
      list
    }.getOrDefault(emptyList())
  }

  private fun serializeFolder(folder: SystemFolderItem): JSONObject {
    val filesArr = JSONArray()
    folder.files.forEach { doc ->
      filesArr.put(JSONObject().apply {
        put("documentKey", doc.documentKey)
        put("uriString", doc.uriString)
        put("displayName", doc.displayName)
        put("mimeType", doc.mimeType)
        put("format", doc.format.name)
        put("supportLevel", doc.supportLevel.name)
        put("sizeBytes", doc.sizeBytes)
        put("pageCount", doc.pageCount)
        put("lastPage", doc.lastPage)
        put("lastOpenedAt", doc.lastOpenedAt)
        put("isFavorite", doc.isFavorite)
        put("isPersistable", doc.isPersistable)
        if (doc.cachedFilePath != null) put("cachedFilePath", doc.cachedFilePath)
      })
    }
    return JSONObject().apply {
      put("folderId", folder.folderId)
      put("folderName", folder.folderName)
      put("folderPathDisplay", folder.folderPathDisplay)
      put("sourceKind", folder.sourceKind.name)
      put("lastModifiedAt", folder.lastModifiedAt)
      if (folder.treeUriString != null) put("treeUriString", folder.treeUriString)
      if (folder.documentId != null) put("documentId", folder.documentId)
      if (folder.localDirectoryPath != null) put("localDirectoryPath", folder.localDirectoryPath)
      put("files", filesArr)
    }
  }

  private fun deserializeFolder(obj: JSONObject): SystemFolderItem? {
    val folderId = obj.optString("folderId").takeIf { it.isNotBlank() } ?: return null
    val folderName = obj.optString("folderName").takeIf { it.isNotBlank() } ?: return null
    val pathDisplay = obj.optString("folderPathDisplay", "/$folderName")
    val kindName = obj.optString("sourceKind", FolderSourceKind.LOCAL_WORKSPACE.name)
    val kind = runCatching { FolderSourceKind.valueOf(kindName) }.getOrDefault(FolderSourceKind.LOCAL_WORKSPACE)
    val lastMod = obj.optLong("lastModifiedAt", System.currentTimeMillis())
    val treeUri = obj.optString("treeUriString").takeIf { it.isNotBlank() }
    val docId = obj.optString("documentId").takeIf { it.isNotBlank() }
    val localPath = obj.optString("localDirectoryPath").takeIf { it.isNotBlank() }

    val files = mutableListOf<DocumentDescriptor>()
    val filesArr = obj.optJSONArray("files") ?: JSONArray()
    for (i in 0 until filesArr.length()) {
      val fObj = filesArr.optJSONObject(i) ?: continue
      val key = fObj.optString("documentKey").takeIf { it.isNotBlank() } ?: continue
      val uriStr = fObj.optString("uriString").takeIf { it.isNotBlank() } ?: continue
      val dispName = fObj.optString("displayName").takeIf { it.isNotBlank() } ?: continue
      val mime = fObj.optString("mimeType", "application/octet-stream")
      val fmt = runCatching { DocumentFormat.valueOf(fObj.optString("format")) }
        .getOrDefault(resolveFormatOnly(dispName, mime))
      val sup = runCatching { SupportLevel.valueOf(fObj.optString("supportLevel")) }
        .getOrDefault(fmt.supportLevel)
      val cachedPath = fObj.optString("cachedFilePath").takeIf { it.isNotBlank() }
      files.add(
        DocumentDescriptor(
          documentKey = key,
          uriString = uriStr,
          displayName = dispName,
          mimeType = mime,
          format = fmt,
          supportLevel = sup,
          sizeBytes = fObj.optLong("sizeBytes", 0L),
          pageCount = fObj.optInt("pageCount", 0),
          lastPage = fObj.optInt("lastPage", 1),
          lastOpenedAt = fObj.optLong("lastOpenedAt", lastMod),
          isFavorite = fObj.optBoolean("isFavorite", false),
          isPersistable = fObj.optBoolean("isPersistable", false),
          cachedFilePath = cachedPath
        )
      )
    }

    return SystemFolderItem(
      folderId = folderId,
      folderName = folderName,
      folderPathDisplay = pathDisplay,
      sourceKind = kind,
      files = files,
      lastModifiedAt = lastMod,
      treeUriString = treeUri,
      documentId = docId,
      localDirectoryPath = localPath
    )
  }

  fun persistTreePermission(treeUri: Uri) {
    runCatching {
      val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(treeUri, flags)
    }.onFailure {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      }
    }
  }

  suspend fun scanAllSystemFoldersAndDocuments(
    existingRecents: List<DocumentDescriptor> = emptyList()
  ): SystemScanResult = withContext(Dispatchers.IO) {
    ensureDefaultWorkspaceFolders()

    val hasPerm = hasFullStorageAccess()
    val recentMap = existingRecents.associateBy { it.documentKey }
    val folderBuckets = linkedMapOf<String, MutableFolderBucket>()
    val seenFileKeys = mutableSetOf<String>()
    val seenCanonicalPaths = mutableSetOf<String>()

    // 1. Scan entire device storage (/storage/emulated/0 + Download + Documents + WhatsApp + Telegram + custom folders)
    scanDeviceExternalStorage(folderBuckets, seenFileKeys, seenCanonicalPaths, recentMap)

    // 2. Scan all persisted SAF directory trees (granted via ACTION_OPEN_DOCUMENT_TREE)
    scanPersistedSafTrees(folderBuckets, seenFileKeys, recentMap)

    // 3. Scan MediaStore.Files & MediaStore.Downloads across the phone
    scanMediaStoreDocuments(folderBuckets, seenFileKeys, seenCanonicalPaths, recentMap)

    // 4. Scan user-created folders in userFoldersRoot
    scanLocalWorkspaceFolders(folderBuckets, seenFileKeys, seenCanonicalPaths, recentMap)

    // 5. Retain pinned / user-created folders even if currently empty or in nested external dirs
    for (pinned in loadPinnedFolders()) {
      val stillValid = when {
        pinned.localDirectoryPath != null -> {
          val dir = File(pinned.localDirectoryPath)
          dir.exists() && dir.isDirectory
        }
        pinned.treeUriString != null -> true
        else -> true
      }
      if (stillValid && !folderBuckets.containsKey(pinned.folderId)) {
        folderBuckets[pinned.folderId] = MutableFolderBucket(
          folderId = pinned.folderId,
          folderName = pinned.folderName,
          folderPathDisplay = pinned.folderPathDisplay,
          sourceKind = pinned.sourceKind,
          lastModifiedAt = pinned.lastModifiedAt,
          treeUriString = pinned.treeUriString,
          documentId = pinned.documentId,
          localDirectoryPath = pinned.localDirectoryPath
        )
      }
    }

    // 6. Group any remaining opened/imported recent documents
    for (recent in existingRecents) {
      val cachedPath = recent.cachedFilePath
      if (cachedPath != null && !seenCanonicalPaths.add(cachedPath)) continue
      if (seenFileKeys.add(recent.documentKey)) {
        val bucketKey = "recent_imports"
        val bucket = folderBuckets.getOrPut(bucketKey) {
          MutableFolderBucket(
            folderId = bucketKey,
            folderName = "المستندات المحفوظة",
            folderPathDisplay = "/Saved",
            sourceKind = FolderSourceKind.LOCAL_WORKSPACE,
            lastModifiedAt = recent.lastOpenedAt,
            localDirectoryPath = importedDocsRoot.absolutePath
          )
        }
        bucket.files.add(recent)
      }
    }

    val sortedFolders = folderBuckets.values
      .map { bucket ->
        val sortedFiles = bucket.files.sortedByDescending { it.lastOpenedAt }
        val latest = sortedFiles.maxOfOrNull { it.lastOpenedAt } ?: bucket.lastModifiedAt
        SystemFolderItem(
          folderId = bucket.folderId,
          folderName = bucket.folderName,
          folderPathDisplay = bucket.folderPathDisplay,
          sourceKind = bucket.sourceKind,
          files = sortedFiles,
          lastModifiedAt = latest,
          treeUriString = bucket.treeUriString,
          documentId = bucket.documentId,
          localDirectoryPath = bucket.localDirectoryPath
        )
      }
      .sortedWith(
        compareByDescending<SystemFolderItem> { it.sourceKind == FolderSourceKind.SYSTEM_STORAGE }
          .thenByDescending { it.sourceKind == FolderSourceKind.LINKED_SAF_TREE }
          .thenByDescending { it.files.size }
          .thenByDescending { it.lastModifiedAt }
      )

    val allFiles = sortedFolders
      .flatMap { it.files }
      .distinctBy { it.documentKey }
      .sortedByDescending { it.lastOpenedAt }

    SystemScanResult(
      folders = sortedFolders,
      allFiles = allFiles,
      hasStoragePermission = hasPerm
    )
  }

  suspend fun createFolder(
    rawFolderName: String,
    parentSafFolder: SystemFolderItem? = null
  ): Result<SystemFolderItem> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanName = rawFolderName.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(60)
      require(cleanName.isNotEmpty()) { "Folder name cannot be empty" }

      // 1. If creating inside a linked SAF tree folder, create it directly in the system storage tree
      if (parentSafFolder?.treeUriString != null && parentSafFolder.documentId != null) {
        val treeUri = Uri.parse(parentSafFolder.treeUriString)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentSafFolder.documentId)
        val createdDocUri = DocumentsContract.createDocument(
          context.contentResolver,
          parentDocUri,
          DocumentsContract.Document.MIME_TYPE_DIR,
          cleanName
        )
        if (createdDocUri != null) {
          val newDocId = DocumentsContract.getDocumentId(createdDocUri)
          val item = SystemFolderItem(
            folderId = "saf_${sha1(createdDocUri.toString())}",
            folderName = cleanName,
            folderPathDisplay = "${parentSafFolder.folderPathDisplay}/$cleanName",
            sourceKind = FolderSourceKind.LINKED_SAF_TREE,
            files = emptyList(),
            lastModifiedAt = System.currentTimeMillis(),
            treeUriString = parentSafFolder.treeUriString,
            documentId = newDocId
          )
          savePinnedFolder(item)
          return@runCatching item
        }
      }

      // 2. If full storage access is available, create directly in /storage/emulated/0/Documents/<cleanName>
      // as well as user_folders so it is visible both in phone File Manager and in DocuView
      var createdExternalDir: File? = null
      runCatching {
        val baseParent = if (parentSafFolder?.localDirectoryPath != null) {
          File(parentSafFolder.localDirectoryPath)
        } else {
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        }
        val target = File(baseParent, cleanName)
        if (target.mkdirs() || target.exists()) {
          createdExternalDir = target
        }
      }

      val localDir = File(userFoldersRoot, cleanName).apply { mkdirs() }
      val effectiveDir = createdExternalDir?.takeIf { it.canWrite() } ?: localDir

      val item = SystemFolderItem(
        folderId = "file_${sha1(effectiveDir.absolutePath)}",
        folderName = cleanName,
        folderPathDisplay = "/${effectiveDir.name}",
        sourceKind = if (createdExternalDir != null) FolderSourceKind.SYSTEM_STORAGE else FolderSourceKind.LOCAL_WORKSPACE,
        files = emptyList(),
        lastModifiedAt = System.currentTimeMillis(),
        localDirectoryPath = effectiveDir.absolutePath
      )
      savePinnedFolder(item)
      item
    }
  }

  suspend fun addDocumentToFolder(
    sourceUri: Uri,
    targetFolder: SystemFolderItem
  ): Result<DocumentDescriptor> = withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      if (sourceUri.scheme == "content") {
        runCatching {
          resolver.takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      }
      var displayName = "Document_${System.currentTimeMillis()}.pdf"
      val mimeType: String? = runCatching { resolver.getType(sourceUri) }.getOrNull()

      runCatching {
        resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
              displayName = cursor.getString(nameIdx)
            }
          }
        }
      }

      val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
      val resolvedFormat = resolveFormatOnly(safeName, mimeType)

      // 1. If targetFolder is a SAF folder, create the document inside that SAF directory as well
      if (targetFolder.treeUriString != null && targetFolder.documentId != null) {
        runCatching {
          val treeUri = Uri.parse(targetFolder.treeUriString)
          val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetFolder.documentId)
          val createdUri = DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            resolvedFormat.primaryMimeType,
            safeName
          )
          if (createdUri != null) {
            resolver.openInputStream(sourceUri)?.use { input ->
              resolver.openOutputStream(createdUri, "wt")?.use { output ->
                input.copyTo(output)
              }
            }
          }
        }
      }

      // 2. Write into the folder's directory for instant offline access
      val targetDir = targetFolder.localDirectoryPath?.let { File(it) }
        ?: File(userFoldersRoot, targetFolder.folderName)
      targetDir.mkdirs()

      val destFile = File(targetDir, safeName)
      resolver.openInputStream(sourceUri)?.use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      } ?: error("Cannot read selected file")

      val fullResolution = typeResolver.resolveFromFile(destFile, safeName, mimeType)
      val fileUri = Uri.fromFile(destFile).toString()
      DocumentDescriptor(
        documentKey = sha1(destFile.absolutePath),
        uriString = fileUri,
        displayName = safeName,
        mimeType = fullResolution.format.primaryMimeType,
        format = fullResolution.format,
        supportLevel = fullResolution.supportLevel,
        sizeBytes = destFile.length(),
        pageCount = fullResolution.estimatedPageCount,
        lastPage = 1,
        lastOpenedAt = System.currentTimeMillis(),
        cachedFilePath = destFile.absolutePath,
        compatibilityWarnings = fullResolution.warnings
      )
    }
  }

  private suspend fun ensureDefaultWorkspaceFolders() {
    val samples = RealSampleDocumentsGenerator.ensureSampleDocuments(context)
    // Clean up legacy long bilingual folder names if present
    runCatching {
      userFoldersRoot.listFiles()?.forEach { f ->
        if (f.isDirectory && f.name.contains("•")) {
          f.deleteRecursively()
        }
      }
    }
    val docsFolder = File(userFoldersRoot, "المستندات والتقارير").apply { mkdirs() }
    val slidesFolder = File(userFoldersRoot, "العروض التقديمية").apply { mkdirs() }
    val dataFolder = File(userFoldersRoot, "الجداول وقواعد البيانات").apply { mkdirs() }

    samples.forEach { sample ->
      val lower = sample.displayName.lowercase()
      val targetDir = when {
        lower.endsWith(".pptx") || lower.endsWith(".ppsx") || lower.endsWith(".ppt") -> slidesFolder
        lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".accdb") || lower.endsWith(".mdb") -> dataFolder
        else -> docsFolder
      }
      val targetFile = File(targetDir, sample.displayName)
      if (!targetFile.exists() || targetFile.length() == 0L) {
        runCatching { sample.file.copyTo(targetFile, overwrite = true) }
      }
    }
  }

  private fun scanDeviceExternalStorage(
    buckets: MutableMap<String, MutableFolderBucket>,
    seenFileKeys: MutableSet<String>,
    seenCanonicalPaths: MutableSet<String>,
    recentMap: Map<String, DocumentDescriptor>
  ) {
    val rootsToScan = linkedSetOf<File>()
    runCatching {
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { rootsToScan.add(it) }
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { rootsToScan.add(it) }
      val extRoot = Environment.getExternalStorageDirectory()
      if (extRoot != null) {
        listOf(
          "Telegram",
          "Telegram/Telegram Documents",
          "WhatsApp/Media/WhatsApp Documents",
          "Android/media/org.telegram.messenger/Telegram/Telegram Documents",
          "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"
        ).forEach { rel ->
          rootsToScan.add(File(extRoot, rel))
        }
        rootsToScan.add(extRoot)
      }
      context.getExternalFilesDirs(null).filterNotNull().forEach { rootsToScan.add(it) }
    }

    val visitedDirs = mutableSetOf<String>()
    for (root in rootsToScan) {
      if (!root.exists() || !root.canRead()) continue
      walkFileDirectory(
        dir = root,
        sourceKind = FolderSourceKind.SYSTEM_STORAGE,
        depth = 0,
        maxDepth = 10,
        includeEmptyFolders = false,
        visitedDirs = visitedDirs,
        buckets = buckets,
        seenFileKeys = seenFileKeys,
        seenCanonicalPaths = seenCanonicalPaths,
        recentMap = recentMap
      )
    }
  }

  private fun scanPersistedSafTrees(
    buckets: MutableMap<String, MutableFolderBucket>,
    seenFileKeys: MutableSet<String>,
    recentMap: Map<String, DocumentDescriptor>
  ) {
    val permissions = runCatching { context.contentResolver.persistedUriPermissions }.getOrDefault(emptyList())
    for (perm in permissions) {
      if (!perm.isReadPermission) continue
      val treeUri = perm.uri
      val isTree = runCatching { DocumentsContract.isTreeUri(treeUri) }.getOrDefault(false)
      if (!isTree) continue

      runCatching {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = extractReadableTreeName(rootDocId)
        walkSafTreeDirectory(
          treeUri = treeUri,
          parentDocId = rootDocId,
          folderName = rootName,
          folderPathDisplay = "/$rootName",
          depth = 0,
          buckets = buckets,
          seenFileKeys = seenFileKeys,
          recentMap = recentMap
        )
      }
    }
  }

  private fun walkSafTreeDirectory(
    treeUri: Uri,
    parentDocId: String,
    folderName: String,
    folderPathDisplay: String,
    depth: Int,
    buckets: MutableMap<String, MutableFolderBucket>,
    seenFileKeys: MutableSet<String>,
    recentMap: Map<String, DocumentDescriptor>
  ) {
    if (depth > 8) return
    val bucketId = "saf_${sha1("${treeUri}_$parentDocId")}"
    val bucket = buckets.getOrPut(bucketId) {
      MutableFolderBucket(
        folderId = bucketId,
        folderName = folderName,
        folderPathDisplay = folderPathDisplay,
        sourceKind = FolderSourceKind.LINKED_SAF_TREE,
        lastModifiedAt = System.currentTimeMillis(),
        treeUriString = treeUri.toString(),
        documentId = parentDocId
      )
    }

    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    runCatching {
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        while (cursor.moveToNext()) {
          val docId = if (idCol >= 0) cursor.getString(idCol) else continue
          val name = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else "Document"
          val mime = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) else ""
          val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
          val lastMod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else System.currentTimeMillis()

          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (!name.startsWith(".")) {
              walkSafTreeDirectory(
                treeUri = treeUri,
                parentDocId = docId,
                folderName = name,
                folderPathDisplay = "$folderPathDisplay/$name",
                depth = depth + 1,
                buckets = buckets,
                seenFileKeys = seenFileKeys,
                recentMap = recentMap
              )
            }
          } else {
            val format = resolveFormatOnly(name, mime)
            if (format != DocumentFormat.UNKNOWN) {
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
              val key = sha1(docUri.toString())
              val existing = recentMap[key]
              val descriptor = DocumentDescriptor(
                documentKey = key,
                uriString = docUri.toString(),
                displayName = name,
                mimeType = format.primaryMimeType,
                format = format,
                supportLevel = format.supportLevel,
                sizeBytes = size,
                pageCount = existing?.pageCount ?: 0,
                lastPage = existing?.lastPage ?: 1,
                lastOpenedAt = if (lastMod > 0) lastMod else (existing?.lastOpenedAt ?: System.currentTimeMillis()),
                isFavorite = existing?.isFavorite ?: false,
                isPersistable = true,
                cachedFilePath = existing?.cachedFilePath
              )
              if (seenFileKeys.add(key)) {
                bucket.files.add(descriptor)
              }
            }
          }
        }
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun scanMediaStoreDocuments(
    buckets: MutableMap<String, MutableFolderBucket>,
    seenFileKeys: MutableSet<String>,
    seenCanonicalPaths: MutableSet<String>,
    recentMap: Map<String, DocumentDescriptor>
  ) {
    val contentUris = mutableListOf(MediaStore.Files.getContentUri("external"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      contentUris.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
    }

    val projection = mutableListOf(
      MediaStore.Files.FileColumns._ID,
      MediaStore.Files.FileColumns.DISPLAY_NAME,
      MediaStore.Files.FileColumns.MIME_TYPE,
      MediaStore.Files.FileColumns.SIZE,
      MediaStore.Files.FileColumns.DATE_MODIFIED,
      MediaStore.Files.FileColumns.DATA
    ).apply {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        add(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
      }
    }.toTypedArray()

    for (baseUri in contentUris) {
      runCatching {
        context.contentResolver.query(
          baseUri,
          projection,
          null,
          null,
          "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
          val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
          val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
          val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
          val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
          val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
          val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
          val relCol = cursor.getColumnIndex("relative_path")
          val bucketNameCol = cursor.getColumnIndex("bucket_display_name")

          while (cursor.moveToNext()) {
            val id = if (idCol >= 0) cursor.getLong(idCol) else continue
            val dataPath = if (dataCol >= 0 && !cursor.isNull(dataCol)) cursor.getString(dataCol) else null
            val rawName = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else null
            val name = rawName ?: dataPath?.substringAfterLast('/') ?: continue
            val mime = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) else null
            val format = resolveFormatOnly(name, mime)
            if (format == DocumentFormat.UNKNOWN) continue

            if (dataPath != null && !seenCanonicalPaths.add(dataPath)) continue

            val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
            val modSec = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
            val modMillis = if (modSec > 0) modSec * 1000L else System.currentTimeMillis()
            val relPath = if (relCol >= 0 && !cursor.isNull(relCol)) {
              cursor.getString(relCol)
            } else {
              dataPath?.substringBeforeLast('/', "")?.substringAfter("/0/", "") ?: "Download"
            }
            val bucketName = if (bucketNameCol >= 0 && !cursor.isNull(bucketNameCol)) {
              cursor.getString(bucketNameCol)
            } else {
              relPath.trim('/').substringAfterLast('/').ifBlank { "Download" }
            }

            val itemUri = ContentUris.withAppendedId(baseUri, id)
            val key = sha1(dataPath ?: itemUri.toString())
            if (!seenFileKeys.add(key)) continue

            val existing = recentMap[key]
            val readableFile = dataPath?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
            val descriptor = DocumentDescriptor(
              documentKey = key,
              uriString = itemUri.toString(),
              displayName = name,
              mimeType = format.primaryMimeType,
              format = format,
              supportLevel = format.supportLevel,
              sizeBytes = size.takeIf { it > 0 } ?: readableFile?.length() ?: 0L,
              pageCount = existing?.pageCount ?: 0,
              lastPage = existing?.lastPage ?: 1,
              lastOpenedAt = modMillis,
              isFavorite = existing?.isFavorite ?: false,
              cachedFilePath = readableFile?.absolutePath ?: existing?.cachedFilePath
            )

            val parentDirPath = readableFile?.parentFile?.absolutePath
            val folderKey = if (parentDirPath != null) {
              "file_${sha1(parentDirPath)}"
            } else {
              "ms_${sha1(relPath.ifBlank { bucketName })}"
            }
            val bucket = buckets.getOrPut(folderKey) {
              MutableFolderBucket(
                folderId = folderKey,
                folderName = bucketName.ifBlank { "Download" },
                folderPathDisplay = "/${relPath.trim('/')}",
                sourceKind = FolderSourceKind.SYSTEM_STORAGE,
                lastModifiedAt = modMillis,
                localDirectoryPath = parentDirPath
              )
            }
            bucket.files.add(descriptor)
          }
        }
      }
    }
  }

  private fun scanLocalWorkspaceFolders(
    buckets: MutableMap<String, MutableFolderBucket>,
    seenFileKeys: MutableSet<String>,
    seenCanonicalPaths: MutableSet<String>,
    recentMap: Map<String, DocumentDescriptor>
  ) {
    val visited = mutableSetOf<String>()
    val subDirs = userFoldersRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty()
    for (subDir in subDirs) {
      walkFileDirectory(
        dir = subDir,
        sourceKind = FolderSourceKind.LOCAL_WORKSPACE,
        depth = 0,
        maxDepth = 3,
        includeEmptyFolders = true,
        visitedDirs = visited,
        buckets = buckets,
        seenFileKeys = seenFileKeys,
        seenCanonicalPaths = seenCanonicalPaths,
        recentMap = recentMap
      )
    }
  }

  private fun walkFileDirectory(
    dir: File,
    sourceKind: FolderSourceKind,
    depth: Int,
    maxDepth: Int,
    includeEmptyFolders: Boolean,
    visitedDirs: MutableSet<String>,
    buckets: MutableMap<String, MutableFolderBucket>,
    seenFileKeys: MutableSet<String>,
    seenCanonicalPaths: MutableSet<String>,
    recentMap: Map<String, DocumentDescriptor>
  ) {
    if (depth > maxDepth || !dir.exists() || !dir.canRead()) return
    val canonicalDir = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
    if (!visitedDirs.add(canonicalDir)) return

    // Skip heavy Android system cache directories (Android/data, Android/obb, .thumbnails) while preserving user folders named "data" and Android/media (e.g. WhatsApp/Telegram)
    val dirNameLower = dir.name.lowercase()
    val parentNameLower = dir.parentFile?.name?.lowercase().orEmpty()
    if (dirNameLower.startsWith(".") || dirNameLower == ".thumbnails" || dirNameLower == "thumbnails") {
      return
    }
    if (parentNameLower == "android" && (dirNameLower == "data" || dirNameLower == "obb" || dirNameLower == "sandbox")) {
      return
    }

    val children = dir.listFiles() ?: return
    val docFiles = mutableListOf<DocumentDescriptor>()

    for (child in children) {
      if (child.name.startsWith(".")) continue
      if (child.isDirectory) {
        walkFileDirectory(
          dir = child,
          sourceKind = sourceKind,
          depth = depth + 1,
          maxDepth = maxDepth,
          includeEmptyFolders = includeEmptyFolders,
          visitedDirs = visitedDirs,
          buckets = buckets,
          seenFileKeys = seenFileKeys,
          seenCanonicalPaths = seenCanonicalPaths,
          recentMap = recentMap
        )
      } else if (child.isFile && child.length() > 0L) {
        val format = resolveFormatOnly(child.name, null)
        if (format != DocumentFormat.UNKNOWN) {
          val absPath = child.absolutePath
          if (!seenCanonicalPaths.add(absPath)) continue
          val fileUri = Uri.fromFile(child).toString()
          val key = sha1(absPath)
          if (seenFileKeys.add(key)) {
            val existing = recentMap[key]
            docFiles.add(
              DocumentDescriptor(
                documentKey = key,
                uriString = fileUri,
                displayName = child.name,
                mimeType = format.primaryMimeType,
                format = format,
                supportLevel = format.supportLevel,
                sizeBytes = child.length(),
                pageCount = existing?.pageCount ?: 0,
                lastPage = existing?.lastPage ?: 1,
                lastOpenedAt = existing?.lastOpenedAt ?: child.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false,
                cachedFilePath = absPath
              )
            )
          }
        }
      }
    }

    if (docFiles.isNotEmpty() || includeEmptyFolders) {
      val bucketKey = "file_${sha1(dir.absolutePath)}"
      val bucket = buckets.getOrPut(bucketKey) {
        MutableFolderBucket(
          folderId = bucketKey,
          folderName = dir.name.ifBlank { "Storage" },
          folderPathDisplay = "/${dir.name}",
          sourceKind = sourceKind,
          lastModifiedAt = dir.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
          localDirectoryPath = dir.absolutePath
        )
      }
      bucket.files.addAll(docFiles)
    }
  }

  private fun resolveFormatOnly(fileName: String, mimeType: String?): DocumentFormat {
    val ext = fileName.substringAfterLast('.', "")
    val byExt = DocumentFormat.fromExtension(ext)
    if (byExt != DocumentFormat.UNKNOWN) return byExt
    return DocumentFormat.fromMimeType(mimeType)
  }

  private fun extractReadableTreeName(treeDocId: String): String {
    val afterColon = treeDocId.substringAfterLast(':', treeDocId)
    val lastSeg = afterColon.trim('/').substringAfterLast('/')
    return lastSeg.ifBlank { "Storage" }
  }

  private fun sha1(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(24)
  }

  private class MutableFolderBucket(
    val folderId: String,
    val folderName: String,
    val folderPathDisplay: String,
    val sourceKind: FolderSourceKind,
    val lastModifiedAt: Long,
    val treeUriString: String? = null,
    val documentId: String? = null,
    val localDirectoryPath: String? = null,
    val files: MutableList<DocumentDescriptor> = mutableListOf()
  )
}
