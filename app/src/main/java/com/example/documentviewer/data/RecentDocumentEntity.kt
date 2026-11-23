package com.example.documentviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.SupportLevel

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
  @PrimaryKey val documentKey: String,
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val formatName: String,
  val supportLevelName: String,
  val sizeBytes: Long,
  val pageCount: Int,
  val lastPage: Int,
  val lastOpenedAt: Long,
  val isFavorite: Boolean,
  val isPersistable: Boolean,
  val cachedFilePath: String?
) {
  fun toDomain(): DocumentDescriptor {
    val format = runCatching { DocumentFormat.valueOf(formatName) }.getOrDefault(DocumentFormat.UNKNOWN)
    val support = runCatching { SupportLevel.valueOf(supportLevelName) }.getOrDefault(format.supportLevel)
    return DocumentDescriptor(
      documentKey = documentKey,
      uriString = uriString,
      displayName = displayName,
      mimeType = mimeType,
      format = format,
      supportLevel = support,
      sizeBytes = sizeBytes,
      pageCount = pageCount,
      lastPage = lastPage,
      lastOpenedAt = lastOpenedAt,
      isFavorite = isFavorite,
      isPersistable = isPersistable,
      cachedFilePath = cachedFilePath
    )
  }

  companion object {
    fun fromDomain(doc: DocumentDescriptor): RecentDocumentEntity =
      RecentDocumentEntity(
        documentKey = doc.documentKey,
        uriString = doc.uriString,
        displayName = doc.displayName,
        mimeType = doc.mimeType,
        formatName = doc.format.name,
        supportLevelName = doc.supportLevel.name,
        sizeBytes = doc.sizeBytes,
        pageCount = doc.pageCount,
        lastPage = doc.lastPage,
        lastOpenedAt = doc.lastOpenedAt,
        isFavorite = doc.isFavorite,
        isPersistable = doc.isPersistable,
        cachedFilePath = doc.cachedFilePath
      )
  }
}
