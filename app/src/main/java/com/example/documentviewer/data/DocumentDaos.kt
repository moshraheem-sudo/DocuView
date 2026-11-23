package com.example.documentviewer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {
  @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC")
  fun observeRecent(): Flow<List<RecentDocumentEntity>>

  @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
  fun observeFavorites(): Flow<List<RecentDocumentEntity>>

  @Query("SELECT * FROM recent_documents WHERE documentKey = :key LIMIT 1")
  suspend fun findByKey(key: String): RecentDocumentEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: RecentDocumentEntity)

  @Query("UPDATE recent_documents SET isFavorite = :favorite WHERE documentKey = :key")
  suspend fun setFavorite(key: String, favorite: Boolean)

  @Query("UPDATE recent_documents SET lastPage = :page, pageCount = :totalPages, lastOpenedAt = :timestamp WHERE documentKey = :key")
  suspend fun updateProgress(key: String, page: Int, totalPages: Int, timestamp: Long)

  @Query("DELETE FROM recent_documents WHERE documentKey = :key")
  suspend fun deleteByKey(key: String)

  @Query("DELETE FROM recent_documents WHERE isFavorite = 0")
  suspend fun clearNonFavorites()
}

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM document_bookmarks WHERE documentKey = :key ORDER BY pageNumber ASC, createdAt DESC")
  fun observeBookmarks(key: String): Flow<List<BookmarkEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: BookmarkEntity)

  @Query("DELETE FROM document_bookmarks WHERE id = :id")
  suspend fun deleteById(id: Long)
}
