package com.example.documentviewer.model

data class DocumentDescriptor(
  val documentKey: String,
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val format: DocumentFormat,
  val supportLevel: SupportLevel,
  val sizeBytes: Long,
  val pageCount: Int = 0,
  val lastPage: Int = 1,
  val lastOpenedAt: Long = System.currentTimeMillis(),
  val isFavorite: Boolean = false,
  val isPersistable: Boolean = false,
  val cachedFilePath: String? = null,
  val compatibilityWarnings: List<CompatibilityWarning> = emptyList()
)
