package com.example.documentviewer.model

import androidx.compose.ui.graphics.Color

enum class DocumentCategory {
  PDF,
  WORD,
  POWERPOINT,
  EXCEL,
  ACCESS,
  HTML,
  CODE,
  OTHER
}

enum class SupportLevel {
  FULL,
  PARTIAL,
  UNSUPPORTED
}

enum class DocumentFormat(
  val extension: String,
  val label: String,
  val category: DocumentCategory,
  val supportLevel: SupportLevel,
  val primaryMimeType: String,
  val accentColor: Color,
  val tintColor: Color
) {
  PDF(
    extension = "pdf",
    label = "PDF",
    category = DocumentCategory.PDF,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/pdf",
    accentColor = Color(0xFFD93829),
    tintColor = Color(0xFFFDECEC)
  ),
  DOCX(
    extension = "docx",
    label = "DOCX",
    category = DocumentCategory.WORD,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    accentColor = Color(0xFF1B6EF3),
    tintColor = Color(0xFFE9F1FE)
  ),
  DOC(
    extension = "doc",
    label = "DOC",
    category = DocumentCategory.WORD,
    supportLevel = SupportLevel.PARTIAL,
    primaryMimeType = "application/msword",
    accentColor = Color(0xFF3B82F6),
    tintColor = Color(0xFFEFF6FF)
  ),
  PPTX(
    extension = "pptx",
    label = "PPTX",
    category = DocumentCategory.POWERPOINT,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    accentColor = Color(0xFFE05A12),
    tintColor = Color(0xFFFFF1E6)
  ),
  PPSX(
    extension = "ppsx",
    label = "PPSX",
    category = DocumentCategory.POWERPOINT,
    supportLevel = SupportLevel.PARTIAL,
    primaryMimeType = "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
    accentColor = Color(0xFFD97706),
    tintColor = Color(0xFFFEF3C7)
  ),
  PPT(
    extension = "ppt",
    label = "PPT",
    category = DocumentCategory.POWERPOINT,
    supportLevel = SupportLevel.PARTIAL,
    primaryMimeType = "application/vnd.ms-powerpoint",
    accentColor = Color(0xFFEA580C),
    tintColor = Color(0xFFFFEDD5)
  ),
  XLSX(
    extension = "xlsx",
    label = "XLSX",
    category = DocumentCategory.EXCEL,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    accentColor = Color(0xFF15803D),
    tintColor = Color(0xFFDCFCE7)
  ),
  XLS(
    extension = "xls",
    label = "XLS",
    category = DocumentCategory.EXCEL,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/vnd.ms-excel",
    accentColor = Color(0xFF16A34A),
    tintColor = Color(0xFFF0FDF4)
  ),
  ACCDB(
    extension = "accdb",
    label = "ACCDB",
    category = DocumentCategory.ACCESS,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/msaccess",
    accentColor = Color(0xFFA4373A),
    tintColor = Color(0xFFFEE2E2)
  ),
  MDB(
    extension = "mdb",
    label = "MDB",
    category = DocumentCategory.ACCESS,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "application/x-msaccess",
    accentColor = Color(0xFFB91C1C),
    tintColor = Color(0xFFFEF2F2)
  ),
  HTML(
    extension = "html",
    label = "HTML",
    category = DocumentCategory.HTML,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "text/html",
    accentColor = Color(0xFF0D9488),
    tintColor = Color(0xFFCCFBF1)
  ),
  CODE(
    extension = "code",
    label = "CODE",
    category = DocumentCategory.CODE,
    supportLevel = SupportLevel.FULL,
    primaryMimeType = "text/plain",
    accentColor = Color(0xFF7C3AED),
    tintColor = Color(0xFFEDE9FE)
  ),
  UNKNOWN(
    extension = "",
    label = "FILE",
    category = DocumentCategory.OTHER,
    supportLevel = SupportLevel.UNSUPPORTED,
    primaryMimeType = "application/octet-stream",
    accentColor = Color(0xFF64748B),
    tintColor = Color(0xFFF1F5F9)
  );

  companion object {
    val CODE_EXTENSIONS = setOf(
      "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
      "json", "xml", "css", "scss", "less", "cpp", "cc", "cxx", "c", "h", "hpp",
      "cs", "go", "rs", "rb", "php", "swift", "dart", "sh", "bash", "zsh", "bat",
      "ps1", "sql", "yaml", "yml", "toml", "ini", "conf", "cfg", "gradle",
      "properties", "md", "markdown", "txt", "log"
    )

    val EXCEL_MODERN_EXTENSIONS = setOf("xlsx", "xlsm", "xltx", "xltm", "xlsb")
    val EXCEL_LEGACY_AND_SHEET_EXTENSIONS = setOf("xls", "xlt", "xla", "ods", "csv", "tsv", "fods")
    val ACCESS_MODERN_EXTENSIONS = setOf("accdb", "accde", "accdt", "accdr", "accda")
    val ACCESS_LEGACY_EXTENSIONS = setOf("mdb", "mde", "mda")

    val HTML_EXTENSIONS = setOf("html", "htm", "xhtml", "shtml")

    fun fromExtension(ext: String?): DocumentFormat {
      val clean = ext?.trim()?.lowercase()?.removePrefix(".") ?: return UNKNOWN
      return when {
        clean == "pdf" -> PDF
        clean in setOf("docx", "docm", "dotx", "dotm") -> DOCX
        clean in setOf("doc", "dot", "rtf", "odt") -> DOC
        clean in setOf("pptx", "pptm", "potx", "potm") -> PPTX
        clean in setOf("ppsx", "ppsm") -> PPSX
        clean in setOf("ppt", "pps", "pot", "odp") -> PPT
        clean in EXCEL_MODERN_EXTENSIONS -> XLSX
        clean in EXCEL_LEGACY_AND_SHEET_EXTENSIONS -> XLS
        clean in ACCESS_MODERN_EXTENSIONS -> ACCDB
        clean in ACCESS_LEGACY_EXTENSIONS -> MDB
        clean in HTML_EXTENSIONS -> HTML
        clean in CODE_EXTENSIONS -> CODE
        else -> UNKNOWN
      }
    }

    fun fromMimeType(mime: String?): DocumentFormat {
      val clean = mime?.trim()?.lowercase()?.substringBefore(";") ?: return UNKNOWN
      return when {
        clean == "application/pdf" || clean == "application/x-pdf" -> PDF
        clean.contains("wordprocessingml") || clean.contains("ms-word.document") -> DOCX
        clean == "application/msword" ||
          clean == "application/vnd.ms-word" ||
          clean == "application/rtf" ||
          clean == "text/rtf" ||
          clean == "application/vnd.oasis.opendocument.text" -> DOC
        clean.contains("presentationml.slideshow") || clean.contains("ms-powerpoint.slideshow") -> PPSX
        clean.contains("presentationml") || clean.contains("ms-powerpoint.presentation") -> PPTX
        clean == "application/vnd.ms-powerpoint" ||
          clean == "application/mspowerpoint" ||
          clean == "application/vnd.oasis.opendocument.presentation" -> PPT
        clean.contains("spreadsheetml") || clean.contains("ms-excel.sheet") -> XLSX
        clean == "application/vnd.ms-excel" ||
          clean == "application/msexcel" ||
          clean == "application/x-msexcel" ||
          clean == "application/vnd.oasis.opendocument.spreadsheet" ||
          clean == "text/csv" ||
          clean == "text/tab-separated-values" ||
          clean == "application/csv" -> XLS
        clean == "application/msaccess" ||
          clean == "application/vnd.ms-access" ||
          clean == "application/x-msaccess" -> ACCDB
        clean == "text/html" || clean == "application/xhtml+xml" -> HTML
        clean.startsWith("text/") ||
          clean == "application/json" ||
          clean == "application/xml" ||
          clean == "application/javascript" ||
          clean == "application/x-javascript" ||
          clean == "application/sql" -> CODE
        else -> UNKNOWN
      }
    }
  }
}

data class CompatibilityWarning(
  val code: String,
  val titleEn: String,
  val titleAr: String,
  val detailEn: String,
  val detailAr: String
)

data class OutlineEntry(
  val title: String,
  val page: Int,
  val depth: Int
)

data class SlideSummary(
  val index: Int,
  val snippet: String
)
