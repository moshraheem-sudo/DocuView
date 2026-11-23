package com.example.documentviewer.data

import com.example.documentviewer.model.DocumentBookmark
import com.example.documentviewer.model.DocumentDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class RecentDocumentsManager(
  private val recentDao: RecentDocumentDao,
  private val bookmarkDao: BookmarkDao
) {
  val recentDocuments: Flow<List<DocumentDescriptor>> =
    recentDao.observeRecent().map { list -> list.map { it.toDomain() } }

  val favoriteDocuments: Flow<List<DocumentDescriptor>> =
    recentDao.observeFavorites().map { list -> list.map { it.toDomain() } }

  fun observeBookmarks(documentKey: String): Flow<List<DocumentBookmark>> =
    bookmarkDao.observeBookmarks(documentKey).map { list -> list.map { it.toDomain() } }

  suspend fun recordOpened(
    descriptor: DocumentDescriptor,
    preserveTimestamp: Boolean = false
  ): DocumentDescriptor {
    val existing = recentDao.findByKey(descriptor.documentKey)
    val timestamp = if (preserveTimestamp && descriptor.lastOpenedAt > 0L) {
      descriptor.lastOpenedAt
    } else {
      System.currentTimeMillis()
    }
    val merged = descriptor.copy(
      isFavorite = existing?.isFavorite ?: descriptor.isFavorite,
      lastPage = existing?.lastPage ?: descriptor.lastPage,
      pageCount = if (descriptor.pageCount > 0) descriptor.pageCount else (existing?.pageCount ?: 0),
      lastOpenedAt = timestamp
    )
    recentDao.upsert(RecentDocumentEntity.fromDomain(merged))
    return merged
  }

  suspend fun upsertModifiedDocument(descriptor: DocumentDescriptor): DocumentDescriptor {
    val updated = descriptor.copy(lastOpenedAt = System.currentTimeMillis())
    recentDao.upsert(RecentDocumentEntity.fromDomain(updated))
    return updated
  }

  suspend fun toggleFavorite(documentKey: String, favorite: Boolean) {
    recentDao.setFavorite(documentKey, favorite)
  }

  suspend fun updateProgress(documentKey: String, currentPage: Int, totalPages: Int) {
    recentDao.updateProgress(
      key = documentKey,
      page = currentPage.coerceAtLeast(1),
      totalPages = totalPages.coerceAtLeast(1),
      timestamp = System.currentTimeMillis()
    )
  }

  suspend fun removeRecent(descriptor: DocumentDescriptor) {
    recentDao.deleteByKey(descriptor.documentKey)
    descriptor.cachedFilePath?.let { path ->
      runCatching { File(path).delete() }
    }
  }

  suspend fun clearHistory() {
    recentDao.clearNonFavorites()
  }

  suspend fun addBookmark(documentKey: String, pageNumber: Int, label: String) {
    bookmarkDao.insert(
      BookmarkEntity(
        documentKey = documentKey,
        pageNumber = pageNumber,
        label = label,
        createdAt = System.currentTimeMillis()
      )
    )
  }

  suspend fun deleteBookmark(id: Long) {
    bookmarkDao.deleteById(id)
  }
}
