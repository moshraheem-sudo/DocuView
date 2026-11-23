package com.example.documentviewer.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ReleaseInfo(
  val versionName: String,
  val title: String,
  val notes: String,
  val downloadUrl: String,
  val sizeBytes: Long,
  val assetName: String
)

sealed interface AppUpdateState {
  data object Idle : AppUpdateState
  data object Checking : AppUpdateState
  data class UpToDate(val checkedAtMillis: Long) : AppUpdateState
  data class UpdateAvailable(val release: ReleaseInfo) : AppUpdateState
  data class Downloading(
    val release: ReleaseInfo,
    val downloadedBytes: Long,
    val totalBytes: Long
  ) : AppUpdateState
  data class ReadyToInstall(
    val release: ReleaseInfo,
    val apkFilePath: String,
    val needsInstallPermission: Boolean
  ) : AppUpdateState
  data class Cancelled(val release: ReleaseInfo) : AppUpdateState
  data class Error(val message: String, val release: ReleaseInfo? = null) : AppUpdateState
}

class AppUpdateManager(private val context: Context) {

  private val updatesDir: File
    get() = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }

  private val targetApkFile: File
    get() = File(updatesDir, "DocuView-update.apk")

  private val partialApkFile: File
    get() = File(updatesDir, "DocuView-update.apk.part")

  suspend fun fetchLatestRelease(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
    runCatching {
      val latestResponse = executeApiGet(LATEST_RELEASE_ENDPOINT)
      if (latestResponse.code == HttpURLConnection.HTTP_OK) {
        parseReleaseFromJson(latestResponse.body)
      } else if (latestResponse.code == HttpURLConnection.HTTP_NOT_FOUND) {
        val listResponse = executeApiGet(RELEASES_LIST_ENDPOINT)
        if (listResponse.code == HttpURLConnection.HTTP_OK) {
          parseLatestReleaseFromArrayJson(listResponse.body)
        } else {
          throw IOException(context.getString(R.string.update_error_server, listResponse.code))
        }
      } else {
        throw IOException(context.getString(R.string.update_error_server, latestResponse.code))
      }
    }
  }

  suspend fun downloadApk(
    release: ReleaseInfo,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
  ): Result<File> = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      if (partialApkFile.exists()) {
        partialApkFile.delete()
      }
      connection = openFollowingRedirects(release.downloadUrl)
      val code = connection.responseCode
      if (code !in 200..299) {
        return@withContext Result.failure(
          IOException(context.getString(R.string.update_error_server, code))
        )
      }

      val headerLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        connection.contentLengthLong
      } else {
        connection.contentLength.toLong()
      }
      val totalBytes = when {
        headerLength > 0L -> headerLength
        release.sizeBytes > 0L -> release.sizeBytes
        else -> 0L
      }

      var downloaded = 0L
      var lastEmitMs = 0L
      onProgress(0L, totalBytes)

      BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
        FileOutputStream(partialApkFile, false).use { output ->
          val buffer = ByteArray(BUFFER_SIZE)
          while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = System.currentTimeMillis()
            if (now - lastEmitMs >= PROGRESS_THROTTLE_MS) {
              lastEmitMs = now
              onProgress(downloaded, totalBytes)
            }
          }
          output.flush()
        }
      }

      currentCoroutineContext().ensureActive()
      val finalTotal = if (totalBytes > 0L) totalBytes else downloaded
      onProgress(downloaded, finalTotal)

      if (downloaded <= 0L || !partialApkFile.exists()) {
        partialApkFile.delete()
        return@withContext Result.failure(IOException(context.getString(R.string.update_error_download)))
      }

      if (targetApkFile.exists()) {
        targetApkFile.delete()
      }
      if (!partialApkFile.renameTo(targetApkFile)) {
        partialApkFile.copyTo(targetApkFile, overwrite = true)
        partialApkFile.delete()
      }

      Result.success(targetApkFile)
    } catch (ce: CancellationException) {
      cleanupPartialDownload()
      throw ce
    } catch (t: Throwable) {
      cleanupPartialDownload()
      val msg = t.message?.takeIf { it.isNotBlank() && !it.contains("github", ignoreCase = true) }
        ?: context.getString(R.string.update_error_download)
      Result.failure(IOException(msg, t))
    } finally {
      runCatching { connection?.disconnect() }
    }
  }

  fun cleanupPartialDownload() {
    runCatching {
      if (partialApkFile.exists()) partialApkFile.delete()
    }
  }

  fun cleanupAllDownloadedApks() {
    runCatching {
      if (partialApkFile.exists()) partialApkFile.delete()
      if (targetApkFile.exists()) targetApkFile.delete()
    }
  }

  fun canInstallPackages(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    } else {
      true
    }
  }

  fun buildUnknownSourcesIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildInstallApkIntent(apkFile: File): Intent {
    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      apkFile
    )
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(contentUri, APK_MIME_TYPE)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun showUpdateAvailableNotification(release: ReleaseInfo) {
    if (!canPostNotifications()) return
    ensureNotificationChannel()
    val openIntent = PendingIntent.getActivity(
      context,
      101,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cancelIntent = buildCancelPendingIntent()
    val sizeText = if (release.sizeBytes > 0L) {
      formatSingleMegabytes(context, release.sizeBytes)
    } else {
      ""
    }
    val body = if (sizeText.isNotEmpty()) {
      context.getString(R.string.update_notif_downloading_size, release.versionName, sizeText)
    } else {
      context.getString(R.string.update_notif_downloading, release.versionName)
    }
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(context.getString(R.string.update_notif_available_title, release.versionName))
      .setContentText(body)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(openIntent)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        context.getString(R.string.update_cancel_button),
        cancelIntent
      )
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
  }

  fun showDownloadProgressNotification(
    release: ReleaseInfo,
    downloadedBytes: Long,
    totalBytes: Long
  ) {
    if (!canPostNotifications()) return
    ensureNotificationChannel()
    val openIntent = PendingIntent.getActivity(
      context,
      101,
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cancelIntent = buildCancelPendingIntent()
    val progressText = formatProgressMegabytes(context, downloadedBytes, totalBytes)
    val indeterminate = totalBytes <= 0L
    val percent = if (totalBytes > 0L) {
      ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    } else {
      0
    }
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(context.getString(R.string.update_notif_available_title, release.versionName))
      .setContentText(progressText)
      .setProgress(100, percent, indeterminate)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(openIntent)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        context.getString(R.string.update_cancel_button),
        cancelIntent
      )
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
  }

  fun showReadyToInstallNotification(release: ReleaseInfo, apkFile: File) {
    if (!canPostNotifications()) return
    ensureNotificationChannel()
    val contentIntent = if (canInstallPackages()) {
      PendingIntent.getActivity(
        context,
        102,
        buildInstallApkIntent(apkFile),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    } else {
      PendingIntent.getActivity(
        context,
        102,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentTitle(context.getString(R.string.update_ready_title, release.versionName))
      .setContentText(context.getString(R.string.update_ready_desc))
      .setAutoCancel(true)
      .setOngoing(false)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(contentIntent)
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
  }

  fun cancelUpdateNotification() {
    runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
  }

  private fun buildCancelPendingIntent(): PendingIntent {
    val intent = Intent(ACTION_CANCEL_APP_UPDATE).apply {
      setPackage(context.packageName)
    }
    return PendingIntent.getBroadcast(
      context,
      204,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun canPostNotifications(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      if (nm.getNotificationChannel(UPDATE_CHANNEL_ID) == null) {
        val channel = NotificationChannel(
          UPDATE_CHANNEL_ID,
          context.getString(R.string.update_channel_name),
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = context.getString(R.string.update_channel_desc)
        }
        nm.createNotificationChannel(channel)
      }
    }
  }

  private data class HttpResponse(val code: Int, val body: String)

  private fun executeApiGet(urlString: String): HttpResponse {
    var connection: HttpURLConnection? = null
    return try {
      connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12_000
        readTimeout = 15_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "DocuView-Android/${BuildConfig.VERSION_NAME}")
      }
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      HttpResponse(code, body)
    } finally {
      runCatching { connection?.disconnect() }
    }
  }

  private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
    var currentUrl = startUrl
    repeat(MAX_REDIRECTS) {
      val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "DocuView-Android/${BuildConfig.VERSION_NAME}")
        setRequestProperty("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
      }
      val code = conn.responseCode
      if (code == HttpURLConnection.HTTP_MOVED_PERM ||
        code == HttpURLConnection.HTTP_MOVED_TEMP ||
        code == HttpURLConnection.HTTP_SEE_OTHER ||
        code == 307 ||
        code == 308
      ) {
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        if (location.isNullOrBlank()) {
          throw IOException(context.getString(R.string.update_error_download))
        }
        currentUrl = URL(URL(currentUrl), location).toString()
      } else {
        return conn
      }
    }
    throw IOException(context.getString(R.string.update_error_download))
  }

  companion object {
    const val ACTION_CANCEL_APP_UPDATE = "com.example.documentviewer.ACTION_CANCEL_APP_UPDATE"
    private const val UPDATE_CHANNEL_ID = "docuview_updates_channel"
    private const val NOTIFICATION_ID = 7041
    private const val BUFFER_SIZE = 16 * 1024
    private const val PROGRESS_THROTTLE_MS = 120L
    private const val MAX_REDIRECTS = 6
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val LATEST_RELEASE_ENDPOINT =
      "https://api.github.com/repos/moshraheem-sudo/DocuView/releases/latest"
    private const val RELEASES_LIST_ENDPOINT =
      "https://api.github.com/repos/moshraheem-sudo/DocuView/releases?per_page=5"

    fun parseReleaseFromJson(jsonString: String): ReleaseInfo? {
      if (jsonString.isBlank()) return null
      val obj = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null
      return parseReleaseObject(obj)
    }

    fun parseLatestReleaseFromArrayJson(jsonArrayString: String): ReleaseInfo? {
      if (jsonArrayString.isBlank()) return null
      val array = runCatching { JSONArray(jsonArrayString) }.getOrNull() ?: return null
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val parsed = parseReleaseObject(obj)
        if (parsed != null) return parsed
      }
      return null
    }

    private fun parseReleaseObject(obj: JSONObject): ReleaseInfo? {
      if (obj.optBoolean("draft", false)) return null
      val rawTag = obj.optString("tag_name", "").trim()
      val rawName = obj.optString("name", "").trim()
      val normalizedVersion = normalizeVersionTag(rawTag.ifBlank { rawName })
      if (normalizedVersion.isBlank()) return null

      val assets = obj.optJSONArray("assets") ?: JSONArray()
      var selectedAsset: JSONObject? = null
      for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val assetName = asset.optString("name", "")
        val downloadUrl = asset.optString("browser_download_url", "")
        if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
          if (assetName.equals("DocuView.apk", ignoreCase = true)) {
            selectedAsset = asset
            break
          }
          if (selectedAsset == null) {
            selectedAsset = asset
          }
        }
      }
      if (selectedAsset == null) return null

      return ReleaseInfo(
        versionName = normalizedVersion,
        title = sanitizeReleaseText(rawName.ifBlank { "v$normalizedVersion" }),
        notes = sanitizeReleaseText(obj.optString("body", "")),
        downloadUrl = selectedAsset.optString("browser_download_url"),
        sizeBytes = selectedAsset.optLong("size", 0L).coerceAtLeast(0L),
        assetName = selectedAsset.optString("name", "DocuView.apk")
      )
    }

    fun normalizeVersionTag(tag: String): String {
      return tag.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore("+")
        .trim()
    }

    fun compareVersions(remoteTag: String, currentVersion: String): Int {
      val remoteParts = extractNumericSegments(remoteTag)
      val currentParts = extractNumericSegments(currentVersion)
      val maxLen = maxOf(remoteParts.size, currentParts.size)
      for (i in 0 until maxLen) {
        val r = remoteParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (r != c) return r.compareTo(c)
      }
      return 0
    }

    private fun extractNumericSegments(version: String): List<Int> {
      val clean = normalizeVersionTag(version).substringBefore("-")
      val parts = clean.split(".")
      if (parts.isEmpty()) return listOf(0)
      return parts.map { part ->
        Regex("\\d+").find(part)?.value?.toIntOrNull() ?: 0
      }
    }

    fun sanitizeReleaseText(text: String): String {
      if (text.isBlank()) return ""
      return text
        .replace(Regex("https?://[^\\s)]*(github|githup)[^\\s)]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(github|githup|gh)\\b|غيتهاب", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
    }

    fun bytesToMegabytes(bytes: Long): Double {
      return bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
    }

    fun formatSingleMegabytes(context: Context, bytes: Long): String {
      val mb = bytesToMegabytes(bytes)
      return context.getString(R.string.update_size_single_mb, String.format(Locale.US, "%.1f", mb))
    }

    fun formatProgressMegabytes(context: Context, downloadedBytes: Long, totalBytes: Long): String {
      val downMb = String.format(Locale.US, "%.1f", bytesToMegabytes(downloadedBytes))
      return if (totalBytes > 0L) {
        val totalMb = String.format(Locale.US, "%.1f", bytesToMegabytes(totalBytes))
        context.getString(R.string.update_progress_mb_of_total, downMb, totalMb)
      } else {
        context.getString(R.string.update_size_single_mb, downMb)
      }
    }
  }
}
