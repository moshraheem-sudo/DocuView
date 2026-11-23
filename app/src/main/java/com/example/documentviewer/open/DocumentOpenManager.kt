package com.example.documentviewer.open

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.documentviewer.DocumentViewerActivity
import com.example.documentviewer.model.DocumentDescriptor
import com.example.documentviewer.model.DocumentFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class DocumentOpenManager(private val context: Context) {

  companion object {
    val SUPPORTED_PICKER_MIME_TYPES = arrayOf("*/*")
  }

  data class UriMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isPersistable: Boolean
  )

  fun tryTakePersistablePermission(uri: Uri, flags: Int): Boolean {
    if (uri.scheme != "content") return false
    val takeFlags = (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).let {
      if (it == 0) Intent.FLAG_GRANT_READ_URI_PERMISSION else it
    }
    return try {
      context.contentResolver.takePersistableUriPermission(uri, takeFlags)
      true
    } catch (_: SecurityException) {
      false
    } catch (_: IllegalArgumentException) {
      false
    } catch (_: UnsupportedOperationException) {
      false
    }
  }

  fun isUriReadable(uri: Uri): Boolean {
    return when (uri.scheme) {
      "file" -> {
        val path = uri.path ?: return false
        val file = File(path)
        file.exists() && file.canRead() && file.length() > 0L
      }
      "content" -> {
        try {
          context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length != 0L
          } ?: context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: SecurityException) {
          false
        } catch (_: Exception) {
          false
        }
      }
      else -> false
    }
  }

  fun isDescriptorReadable(descriptor: DocumentDescriptor): Boolean {
    val cachedPath = descriptor.cachedFilePath
    if (!cachedPath.isNullOrBlank()) {
      val cached = File(cachedPath)
      if (cached.exists() && cached.canRead() && cached.length() > 0L) {
        return true
      }
    }
    val uri = runCatching { Uri.parse(descriptor.uriString) }.getOrNull() ?: return false
    return isUriReadable(uri)
  }

  fun queryUriMetadata(uri: Uri, isPersistable: Boolean): UriMetadata {
    var name: String? = null
    var size = -1L

    if (uri.scheme == "content") {
      try {
        context.contentResolver.query(
          uri,
          arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
          null,
          null,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) name = cursor.getString(nameIdx)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
          }
        }
      } catch (_: SecurityException) {
      } catch (_: Exception) {
      }
    } else if (uri.scheme == "file" && uri.path != null) {
      val f = File(uri.path!!)
      name = f.name
      size = f.length()
    }

    val resolvedName = name?.takeIf { it.isNotBlank() }
      ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
      ?: "Document"

    val mime = try {
      context.contentResolver.getType(uri)
    } catch (_: SecurityException) {
      null
    } catch (_: Exception) {
      null
    } ?: DocumentFormat.fromExtension(resolvedName.substringAfterLast('.', "")).primaryMimeType

    return UriMetadata(
      displayName = resolvedName,
      sizeBytes = size.coerceAtLeast(0L),
      mimeType = mime,
      isPersistable = isPersistable
    )
  }

  /**
   * Copies incoming shared URIs (e.g. ACTION_SEND / ACTION_VIEW from WhatsApp, Telegram, Email)
   * or picked URIs immediately into private app storage (`context.filesDir/imported_docs`) so
   * reopened documents use local storage paths rather than expiring transient content:// URIs.
   */
  fun cachePrivateCopyIfNeeded(uri: Uri, displayName: String, forceCopy: Boolean): File? {
    if (!forceCopy && uri.scheme == "file" && uri.path != null) {
      val file = File(uri.path!!)
      if (file.exists() && file.canRead() && file.length() > 0L) return file
    }

    val dir = File(context.filesDir, "imported_docs").apply { mkdirs() }
    val hash = sha256Short(uri.toString() + "_" + displayName)
    val ext = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
    val target = File(dir, "$hash$ext")

    if (target.exists() && target.canRead() && target.length() > 0L) {
      return target
    }

    val tempTarget = File(dir, "$hash$ext.tmp")
    return try {
      val inputStream = when (uri.scheme) {
        "file" -> uri.path?.let { path ->
          val source = File(path)
          if (source.exists() && source.canRead()) FileInputStream(source) else null
        }
        else -> context.contentResolver.openInputStream(uri)
      } ?: return null

      inputStream.use { input ->
        FileOutputStream(tempTarget).use { output ->
          input.copyTo(output, bufferSize = 32 * 1024)
          output.flush()
        }
      }

      if (tempTarget.exists() && tempTarget.length() > 0L) {
        if (target.exists()) target.delete()
        if (tempTarget.renameTo(target) || tempTarget.copyTo(target, overwrite = true).exists()) {
          tempTarget.delete()
          target
        } else {
          null
        }
      } else {
        tempTarget.delete()
        null
      }
    } catch (_: SecurityException) {
      tempTarget.delete()
      null
    } catch (_: Exception) {
      tempTarget.delete()
      null
    }
  }

  fun resolveShareableContentUri(
    activityContext: Context,
    descriptor: DocumentDescriptor
  ): Uri? {
    val localFile = descriptor.cachedFilePath
      ?.let { File(it) }
      ?.takeIf { it.exists() && it.canRead() }
      ?: descriptor.uriString
        .takeIf { it.startsWith("file://") }
        ?.let { runCatching { Uri.parse(it).path?.let(::File) }.getOrNull() }
        ?.takeIf { it.exists() && it.canRead() }

    if (localFile != null) {
      return runCatching {
        FileProvider.getUriForFile(
          activityContext,
          "${activityContext.packageName}.fileprovider",
          localFile
        )
      }.getOrElse {
        // If localFile resides outside configured FileProvider paths, copy to imported_docs first
        val fallbackCopy = cachePrivateCopyIfNeeded(
          uri = Uri.fromFile(localFile),
          displayName = descriptor.displayName,
          forceCopy = true
        ) ?: return null
        FileProvider.getUriForFile(
          activityContext,
          "${activityContext.packageName}.fileprovider",
          fallbackCopy
        )
      }
    }

    val parsed = runCatching { Uri.parse(descriptor.uriString) }.getOrNull() ?: return null
    if (parsed.scheme == "content" && isUriReadable(parsed)) {
      return parsed
    }
    return null
  }

  fun shareDocument(activityContext: Context, descriptor: DocumentDescriptor): Boolean {
    val shareUri = resolveShareableContentUri(activityContext, descriptor)
    if (shareUri == null) {
      Toast.makeText(
        activityContext,
        activityContext.getString(R.string.file_unavailable_reselect),
        Toast.LENGTH_LONG
      ).show()
      return false
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
      type = descriptor.mimeType.ifBlank { descriptor.format.primaryMimeType }
      putExtra(Intent.EXTRA_STREAM, shareUri)
      putExtra(Intent.EXTRA_SUBJECT, descriptor.displayName)
      clipData = ClipData.newUri(activityContext.contentResolver, descriptor.displayName, shareUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
      val chooser = Intent.createChooser(intent, descriptor.displayName).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      activityContext.startActivity(chooser)
      true
    } catch (_: SecurityException) {
      Toast.makeText(
        activityContext,
        activityContext.getString(R.string.file_unavailable_reselect),
        Toast.LENGTH_LONG
      ).show()
      false
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(
        activityContext,
        activityContext.getString(R.string.error_no_external_app),
        Toast.LENGTH_SHORT
      ).show()
      false
    }
  }

  fun openInExternalApp(activityContext: Context, descriptor: DocumentDescriptor): Boolean {
    val externalUri = resolveShareableContentUri(activityContext, descriptor)
    if (externalUri == null) {
      Toast.makeText(
        activityContext,
        activityContext.getString(R.string.file_unavailable_reselect),
        Toast.LENGTH_LONG
      ).show()
      return false
    }

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(externalUri, descriptor.mimeType.ifBlank { descriptor.format.primaryMimeType })
      clipData = ClipData.newUri(activityContext.contentResolver, descriptor.displayName, externalUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
      activityContext.startActivity(Intent.createChooser(viewIntent, descriptor.displayName))
      true
    } catch (_: SecurityException) {
      Toast.makeText(
        activityContext,
        activityContext.getString(R.string.file_unavailable_reselect),
        Toast.LENGTH_LONG
      ).show()
      false
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(
        activityContext,
        activityContext.getString(R.string.error_no_external_app),
        Toast.LENGTH_SHORT
      ).show()
      false
    }
  }

  fun printAdapter(
    activityContext: Context,
    jobName: String,
    adapter: PrintDocumentAdapter,
    landscape: Boolean = false
  ) {
    val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val attributes = PrintAttributes.Builder()
      .setMediaSize(
        if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape()
        else PrintAttributes.MediaSize.ISO_A4
      )
      .build()
    printManager.print(jobName, adapter, attributes)
  }

  fun extractIncomingUri(intent: Intent?): Uri? {
    if (intent == null) return null
    val clipUri = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    val sourceExtraUri = intent.getStringExtra(DocumentViewerActivity.EXTRA_SOURCE_URI)
      ?.takeIf { it.isNotBlank() }
      ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data ?: sourceExtraUri ?: clipUri
      Intent.ACTION_SEND -> {
        val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        streamUri ?: clipUri ?: intent.data ?: sourceExtraUri
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        val streamList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        streamList?.firstOrNull() ?: clipUri ?: intent.data ?: sourceExtraUri
      }
      else -> intent.data ?: sourceExtraUri ?: clipUri
    }
  }

  fun buildDocumentKey(uri: Uri, displayName: String, sizeBytes: Long): String {
    return sha256Short("${uri}_${displayName}_$sizeBytes")
  }

  private fun sha256Short(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.take(12).joinToString("") { "%02x".format(it) }
  }
}
