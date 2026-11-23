package com.example.documentviewer.resolver

import com.example.documentviewer.model.CompatibilityWarning
import com.example.documentviewer.model.DocumentFormat
import java.io.InputStream
import java.util.zip.ZipInputStream

data class OpenXmlInspection(
  val detectedFormat: DocumentFormat,
  val slideCount: Int = 0,
  val warnings: List<CompatibilityWarning> = emptyList()
)

object OpenXmlInspector {
  private const val MAX_ZIP_ENTRIES = 4096

  fun inspect(inputStream: InputStream, hintExtension: String?): OpenXmlInspection {
    var hasWordDoc = false
    var hasPptPresentation = false
    var hasExcelWorkbook = false
    var hasOdsSpreadsheet = false
    var isSlideshow = hintExtension.equals("ppsx", ignoreCase = true)
    var slideCount = 0
    var sheetCount = 0

    var hasSmartArt = false
    var hasCharts = false
    var hasMedia = false
    var hasEmbeddings = false
    var hasMacros = false
    var hasCommentsOrMath = false

    ZipInputStream(inputStream).use { zip ->
      var count = 0
      var entry = zip.nextEntry
      while (entry != null && count++ < MAX_ZIP_ENTRIES) {
        val name = entry.name.replace('\\', '/').lowercase().removePrefix("/")
        when {
          name == "word/document.xml" || (name.startsWith("word/document") && name.endsWith(".xml")) -> hasWordDoc = true
          name == "ppt/presentation.xml" || (name.startsWith("ppt/presentation") && name.endsWith(".xml")) -> hasPptPresentation = true
          name.startsWith("ppt/slides/slide") && name.endsWith(".xml") -> {
            hasPptPresentation = true
            slideCount++
          }
          name == "xl/workbook.xml" || (name.startsWith("xl/workbook") && name.endsWith(".xml")) -> hasExcelWorkbook = true
          name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
            hasExcelWorkbook = true
            sheetCount++
          }
          name == "mimetype" -> {
            val content = zip.readNBytesBounded(512).toString(Charsets.UTF_8)
            if (content.contains("opendocument.spreadsheet", ignoreCase = true)) {
              hasOdsSpreadsheet = true
            }
          }
          name.startsWith("ppt/diagrams/") || name.startsWith("word/diagrams/") -> hasSmartArt = true
          name.startsWith("ppt/charts/") || name.startsWith("word/charts/") || name.startsWith("xl/charts/") -> hasCharts = true
          name.startsWith("ppt/media/") &&
            (name.endsWith(".mp4") || name.endsWith(".wmv") || name.endsWith(".mov") ||
              name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a")) -> hasMedia = true
          name.contains("/embeddings/") -> hasEmbeddings = true
          name.endsWith("vbaproject.bin") -> hasMacros = true
          name == "word/comments.xml" -> hasCommentsOrMath = true
          name == "[content_types].xml" -> {
            val content = zip.readNBytesBounded(64 * 1024).toString(Charsets.UTF_8)
            if (content.contains("presentationml.slideshow", ignoreCase = true) ||
              content.contains("ms-powerpoint.slideshow", ignoreCase = true)
            ) {
              isSlideshow = true
            }
            if (content.contains("wordprocessingml", ignoreCase = true) ||
              content.contains("ms-word", ignoreCase = true)
            ) {
              hasWordDoc = true
            }
            if (content.contains("presentationml", ignoreCase = true) ||
              content.contains("ms-powerpoint", ignoreCase = true)
            ) {
              hasPptPresentation = true
            }
            if (content.contains("spreadsheetml", ignoreCase = true) ||
              content.contains("ms-excel", ignoreCase = true)
            ) {
              hasExcelWorkbook = true
            }
          }
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }

    val format = when {
      hasWordDoc -> DocumentFormat.DOCX
      hasExcelWorkbook -> DocumentFormat.XLSX
      hasOdsSpreadsheet -> DocumentFormat.XLS
      hasPptPresentation && isSlideshow -> DocumentFormat.PPSX
      hasPptPresentation -> DocumentFormat.PPTX
      else -> DocumentFormat.UNKNOWN
    }

    val effectiveCount = when {
      hasExcelWorkbook -> sheetCount.coerceAtLeast(1)
      else -> slideCount
    }

    val warnings = buildList {
      if (format == DocumentFormat.PPSX) {
        add(
          CompatibilityWarning(
            code = "PPSX_TIMINGS",
            titleEn = "Slide Show Timings",
            titleAr = "توقيتات العرض التلقائي",
            detailEn = "PPSX slides render visually, but automatic slide transitions and animations are omitted.",
            detailAr = "تُعرض شرائح PPSX بصرياً، ولكن حركات الانتقال والتوقيت التلقائي غير مدعومة."
          )
        )
      }
      if (hasSmartArt) {
        add(
          CompatibilityWarning(
            code = "SMARTART",
            titleEn = "SmartArt Diagrams",
            titleAr = "رسومات SmartArt",
            detailEn = "SmartArt graphics detected; complex diagram layouts may not render accurately.",
            detailAr = "تم اكتشاف رسومات SmartArt؛ قد لا تظهر المخططات التوضيحية المعقدة بدقة كاملة."
          )
        )
      }
      if (hasCharts) {
        add(
          CompatibilityWarning(
            code = "CHARTS",
            titleEn = "Embedded Charts",
            titleAr = "مخططات بيانية مضمّنة",
            detailEn = "Charts detected; advanced 3D/combo chart series may render in simplified form or be omitted.",
            detailAr = "تم اكتشاف مخططات بيانية؛ قد تُعرض المخططات المتقدمة أو ثلاثية الأبعاد بشكل مبسّط."
          )
        )
      }
      if (hasMedia) {
        add(
          CompatibilityWarning(
            code = "MEDIA",
            titleEn = "Audio / Video Clips",
            titleAr = "مقاطع صوت أو فيديو",
            detailEn = "Embedded audio/video media is disabled in offline presentation mode.",
            detailAr = "تم تعطيل تشغيل مقاطع الصوت والفيديو المضمنة في وضع العرض غير المتصل."
          )
        )
      }
      if (hasEmbeddings) {
        add(
          CompatibilityWarning(
            code = "OLE_EMBEDDING",
            titleEn = "Embedded OLE Objects",
            titleAr = "كائنات OLE مضمّنة",
            detailEn = "Embedded external objects (Excel sheets/OLE) cannot be launched inline.",
            detailAr = "لا يمكن تشغيل الكائنات المضمنة مثل جداول Excel المدمجة مباشرة."
          )
        )
      }
      if (hasMacros) {
        add(
          CompatibilityWarning(
            code = "MACROS_BLOCKED",
            titleEn = "Macros Stripped",
            titleAr = "تعطيل وحدات الماكرو",
            detailEn = "VBA macros are ignored for security.",
            detailAr = "يتم تجاهل وحدات الماكرو البرمجية لأسباب أمنية."
          )
        )
      }
      if (hasCommentsOrMath && format == DocumentFormat.DOCX) {
        add(
          CompatibilityWarning(
            code = "COMMENTS",
            titleEn = "Review Comments",
            titleAr = "تعليقات المراجعة",
            detailEn = "Review comments are shown inline via 💬 badges (tap to open) and in the Review Comments section at the bottom of the document.",
            detailAr = "تظهر تعليقات المراجعة عبر أيقونات 💬 داخل النص (اضغط عليها للعرض) وفي قسم «تعليقات المراجعة» أسفل المستند."
          )
        )
      }
    }

    return OpenXmlInspection(detectedFormat = format, slideCount = effectiveCount, warnings = warnings)
  }

  private fun InputStream.readNBytesBounded(limit: Int): ByteArray {
    val buf = ByteArray(4096)
    val out = java.io.ByteArrayOutputStream()
    var read: Int
    while (read(buf).also { read = it } != -1) {
      val rem = limit - out.size()
      if (rem <= 0) break
      out.write(buf, 0, minOf(read, rem))
    }
    return out.toByteArray()
  }
}
