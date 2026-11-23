package com.example.documentviewer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [RecentDocumentEntity::class, BookmarkEntity::class],
  version = 1,
  exportSchema = false
)
abstract class DocumentDatabase : RoomDatabase() {
  abstract fun recentDocumentDao(): RecentDocumentDao
  abstract fun bookmarkDao(): BookmarkDao

  companion object {
    @Volatile private var instance: DocumentDatabase? = null

    fun getInstance(context: Context): DocumentDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          DocumentDatabase::class.java,
          "docuview_local.db"
        ).build().also { instance = it }
      }
  }
}
