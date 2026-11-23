package com.example.documentviewer.data

import android.content.Context
import android.net.Uri
import com.example.documentviewer.model.DocumentBookmark
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.SupportLevel
import com.example.documentviewer.open.DocumentOpenManager
import com.example.documentviewer.resolver.DocumentTypeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class FileUnavailableException(message: String) : IllegalStateException(message)

class DocumentRepository(
  private val context: Context,
  private val recentManager: RecentDocumentsManager = RecentDocumentsManager(
    DocumentDatabase.getInstance(context).recentDocumentDao(),
    DocumentDatabase.getInstance(context).bookmarkDao()
  ),
  private val typeResolver: DocumentTypeResolver = DocumentTypeResolver(),
  val openManager: DocumentOpenManager = DocumentOpenManager(context)
) {
  val recentDocuments: Flow<List<DocumentDescriptor>> = recentManager.recentDocuments
  val favoriteDocuments: Flow<List<DocumentDescriptor>> = recentManager.favoriteDocuments

  fun observeBookmarks(documentKey: String): Flow<List<DocumentBookmark>> =
    recentManager.observeBookmarks(documentKey)

  suspend fun prepareDocumentFromUri(
    uri: Uri,
    intentFlags: Int,
    existingKey: String? = null
  ): Result<DocumentDescriptor> = withContext(Dispatchers.IO) {
    runCatching {
      val persistable = openManager.tryTakePersistablePermission(uri, intentFlags)
      val meta = openManager.queryUriMetadata(uri, persistable)

      // Copy transient or external URIs (e.g. WhatsApp/Telegram/Mail ACTION_SEND/ACTION_VIEW)
      // immediately into private internal storage (context.filesDir) on Dispatchers.IO.
      val cachedFile = openManager.cachePrivateCopyIfNeeded(
        uri = uri,
        displayName = meta.displayName,
        forceCopy = true
      )

      if (cachedFile == null && !openManager.isUriReadable(uri)) {
        throw FileUnavailableException(
          context.getString(com.example.R.string.file_unavailable_reselect)
        )
      }

      val resolved = when {
        cachedFile != null && cachedFile.exists() ->
          typeResolver.resolveFromFile(cachedFile, meta.displayName, meta.mimeType)
        else ->
          typeResolver.resolveFromUri(context.contentResolver, uri, meta.displayName, meta.mimeType)
      }

      if (resolved.format == DocumentFormat.UNKNOWN || resolved.supportLevel == SupportLevel.UNSUPPORTED) {
        throw IllegalArgumentException(
          "Unsupported file format: ${meta.displayName} (${meta.mimeType})"
        )
      }

      val actualSize = if (meta.sizeBytes > 0L) meta.sizeBytes else (cachedFile?.length() ?: 0L)
      val localUri = cachedFile?.takeIf { it.exists() && it.canRead() }?.let { Uri.fromFile(it) } ?: uri
      val docKey = existingKey ?: openManager.buildDocumentKey(localUri, meta.displayName, actualSize)

      // Save the local file path & local file:// URI instead of transient content:// URI
      val descriptor = DocumentDescriptor(
        documentKey = docKey,
        uriString = localUri.toString(),
        displayName = meta.displayName,
        mimeType = resolved.format.primaryMimeType,
        format = resolved.format,
        supportLevel = resolved.supportLevel,
        sizeBytes = actualSize,
        pageCount = resolved.estimatedPageCount,
        lastPage = 1,
        isPersistable = persistable || cachedFile != null,
        cachedFilePath = cachedFile?.absolutePath,
        compatibilityWarnings = resolved.warnings
      )

      recentManager.recordOpened(descriptor)
    }
  }

  /**
   * Validates that a saved/recent descriptor is still accessible before launching the viewer.
   * Migrates any legacy readable content:// entry into private filesDir storage, or fails cleanly
   * with FileUnavailableException when the external URI grant has expired.
   */
  suspend fun prepareDescriptorForOpen(descriptor: DocumentDescriptor): Result<DocumentDescriptor> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cached = descriptor.cachedFilePath?.let { File(it) }
        if (cached != null && cached.exists() && cached.canRead() && cached.length() > 0L) {
          val localUriString = Uri.fromFile(cached).toString()
          return@runCatching if (descriptor.uriString != localUriString) {
            val migrated = descriptor.copy(
              uriString = localUriString,
              sizeBytes = cached.length(),
              isPersistable = true
            )
            recentManager.upsertModifiedDocument(migrated)
          } else {
            descriptor
          }
        }

        val parsedUri = runCatching { Uri.parse(descriptor.uriString) }.getOrNull()
          ?: throw FileUnavailableException(
            context.getString(com.example.R.string.file_unavailable_reselect)
          )

        if (parsedUri.scheme == "file") {
          val localPath = parsedUri.path?.let(::File)
          if (localPath != null && localPath.exists() && localPath.canRead() && localPath.length() > 0L) {
            return@runCatching descriptor.copy(cachedFilePath = localPath.absolutePath)
          }
          throw FileUnavailableException(
            context.getString(com.example.R.string.file_unavailable_reselect)
          )
        }

        // Legacy content:// entry without a local copy: validate permission & migrate if still readable
        if (!openManager.isUriReadable(parsedUri)) {
          throw FileUnavailableException(
            context.getString(com.example.R.string.file_unavailable_reselect)
          )
        }

        val copied = openManager.cachePrivateCopyIfNeeded(
          uri = parsedUri,
          displayName = descriptor.displayName,
          forceCopy = true
        )
        if (copied != null && copied.exists() && copied.canRead() && copied.length() > 0L) {
          val migrated = descriptor.copy(
            uriString = Uri.fromFile(copied).toString(),
            cachedFilePath = copied.absolutePath,
            sizeBytes = copied.length(),
            isPersistable = true
          )
          recentManager.upsertModifiedDocument(migrated)
        } else {
          descriptor
        }
      }
    }

  suspend fun reopenFromDescriptor(descriptor: DocumentDescriptor): Result<DocumentDescriptor> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cached = descriptor.cachedFilePath?.let { File(it) }
        if (cached != null && cached.exists() && cached.canRead() && cached.length() > 0L) {
          val resolved = typeResolver.resolveFromFile(cached, descriptor.displayName, descriptor.mimeType)
          return@runCatching recentManager.recordOpened(
            descriptor.copy(
              uriString = Uri.fromFile(cached).toString(),
              sizeBytes = cached.length(),
              isPersistable = true,
              compatibilityWarnings = resolved.warnings
            )
          )
        }

        val uri = runCatching { Uri.parse(descriptor.uriString) }.getOrNull()
          ?: throw FileUnavailableException(
            context.getString(com.example.R.string.file_unavailable_reselect)
          )

        if (!openManager.isUriReadable(uri)) {
          throw FileUnavailableException(
            context.getString(com.example.R.string.file_unavailable_reselect)
          )
        }

        val migratedFile = openManager.cachePrivateCopyIfNeeded(
          uri = uri,
          displayName = descriptor.displayName,
          forceCopy = true
        )
        if (migratedFile != null && migratedFile.exists() && migratedFile.canRead() && migratedFile.length() > 0L) {
          val resolved = typeResolver.resolveFromFile(migratedFile, descriptor.displayName, descriptor.mimeType)
          return@runCatching recentManager.recordOpened(
            descriptor.copy(
              uriString = Uri.fromFile(migratedFile).toString(),
              cachedFilePath = migratedFile.absolutePath,
              sizeBytes = migratedFile.length(),
              isPersistable = true,
              compatibilityWarnings = resolved.warnings
            )
          )
        }

        val resolved = typeResolver.resolveFromUri(
          context.contentResolver,
          uri,
          descriptor.displayName,
          descriptor.mimeType
        )
        recentManager.recordOpened(
          descriptor.copy(compatibilityWarnings = resolved.warnings)
        )
      }
    }

  suspend fun recordSavedDescriptor(descriptor: DocumentDescriptor): DocumentDescriptor =
    withContext(Dispatchers.IO) {
      val cachedSize = descriptor.cachedFilePath?.let { File(it) }?.takeIf { it.exists() }?.length()
        ?: descriptor.sizeBytes
      recentManager.upsertModifiedDocument(descriptor.copy(sizeBytes = cachedSize))
    }

  suspend fun saveDocumentToDestinationUri(
    descriptor: DocumentDescriptor,
    destinationUri: Uri
  ): Result<Long> = withContext(Dispatchers.IO) {
    runCatching {
      val cachedFile = descriptor.cachedFilePath?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
      val inputStream = if (cachedFile != null) {
        FileInputStream(cachedFile)
      } else {
        context.contentResolver.openInputStream(Uri.parse(descriptor.uriString))
      } ?: throw IllegalStateException("Cannot open source file stream")

      var bytesWritten = 0L
      inputStream.use { input ->
        val output = context.contentResolver.openOutputStream(destinationUri, "wt")
          ?: context.contentResolver.openOutputStream(destinationUri)
          ?: throw IllegalStateException("Cannot open destination URI for writing")
        output.use { out ->
          bytesWritten = input.copyTo(out, bufferSize = 32 * 1024)
          out.flush()
        }
      }
      bytesWritten
    }
  }

  suspend fun toggleFavorite(documentKey: String, favorite: Boolean) {
    recentManager.toggleFavorite(documentKey, favorite)
  }

  suspend fun updateProgress(documentKey: String, page: Int, totalPages: Int) {
    recentManager.updateProgress(documentKey, page, totalPages)
  }

  suspend fun removeRecent(descriptor: DocumentDescriptor) {
    recentManager.removeRecent(descriptor)
  }

  suspend fun clearHistory() {
    recentManager.clearHistory()
  }

  suspend fun addBookmark(documentKey: String, pageNumber: Int, label: String) {
    recentManager.addBookmark(documentKey, pageNumber, label)
  }

  suspend fun deleteBookmark(id: Long) {
    recentManager.deleteBookmark(id)
  }

  suspend fun seedSampleWorkspace(): List<DocumentDescriptor> = withContext(Dispatchers.IO) {
    val samples = com.example.documentviewer.sample.RealSampleDocumentsGenerator
      .ensureSampleDocuments(context)
    val now = System.currentTimeMillis()
    val offsets = listOf(
      0L,                           // PDF: Just now (Recent / New)
      2L * 60L * 60L * 1000L,       // XLSX: 2 hours ago
      4L * 60L * 60L * 1000L,       // ACCDB: 4 hours ago
      8L * 60L * 60L * 1000L,       // DOCX: 8 hours ago
      72L * 60L * 60L * 1000L       // PPTX: 3 days ago (Older)
    )
    samples.mapIndexedNotNull { idx, sample ->
      val uri = Uri.fromFile(sample.file)
      val resolved = typeResolver.resolveFromFile(sample.file, sample.displayName, sample.mimeType)
      val size = sample.file.length()
      val key = openManager.buildDocumentKey(uri, sample.displayName, size)
      val openedAt = now - offsets.getOrElse(idx) { 0L }
      sample.file.setLastModified(openedAt)
      val descriptor = DocumentDescriptor(
        documentKey = key,
        uriString = uri.toString(),
        displayName = sample.displayName,
        mimeType = sample.mimeType,
        format = resolved.format,
        supportLevel = resolved.supportLevel,
        sizeBytes = size,
        pageCount = resolved.estimatedPageCount.coerceAtLeast(if (resolved.format == DocumentFormat.PDF) 3 else 2),
        lastPage = 1,
        lastOpenedAt = openedAt,
        isPersistable = true,
        cachedFilePath = sample.file.absolutePath,
        compatibilityWarnings = resolved.warnings
      )
      recentManager.recordOpened(descriptor, preserveTimestamp = true)
    }
  }
}
