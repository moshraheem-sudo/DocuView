package com.example.documentviewer.resolver

import android.content.ContentResolver
import android.net.Uri
import com.example.documentviewer.model.CompatibilityWarning
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.SupportLevel
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class ResolvedDocumentType(
  val format: DocumentFormat,
  val supportLevel: SupportLevel,
  val estimatedPageCount: Int = 0,
  val warnings: List<CompatibilityWarning> = emptyList()
)

class DocumentTypeResolver {

  fun resolveFromFile(file: File, displayName: String, mimeType: String?): ResolvedDocumentType {
    return FileInputStream(file).use { stream ->
      resolveFromStreamProvider(
        openStream = { FileInputStream(file) },
        displayName = displayName,
        mimeType = mimeType,
        firstHeaderStream = stream
      )
    }
  }

  fun resolveFromUri(
    contentResolver: ContentResolver,
    uri: Uri,
    displayName: String,
    mimeType: String?
  ): ResolvedDocumentType {
    val first = contentResolver.openInputStream(uri)
      ?: return fallbackResolve(displayName, mimeType)
    return first.use { stream ->
      resolveFromStreamProvider(
        openStream = { contentResolver.openInputStream(uri) },
        displayName = displayName,
        mimeType = mimeType,
        firstHeaderStream = stream
      )
    }
  }

