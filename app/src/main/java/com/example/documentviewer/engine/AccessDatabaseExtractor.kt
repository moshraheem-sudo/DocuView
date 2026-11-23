package com.example.documentviewer.engine

import android.content.Context
import android.net.Uri
import com.healthmarketscience.jackcess.Database
import com.healthmarketscience.jackcess.DatabaseBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

sealed interface AccessExtractionResult {
  data class Success(
    val jsonBytes: ByteArray,
    val tableCount: Int,
    val queryCount: Int,
    val fileFormat: String
  ) : AccessExtractionResult

  data class Failure(val message: String) : AccessExtractionResult
}

object AccessDatabaseExtractor {
  private const val MAX_ROWS_PER_TABLE = 800

  fun extractToJson(
    context: Context,
    cachedFile: File?,
    uri: Uri?,
    displayName: String
  ): AccessExtractionResult {
    var tempFile: File? = null
    return try {
      val dbFile: File = when {
        cachedFile != null && cachedFile.exists() && cachedFile.canRead() -> cachedFile
        uri != null -> {
          val ext = displayName.substringAfterLast('.', "accdb")
          val tmp = File.createTempFile("access_tmp_", ".$ext", context.cacheDir)
          tempFile = tmp
          context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output, bufferSize = 32 * 1024) }
          } ?: return AccessExtractionResult.Failure("Unable to open Access database stream.")
          tmp
        }
        else -> return AccessExtractionResult.Failure("Access database file is unavailable.")
      }

      DatabaseBuilder(dbFile)
        .setReadOnly(true)
        .open()
        .use { db ->
          val root = JSONObject()
          val fmtName = runCatching { db.fileFormat.name }.getOrDefault("ACCESS")
          root.put("fileName", displayName)
          root.put("fileFormat", fmtName)

          val tablesJson = JSONArray()
          val tableNames = runCatching { db.tableNames.sorted() }.getOrDefault(emptyList())
          for (tableName in tableNames) {
            runCatching {
              val table = db.getTable(tableName) ?: return@runCatching
              val colsJson = JSONArray()
              val colNames = mutableListOf<String>()
              for (col in table.columns) {
                colNames.add(col.name)
                colsJson.put(
                  JSONObject()
                    .put("name", col.name)
                    .put("type", col.type.name)
                    .put("autoNumber", col.isAutoNumber)
                    .put("length", col.lengthInUnits)
                )
              }

              val rowsJson = JSONArray()
              var rowIdx = 0
              for (row in table) {
                if (rowIdx >= MAX_ROWS_PER_TABLE) break
                val rowArr = JSONArray()
                for (colName in colNames) {
                  rowArr.put(formatCellValue(row[colName]))
                }
                rowsJson.put(rowArr)
                rowIdx++
              }

              tablesJson.put(
                JSONObject()
                  .put("name", table.name)
                  .put("rowCount", table.rowCount)
                  .put("columns", colsJson)
                  .put("rows", rowsJson)
                  .put("truncated", table.rowCount > MAX_ROWS_PER_TABLE)
              )
            }
          }
          root.put("tables", tablesJson)

          val queriesJson = JSONArray()
          runCatching {
            for (q in db.queries) {
              runCatching {
                queriesJson.put(
                  JSONObject()
                    .put("name", q.name)
                    .put("type", q.type.name)
                    .put("sql", runCatching { q.toSQLString() }.getOrDefault(""))
                )
              }
            }
          }
          root.put("queries", queriesJson)

          AccessExtractionResult.Success(
            jsonBytes = root.toString().toByteArray(Charsets.UTF_8),
            tableCount = tablesJson.length(),
            queryCount = queriesJson.length(),
            fileFormat = fmtName
          )
        }
    } catch (e: Throwable) {
      val msg = e.message.orEmpty()
      val friendly = when {
        msg.contains("password", ignoreCase = true) ||
          msg.contains("encrypt", ignoreCase = true) ||
          msg.contains("codec", ignoreCase = true) ->
          "This Access database appears encrypted or password-protected ($msg)."
        msg.isNotBlank() -> "Unable to read Access database: $msg"
        else -> "Unable to parse Microsoft Access database file."
      }
      AccessExtractionResult.Failure(friendly)
    } finally {
      runCatching { tempFile?.delete() }
    }
  }

  private fun formatCellValue(value: Any?): String {
    if (value == null) return ""
    return when (value) {
      is Boolean -> if (value) "true" else "false"
      is ByteArray -> "[Binary ${value.size} B]"
      is LocalDateTime -> {
        if (value.hour == 0 && value.minute == 0 && value.second == 0) {
          value.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
          value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
        }
      }
      is Date -> {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fmt.format(value).removeSuffix(" 00:00")
      }
      else -> value.toString()
    }
  }
}
