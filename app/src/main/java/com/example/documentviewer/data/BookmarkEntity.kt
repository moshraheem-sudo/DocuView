package com.example.documentviewer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.documentviewer.model.DocumentBookmark

@Entity(tableName = "document_bookmarks")
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val documentKey: String,
  val pageNumber: Int,
  val label: String,
  val createdAt: Long
) {
  fun toDomain(): DocumentBookmark =
    DocumentBookmark(
      id = id,
      documentKey = documentKey,
      pageNumber = pageNumber,
      label = label,
      createdAt = createdAt
    )
}