  private fun resolveFromStreamProvider(
    openStream: () -> InputStream?,
    displayName: String,
    mimeType: String?,
    firstHeaderStream: InputStream
  ): ResolvedDocumentType {
    val buffered = BufferedInputStream(firstHeaderStream, 1024)
    val header = ByteArray(1024)
    val bytesRead = buffered.read(header)
    val ext = displayName.substringAfterLast('.', "").lowercase()

    if (bytesRead >= 4) {
      val headerAscii = String(header, 0, bytesRead, Charsets.ISO_8859_1)
      // 1. PDF Magic (%PDF- anywhere within the first 1024 bytes per PDF specification)
      if (headerAscii.contains("%PDF-")) {
        return ResolvedDocumentType(
          format = DocumentFormat.PDF,
          supportLevel = SupportLevel.FULL
        )
      }

      // 2. Microsoft Access Jet / ACE Database Magic ("Standard ACE DB" / "Standard Jet DB")
      if (headerAscii.contains("Standard ACE DB")) {
        return ResolvedDocumentType(
          format = DocumentFormat.ACCDB,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }
      if (headerAscii.contains("Standard Jet DB")) {
        return ResolvedDocumentType(
          format = DocumentFormat.MDB,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }

      // 2. OpenXML ZIP Magic (PK\x03\x04)
      if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
      ) {
        val inspection = openStream()?.use { OpenXmlInspector.inspect(it, ext) }
        if (inspection != null && inspection.detectedFormat != DocumentFormat.UNKNOWN) {
          return ResolvedDocumentType(
            format = inspection.detectedFormat,
            supportLevel = inspection.detectedFormat.supportLevel,
            estimatedPageCount = inspection.slideCount,
            warnings = inspection.warnings
          )
        }
      }

      // 4. OLE2 Binary Magic (D0 CF 11 E0 A1 B1 1A E1) -> Legacy DOC / PPT / XLS
      if (Ole2LegacyParser.isOle2Header(header)) {
        val oleResult = openStream()?.use { Ole2LegacyParser.inspect(it) }
        return when {
          oleResult?.kind == Ole2LegacyParser.LegacyKind.EXCEL_XLS ||
            ext in setOf("xls", "xlt", "xla") -> ResolvedDocumentType(
            format = DocumentFormat.XLS,
            supportLevel = SupportLevel.FULL,
            estimatedPageCount = 1
          )
          oleResult?.kind == Ole2LegacyParser.LegacyKind.POWERPOINT_PPT ||
            ext == "ppt" || ext == "pps" || ext == "pot" -> ResolvedDocumentType(
            format = DocumentFormat.PPT,
            supportLevel = SupportLevel.PARTIAL,
            warnings = listOf(legacyPptWarning())
          )
          else -> ResolvedDocumentType(
            format = DocumentFormat.DOC,
            supportLevel = SupportLevel.PARTIAL,
            warnings = listOf(legacyDocWarning())
          )
        }
      }

      // 5. Excel / Access extension routing when not ZIP/OLE2 (e.g. CSV, TSV, flat files)
      if (ext in DocumentFormat.EXCEL_MODERN_EXTENSIONS) {
        return ResolvedDocumentType(
          format = DocumentFormat.XLSX,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }
      if (ext in DocumentFormat.EXCEL_LEGACY_AND_SHEET_EXTENSIONS) {
        return ResolvedDocumentType(
          format = DocumentFormat.XLS,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }
      if (ext in DocumentFormat.ACCESS_MODERN_EXTENSIONS) {
        return ResolvedDocumentType(
          format = DocumentFormat.ACCDB,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }
      if (ext in DocumentFormat.ACCESS_LEGACY_EXTENSIONS) {
        return ResolvedDocumentType(
          format = DocumentFormat.MDB,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }

      // 4. RTF ({\rtf) or Word XML saved as .doc
      val trimmedAscii = headerAscii.trimStart()
      if (trimmedAscii.startsWith("{\\rtf") ||
        trimmedAscii.contains("Word.Document", ignoreCase = true) ||
        trimmedAscii.contains("<w:wordDocument", ignoreCase = true)
      ) {
        return ResolvedDocumentType(
          format = DocumentFormat.DOC,
          supportLevel = SupportLevel.PARTIAL,
          warnings = listOf(legacyDocWarning())
        )
      }

      // 5. HTML detection by extension or header tags
      if (ext in DocumentFormat.HTML_EXTENSIONS ||
        trimmedAscii.startsWith("<!doctype html", ignoreCase = true) ||
        trimmedAscii.startsWith("<html", ignoreCase = true)
      ) {
        return ResolvedDocumentType(
          format = DocumentFormat.HTML,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }

      // 6. Code & text files detection
      if (ext in DocumentFormat.CODE_EXTENSIONS) {
        return ResolvedDocumentType(
          format = DocumentFormat.CODE,
          supportLevel = SupportLevel.FULL,
          estimatedPageCount = 1
        )
      }
    }

    return fallbackResolve(displayName, mimeType)
  }

  fun fallbackResolve(displayName: String, mimeType: String?): ResolvedDocumentType {
    val ext = displayName.substringAfterLast('.', "")
    val byExt = DocumentFormat.fromExtension(ext)
    val byMime = DocumentFormat.fromMimeType(mimeType)
    val resolved = when {
      byExt != DocumentFormat.UNKNOWN -> byExt
      byMime != DocumentFormat.UNKNOWN -> byMime
      else -> DocumentFormat.CODE // Treat unknown text/script files with the Code & Text viewer
    }
    val warnings = when (resolved) {
      DocumentFormat.DOC -> listOf(legacyDocWarning())
      DocumentFormat.PPT -> listOf(legacyPptWarning())
      DocumentFormat.PPSX -> listOf(
        CompatibilityWarning(
          code = "PPSX_TIMINGS",
          titleEn = "Slide Show Timings",
          titleAr = "توقيتات العرض التلقائي",
          detailEn = "PPSX slides render visually, but automatic slide transitions and animations are omitted.",
          detailAr = "تُعرض شرائح PPSX بصرياً، ولكن حركات الانتقال والتوقيت التلقائي غير مدعومة."
        )
      )
      else -> emptyList()
    }
    return ResolvedDocumentType(
      format = resolved,
      supportLevel = resolved.supportLevel,
      estimatedPageCount = 1,
      warnings = warnings
    )
  }

  private fun legacyDocWarning() = CompatibilityWarning(
    code = "LEGACY_DOC_PARTIAL",
    titleEn = "Legacy .DOC (Partial Support)",
    titleAr = "صيغة .DOC القديمة (دعم جزئي)",
    detailEn = "Binary .doc files display extracted text paragraphs only. Convert to .docx for full formatting, tables, and images.",
    detailAr = "تعرض ملفات .doc الثنائية القديمة النصوص المستخرجة فقط. يُنصح بصيغة .docx لعرض الجداول والصور والتنسيق الكامل."
  )

  private fun legacyPptWarning() = CompatibilityWarning(
    code = "LEGACY_PPT_PARTIAL",
    titleEn = "Legacy .PPT (Partial Support)",
    titleAr = "صيغة .PPT القديمة (دعم جزئي)",
    detailEn = "Binary .ppt files display extracted slide text cards only. Convert to .pptx for full slide layout and graphics.",
    detailAr = "تعرض ملفات .ppt الثنائية القديمة نصوص الشرائح المستخرجة فقط. يُنصح بصيغة .pptx لعرض التصميم الكامل."
  )
}
