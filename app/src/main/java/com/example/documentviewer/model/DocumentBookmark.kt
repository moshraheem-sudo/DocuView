package com.example.documentviewer.model

data class DocumentBookmark(
  val id: Long = 0,
  val documentKey: String,
  val pageNumber: Int,
  val label: String,
  val createdAt: Long = System.currentTimeMillis()
)
