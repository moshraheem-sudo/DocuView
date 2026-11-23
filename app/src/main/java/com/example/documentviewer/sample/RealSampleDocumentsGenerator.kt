package com.example.documentviewer.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates real, standards-compliant sample documents (PDF, DOCX, PPTX) locally on device
 * in `filesDir/imported_docs/` so users can immediately test and inspect all three rendering
 * engines, Arabic RTL, English LTR, tables, shapes, headers/footers, and multi-page navigation.
 */
object RealSampleDocumentsGenerator {

  data class GeneratedSample(
    val file: File,
    val displayName: String,
    val mimeType: String
  )

  suspend fun ensureSampleDocuments(context: Context): List<GeneratedSample> =
    withContext(Dispatchers.IO) {
      val dir = File(context.filesDir, "imported_docs").apply { mkdirs() }

      val pdfFile = File(dir, "DocuView_Architecture_Guide_AR_EN.pdf")
      if (!pdfFile.exists() || pdfFile.length() < 512L) {
        generateRealMultiPagePdf(pdfFile)
      }

      val docxFile = File(dir, "Enterprise_Specification_Report_RTL.docx")
      if (!docxFile.exists() || docxFile.length() < 512L) {
        generateRealOpenXmlDocx(docxFile)
      }

      val pptxFile = File(dir, "Product_Strategy_Presentation_2026.pptx")
      val pptxMarker = File(dir, ".pptx_sample_v2")
      if (!pptxFile.exists() || pptxFile.length() < 4500L || !pptxMarker.exists()) {
        generateRealOpenXmlPptx(pptxFile)
        runCatching { pptxMarker.writeText("v2") }
      }

      val xlsxFile = File(dir, "Financial_Budget_Report_2026.xlsx")
      if (!xlsxFile.exists() || xlsxFile.length() < 512L) {
        generateRealOpenXmlXlsx(xlsxFile)
      }

      val accdbFile = File(dir, "Company_Directory_Sample.accdb")
      if (!accdbFile.exists() || accdbFile.length() < 4096L) {
        runCatching { generateRealAccessAccdb(accdbFile) }
      }

      buildList {
        add(
          GeneratedSample(
            file = pdfFile,
            displayName = pdfFile.name,
            mimeType = "application/pdf"
          )
        )
        add(
          GeneratedSample(
            file = docxFile,
            displayName = docxFile.name,
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          )
        )
        add(
          GeneratedSample(
            file = pptxFile,
            displayName = pptxFile.name,
            mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
          )
        )
        add(
          GeneratedSample(
            file = xlsxFile,
            displayName = xlsxFile.name,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          )
        )
        if (accdbFile.exists() && accdbFile.length() > 0L) {
          add(
            GeneratedSample(
              file = accdbFile,
              displayName = accdbFile.name,
              mimeType = "application/x-msaccess"
            )
          )
        }
      }
    }

  private fun generateRealMultiPagePdf(target: File) {
    var doc: PdfDocument? = null
    try {
      doc = PdfDocument()
      val width = 595
      val height = 842

      // Page 1: Cover & Bilingual Architecture Overview
      run {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
        drawPdfPage1(page.canvas, width, height)
        doc.finishPage(page)
      }

      // Page 2: Engine Matrix & Performance Table
      run {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 2).create())
        drawPdfPage2(page.canvas, width, height)
        doc.finishPage(page)
      }

      // Page 3: Zero-Permission Security & Sandboxing
      run {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 3).create())
        drawPdfPage3(page.canvas, width, height)
        doc.finishPage(page)
      }

      FileOutputStream(target).use { doc.writeTo(it) }
      if (target.exists() && target.length() > 512L) return
    } catch (_: Throwable) {
      // Fallback for JVM test environments where native PdfDocument C++ bindings are unavailable
    } finally {
      runCatching { doc?.close() }
    }
    writePurePdf14Fallback(target)
  }

  private fun writePurePdf14Fallback(target: File) {
    val page1Stream = """
      0.086 0.169 0.302 rg
      36 674 523 132 re f
      1 1 1 rg
      BT /F1 20 Tf 56 760 Td (DocuView - Universal Document Engine) Tj ET
      BT /F1 12 Tf 56 730 Td (PDF, Word, Excel, Access & PowerPoint Native Android Viewer) Tj ET
      0.118 0.161 0.231 rg
      BT /F1 14 Tf 44 620 Td (1. Executive Overview & Sandboxed Architecture) Tj ET
      BT /F1 11 Tf 44 590 Td (DocuView renders PDF, DOCX, XLSX, ACCDB, MDB, and PPTX locally) Tj ET
      BT /F1 11 Tf 44 568 Td (inside a hardened Android WebViewAssetLoader sandbox with zero network permissions.) Tj ET
      BT /F1 11 Tf 44 536 Td (- High-DPI vector text rendering with selectable text layer) Tj ET
      BT /F1 11 Tf 44 514 Td (- Full-text search highlighting, thumbnails, bookmarks, and rotation) Tj ET
      BT /F1 11 Tf 44 484 Td (Developer: Mohammed Shaheed Raheem) Tj ET
      BT /F1 10 Tf 44 40 Td (DocuView Native Android Document Engine - Page 1 of 3) Tj ET
    """.trimIndent()

    val page2Stream = """
      0.086 0.169 0.302 rg
      BT /F1 16 Tf 44 770 Td (2. Format Compatibility Matrix) Tj ET
      0.118 0.161 0.231 rg
      BT /F1 12 Tf 44 720 Td (PDF (.pdf) - FULL SUPPORT - Mozilla PDF.js 3.11 Apache-2.0) Tj ET
      BT /F1 12 Tf 44 692 Td (Word (.docx) - FULL SUPPORT - docx-preview 0.3 Apache-2.0) Tj ET
      BT /F1 12 Tf 44 664 Td (PowerPoint (.pptx) - FULL SUPPORT - PPTXjs 1.21 MIT License) Tj ET
      BT /F1 12 Tf 44 636 Td (Slideshow (.ppsx) - PARTIAL SUPPORT - Visual slides supported) Tj ET
      BT /F1 12 Tf 44 608 Td (Legacy (.doc / .ppt) - PARTIAL SUPPORT - Ole2LegacyParser CFBF) Tj ET
      BT /F1 10 Tf 44 40 Td (DocuView Native Android Document Engine - Page 2 of 3) Tj ET
    """.trimIndent()

    val page3Stream = """
      0.086 0.169 0.302 rg
      BT /F1 16 Tf 44 770 Td (3. Security & Offline Sandbox Guarantees) Tj ET
      0.118 0.161 0.231 rg
      BT /F1 12 Tf 44 720 Td (- Zero INTERNET Permission in AndroidManifest.xml) Tj ET
      BT /F1 12 Tf 44 692 Td (- Storage Access Framework ACTION_OPEN_DOCUMENT & ContentResolver) Tj ET
      BT /F1 12 Tf 44 664 Td (- Ephemeral UUID Session Tokens & Strict Content-Security-Policy) Tj ET
      BT /F1 10 Tf 44 40 Td (DocuView Native Android Document Engine - Page 3 of 3) Tj ET
    """.trimIndent()

    val objects = listOf(
      "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
      "2 0 obj\n<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>\nendobj\n",
      "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 6 0 R >> >> /Contents 7 0 R >>\nendobj\n",
      "4 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 6 0 R >> >> /Contents 8 0 R >>\nendobj\n",
      "5 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 6 0 R >> >> /Contents 9 0 R >>\nendobj\n",
      "6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n",
      "7 0 obj\n<< /Length ${page1Stream.toByteArray().size} >>\nstream\n$page1Stream\nendstream\nendobj\n",
      "8 0 obj\n<< /Length ${page2Stream.toByteArray().size} >>\nstream\n$page2Stream\nendstream\nendobj\n",
      "9 0 obj\n<< /Length ${page3Stream.toByteArray().size} >>\nstream\n$page3Stream\nendstream\nendobj\n"
    )

    val sb = StringBuilder("%PDF-1.4\n")
    val offsets = IntArray(objects.size)
    for (i in objects.indices) {
      offsets[i] = sb.toString().toByteArray(Charsets.ISO_8859_1).size
      sb.append(objects[i])
    }
    val xrefOffset = sb.toString().toByteArray(Charsets.ISO_8859_1).size
    sb.append("xref\n0 ${objects.size + 1}\n")
    sb.append("0000000000 65535 f \n")
    for (off in offsets) {
      sb.append(String.format(java.util.Locale.US, "%010d 00000 n \n", off))
    }
    sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
    target.writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1))
  }

  private fun drawPdfPage1(canvas: Canvas, w: Int, h: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    // Header Banner
    paint.color = Color.parseColor("#162B4D")
    canvas.drawRoundRect(RectF(36f, 36f, w - 36f, 168f), 16f, 16f, paint)

    paint.color = Color.WHITE
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 22f
    canvas.drawText("DocuView — Universal Document Engine", 56f, 84f, paint)

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 14f
    paint.color = Color.parseColor("#D6E4FF")
    canvas.drawText("دليل معمارية مشغّل المستندات الشامل (PDF • Word • Excel • Access • PowerPoint)", 56f, 115f, paint)
    canvas.drawText("Developer: Mohammed Shaheed Raheem • Local Sandboxed Engine", 56f, 142f, paint)

    // Section 1
    var y = 215f
    paint.color = Color.parseColor("#D93829")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 16f
    canvas.drawText("1. Executive Overview | نظرة عامة تنفيذية", 44f, y, paint)

    y += 28f
    paint.color = Color.parseColor("#1E293B")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 12.5f
    val lines = listOf(
      "DocuView renders PDF, Word, Excel, Access Databases, and PowerPoint",
      "locally inside a hardened Android WebViewAssetLoader sandbox.",
      "يدعم المشغّل عرض ملفات PDF وWord وExcel وAccess وPowerPoint مع دعم العربية RTL",
      "والبحث الفوري داخل النصوص، وتحديد النص ونسخه، وتكبير العرض، والمشاركة والطباعة.",
      "",
      "Key Capabilities Included in This Real PDF Document:",
      "• High-DPI vector text rendering with selectable & copyable text layer",
      "• Full-text search highlighting (Try searching 'Offline' or 'Sandbox' or 'المستندات')",
      "• Page thumbnails grid, bookmarks, 90° clockwise rotation, and Night reading mode"
    )
    for (line in lines) {
      canvas.drawText(line, 44f, y, paint)
      y += 22f
    }

    // Visual Architecture Diagram Box
    y += 18f
    paint.color = Color.parseColor("#F1F5F9")
    canvas.drawRoundRect(RectF(44f, y, w - 44f, y + 170f), 12f, 12f, paint)

    paint.color = Color.parseColor("#162B4D")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 13f
    canvas.drawText("Modular Rendering Pipeline", 64f, y + 30f, paint)

    val boxes = listOf(
      Triple("PDF Engine", "Mozilla PDF.js 3.11\nApache-2.0", "#D93829"),
      Triple("Word Engine", "docx-preview 0.3\nApache-2.0", "#1B6EF3"),
      Triple("Slides Engine", "PPTXjs 1.21 + D3\nMIT License", "#E05A12")
    )
    var bx = 64f
    for ((title, sub, hex) in boxes) {
      paint.color = Color.parseColor(hex)
      canvas.drawRoundRect(RectF(bx, y + 50f, bx + 145f, y + 145f), 10f, 10f, paint)
      paint.color = Color.WHITE
      paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      paint.textSize = 13f
      canvas.drawText(title, bx + 14f, y + 80f, paint)
      paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      paint.textSize = 11f
      val parts = sub.split("\n")
      canvas.drawText(parts[0], bx + 14f, y + 104f, paint)
      canvas.drawText(parts.getOrElse(1) { "" }, bx + 14f, y + 124f, paint)
      bx += 162f
    }

    drawPdfFooter(canvas, w, h, 1, 3)
  }

  private fun drawPdfPage2(canvas: Canvas, w: Int, h: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    paint.color = Color.parseColor("#162B4D")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 18f
    canvas.drawText("2. Format Compatibility Matrix | مصفوفة الصيغ المدعومة", 44f, 64f, paint)

    // Table Header
    var top = 96f
    val left = 44f
    val right = w - 44f
    val rowH = 44f

    paint.color = Color.parseColor("#162B4D")
    canvas.drawRect(left, top, right, top + rowH, paint)
    paint.color = Color.WHITE
    paint.textSize = 12f
    canvas.drawText("Format", left + 14f, top + 27f, paint)
    canvas.drawText("Support Level", left + 100f, top + 27f, paint)
    canvas.drawText("Engine & Capabilities", left + 220f, top + 27f, paint)

    val rows = listOf(
      Triple("PDF (.pdf)", "FULL SUPPORT", "PDF.js 3.11: Zoom, Search, Copy, Rotate, Bookmarks, Print"),
      Triple("Word (.docx)", "FULL SUPPORT", "docx-preview: Pages, Tables, Images, Headers, RTL Arabic"),
      Triple("Slides (.pptx)", "FULL SUPPORT", "PPTXjs: Slides, Shapes, Tables, Presentation Mode, Swipe"),
      Triple("Show (.ppsx)", "PARTIAL", "PPTXjs: Visual slides supported; auto-timings omitted"),
      Triple("Legacy (.doc)", "PARTIAL", "Ole2LegacyParser: UTF-16LE / Arabic text stream extraction"),
      Triple("Legacy (.ppt)", "PARTIAL", "Ole2LegacyParser: Binary slide text record cards")
    )

    top += rowH
    rows.forEachIndexed { idx, (fmt, level, desc) ->
      paint.color = if (idx % 2 == 0) Color.parseColor("#F8FAFC") else Color.WHITE
      canvas.drawRect(left, top, right, top + rowH, paint)
      paint.color = Color.parseColor("#CBD5E1")
      canvas.drawLine(left, top + rowH, right, top + rowH, paint)

      paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      paint.color = Color.parseColor("#0F172A")
      canvas.drawText(fmt, left + 14f, top + 27f, paint)

      paint.color = if (level.contains("FULL")) Color.parseColor("#15803D") else Color.parseColor("#B45309")
      canvas.drawText(level, left + 100f, top + 27f, paint)

      paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
      paint.color = Color.parseColor("#334155")
      canvas.drawText(desc, left + 220f, top + 27f, paint)
      top += rowH
    }

    drawPdfFooter(canvas, w, h, 2, 3)
  }

  private fun drawPdfPage3(canvas: Canvas, w: Int, h: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    paint.color = Color.parseColor("#162B4D")
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 18f
    canvas.drawText("3. Security & Offline Sandbox | الأمان والعزل المحلي", 44f, 64f, paint)

    var y = 110f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 13f
    paint.color = Color.parseColor("#1E293B")
    val securityNotes = listOf(
      "• Zero INTERNET Permission: The AndroidManifest declares no network permissions.",
      "• Storage Access Framework (SAF): Uses ACTION_OPEN_DOCUMENT & ContentResolver.",
      "• WebViewAssetLoader: Serves local engines over https://appassets.androidplatform.net.",
      "• Ephemeral UUID Session Tokens: Each opened document uses a one-time session path.",
      "• Strict Content-Security-Policy (CSP): Blocks external requests, object/frame tags.",
      "",
      "ضمانات الخصوصية والأمان في التطبيق:",
      "• يعمل التطبيق محلياً ولا يرفع أي مستند إلى أي خادم خارجي.",
      "• يتم تعطيل allowFileAccess وallowContentAccess داخل WebView لمنع تسريب الملفات.",
      "• المطور: Mohammed Shaheed Raheem — DocuView Universal Document Viewer."
    )
    for (note in securityNotes) {
      canvas.drawText(note, 44f, y, paint)
      y += 26f
    }

    drawPdfFooter(canvas, w, h, 3, 3)
  }

  private fun drawPdfFooter(canvas: Canvas, w: Int, h: Int, page: Int, total: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.parseColor("#94A3B8")
    paint.textSize = 11f
    canvas.drawLine(44f, h - 52f, w - 44f, h - 52f, paint)
    canvas.drawText("DocuView • Mohammed Shaheed Raheem", 44f, h - 32f, paint)
    canvas.drawText("Page $page of $total", w - 110f, h - 32f, paint)
  }

  private fun generateRealOpenXmlDocx(target: File) {
    ZipOutputStream(FileOutputStream(target)).use { zip ->
      fun entry(name: String, xml: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(xml.trimIndent().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }

      entry(
        "[Content_Types].xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
          <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
          <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
        </Types>
        """
      )

      entry(
        "_rels/.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
        """
      )

      entry(
        "word/_rels/document.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
        </Relationships>
        """
      )

      entry(
        "word/header1.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:p>
            <w:pPr><w:jc w:val="right"/></w:pPr>
            <w:r>
              <w:rPr><w:color w:val="64748B"/><w:sz w:val="18"/></w:rPr>
              <w:t>DocuView Word Engine • مواصفات المحرك المحلي (DOCX Visual Renderer)</w:t>
            </w:r>
          </w:p>
        </w:hdr>
        """
      )

      entry(
        "word/footer1.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
              <w:rPr><w:color w:val="64748B"/><w:sz w:val="18"/></w:rPr>
              <w:t>DocuView • Developed by Mohammed Shaheed Raheem</w:t>
            </w:r>
          </w:p>
        </w:ftr>
        """
      )

      entry(
        "word/styles.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
            <w:name w:val="Normal"/>
            <w:rPr>
              <w:rFonts w:ascii="Cairo" w:hAnsi="Cairo" w:cs="Cairo"/>
              <w:sz w:val="24"/>
            </w:rPr>
          </w:style>
        </w:styles>
        """
      )

      entry(
        "word/document.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:p>
              <w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr>
              <w:r>
                <w:rPr><w:b/><w:color w:val="162B4D"/><w:sz w:val="36"/></w:rPr>
                <w:t>تقرير المواصفات الفنية لمحرّك مستندات Word (DOCX)</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr>
              <w:r>
                <w:rPr><w:color w:val="1B6EF3"/><w:sz w:val="24"/></w:rPr>
                <w:t>يعمل هذا المستند الحقيقي بصيغة OpenXML (.docx) محلياً عبر مكتبة docx-preview مع دعم كامل للعربية RTL ونسخ النصوص والجداول والرؤوس والتذييلات.</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:r>
                <w:rPr><w:b/><w:color w:val="0F172A"/><w:sz w:val="26"/></w:rPr>
                <w:t>Bilingual Rendering, Text Copy &amp; Typography Verification</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:r>
                <w:rPr><w:color w:val="334155"/><w:sz w:val="22"/></w:rPr>
                <w:t>You can select and copy any text directly from this Word document or use the Copy button in the bottom toolbar to extract or copy the entire page text.</w:t>
              </w:r>
            </w:p>
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="9000" w:type="dxa"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
                  <w:insideH w:val="single" w:sz="6" w:space="0" w:color="E2E8F0"/>
                  <w:insideV w:val="single" w:sz="6" w:space="0" w:color="E2E8F0"/>
                </w:tblBorders>
              </w:tblPr>
              <w:tr>
                <w:tc>
                  <w:tcPr><w:shd w:val="clear" w:fill="162B4D"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:color w:val="FFFFFF"/></w:rPr><w:t>الميزة / Feature</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:shd w:val="clear" w:fill="162B4D"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:color w:val="FFFFFF"/></w:rPr><w:t>الحالة / Status</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr><w:shd w:val="clear" w:fill="162B4D"/></w:tcPr>
                  <w:p><w:r><w:rPr><w:b/><w:color w:val="FFFFFF"/></w:rPr><w:t>التفاصيل التقنية / Details</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
              <w:tr>
                <w:tc><w:p><w:r><w:b/><w:t>دعم العربية واتجاه RTL</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:rPr><w:color w:val="15803D"/><w:b/></w:rPr><w:t>مكتمل 100%</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>محاذاة الفقرات والجداول وتبديل اتجاه النص فورياً</w:t></w:r></w:p></w:tc>
              </w:tr>
              <w:tr>
                <w:tc><w:p><w:r><w:b/><w:t>نسخ وتحديد النصوص</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:rPr><w:color w:val="15803D"/><w:b/></w:rPr><w:t>مدعوم بالكامل</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>تحديد النص بالإصبع أو نسخ نص الصفحة بالكامل بضغطة زر</w:t></w:r></w:p></w:tc>
              </w:tr>
              <w:tr>
                <w:tc><w:p><w:r><w:b/><w:t>البحث والتصدير إلى PDF</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:rPr><w:color w:val="15803D"/><w:b/></w:rPr><w:t>مدعوم بالكامل</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>بحث فوري مع تمييز النتائج وتصدير إلى PDF عبر نظام الطباعة</w:t></w:r></w:p></w:tc>
              </w:tr>
            </w:tbl>
            <w:p>
              <w:r><w:br w:type="page"/></w:r>
            </w:p>
            <w:p>
              <w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr>
              <w:r>
                <w:rPr><w:b/><w:color w:val="162B4D"/><w:sz w:val="30"/></w:rPr>
                <w:t>الصفحة الثانية: اختبار فواصل الصفحات والوضع الليلي</w:t>
              </w:r>
            </w:p>
            <w:p>
              <w:pPr><w:bidi/><w:jc w:val="right"/></w:pPr>
              <w:r>
                <w:rPr><w:sz w:val="22"/></w:rPr>
                <w:t>يمكنك تجربة شريط البحث في الأعلى للبحث عن كلمة "المواصفات" أو "OpenXML" وملاحظة التمييز الفوري للنتائج، أو الضغط على أيقونة النسخ في الشريط السفلي لنسخ النصوص.</w:t>
              </w:r>
            </w:p>
            <w:sectPr>
              <w:headerReference w:type="default" r:id="rId2" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
              <w:footerReference w:type="default" r:id="rId3" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
              <w:pgSz w:w="11906" w:h="16838"/>
              <w:pgMar w:top="1080" w:right="1080" w:bottom="1080" w:left="1080" w:header="540" w:footer="540" w:gutter="0"/>
            </w:sectPr>
          </w:body>
        </w:document>
        """
      )
    }
  }

  private fun generateRealOpenXmlPptx(target: File) {
    ZipOutputStream(FileOutputStream(target)).use { zip ->
      fun entry(name: String, xml: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(xml.trimIndent().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }

      entry(
        "[Content_Types].xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
          <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
          <Override PartName="/ppt/tableStyles.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"/>
          <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
          <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
          <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
          <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
          <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
          <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
        </Types>
        """
      )

      entry(
        "_rels/.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
        </Relationships>
        """
      )

      entry(
        "docProps/app.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
          <Application>Microsoft Office PowerPoint</Application>
          <AppVersion>16.0000</AppVersion>
          <Slides>3</Slides>
        </Properties>
        """
      )

      entry(
        "ppt/tableStyles.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:tblStyleLst xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>
        """
      )

      entry(
        "ppt/_rels/presentation.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
          <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
          <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
          <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles" Target="tableStyles.xml"/>
        </Relationships>
        """
      )

      entry(
        "ppt/presentation.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:sldMasterIdLst>
            <p:sldMasterId id="2147483648" r:id="rId4"/>
          </p:sldMasterIdLst>
          <p:sldIdLst>
            <p:sldId id="256" r:id="rId1"/>
            <p:sldId id="257" r:id="rId2"/>
            <p:sldId id="258" r:id="rId3"/>
          </p:sldIdLst>
          <p:sldSz cx="9144000" cy="5143500" type="screen16x9"/>
          <p:notesSz cx="6858000" cy="9144000"/>
        </p:presentation>
        """
      )

      entry(
        "ppt/theme/theme1.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="DocuViewTheme">
          <a:themeElements>
            <a:clrScheme name="Office">
              <a:dk1><a:srgbClr val="0F172A"/></a:dk1>
              <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
              <a:dk2><a:srgbClr val="162B4D"/></a:dk2>
              <a:lt2><a:srgbClr val="F1F5F9"/></a:lt2>
              <a:accent1><a:srgbClr val="1B6EF3"/></a:accent1>
              <a:accent2><a:srgbClr val="E05A12"/></a:accent2>
              <a:accent3><a:srgbClr val="15803D"/></a:accent3>
              <a:accent4><a:srgbClr val="D97706"/></a:accent4>
              <a:accent5><a:srgbClr val="7C3AED"/></a:accent5>
              <a:accent6><a:srgbClr val="0284C7"/></a:accent6>
              <a:hlink><a:srgbClr val="1B6EF3"/></a:hlink>
              <a:folHlink><a:srgbClr val="64748B"/></a:folHlink>
            </a:clrScheme>
            <a:fontScheme name="Office">
              <a:majorFont><a:latin typeface="Cairo"/><a:ea typeface=""/><a:cs typeface="Cairo"/></a:majorFont>
              <a:minorFont><a:latin typeface="Cairo"/><a:ea typeface=""/><a:cs typeface="Cairo"/></a:minorFont>
            </a:fontScheme>
            <a:fmtScheme name="Office">
              <a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst>
              <a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst>
              <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>
              <a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst>
            </a:fmtScheme>
          </a:themeElements>
        </a:theme>
        """
      )

      entry(
        "ppt/slideMasters/_rels/slideMaster1.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
        </Relationships>
        """
      )

      entry(
        "ppt/slideMasters/slideMaster1.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld>
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
            </p:spTree>
          </p:cSld>
          <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
          <p:sldLayoutIdLst>
            <p:sldLayoutId id="2147483649" r:id="rId1"/>
          </p:sldLayoutIdLst>
          <p:txStyles>
            <p:titleStyle><a:lvl1pPr><a:defRPr sz="3200"/></a:lvl1pPr></p:titleStyle>
            <p:bodyStyle><a:lvl1pPr><a:defRPr sz="2000"/></a:lvl1pPr></p:bodyStyle>
            <p:otherStyle><a:lvl1pPr><a:defRPr sz="1800"/></a:lvl1pPr></p:otherStyle>
          </p:txStyles>
        </p:sldMaster>
        """
      )

      entry(
        "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
        </Relationships>
        """
      )

      entry(
        "ppt/slideLayouts/slideLayout1.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                     xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                     type="blank" preserve="1">
          <p:cSld name="Blank">
            <p:spTree>
              <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
              <p:grpSpPr/>
            </p:spTree>
          </p:cSld>
          <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
        </p:sldLayout>
        """
      )

      val slideRels = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
        </Relationships>
      """
      entry("ppt/slides/_rels/slide1.xml.rels", slideRels)
      entry("ppt/slides/_rels/slide2.xml.rels", slideRels)
      entry("ppt/slides/_rels/slide3.xml.rels", slideRels)

      entry(
        "ppt/slides/slide1.xml",
        buildSlideXml(
          bgHex = "162B4D",
          title = "DocuView PowerPoint Engine",
          subtitle = "عرض تقديمي تفاعلي • PPTXjs Deck",
          bullet1 = "1. High-precision slide scaling & Presentation Mode",
          bullet2 = "2. Swipe navigation, slide thumbnails strip & text search",
          bullet3 = "3. Developed by Mohammed Shaheed Raheem • DocuView v1.3.0",
          accentHex = "E05A12",
          titleColor = "FFFFFF",
          bodyColor = "E2E8F0"
        )
      )

      entry(
        "ppt/slides/slide2.xml",
        buildSlideXml(
          bgHex = "FFFFFF",
          title = "المميزات التقنية لمشغّل العروض التقديمية",
          subtitle = "دعم النصوص العربية والإنجليزية والأشكال الهندسية",
          bullet1 = "• التنقل السلس بين الشرائح بالسحب أو أزرار السابق والتالي",
          bullet2 = "• البحث الفوري داخل نصوص الشرائح ونسخ النصوص وتصدير PDF",
          bullet3 = "• متوافق مع جميع عروض PowerPoint القياسية محلياً",
          accentHex = "1B6EF3",
          titleColor = "162B4D",
          bodyColor = "334155"
        )
      )

      entry(
        "ppt/slides/slide3.xml",
        buildSlideXml(
          bgHex = "0F172A",
          title = "Zero-Trust Offline Security Architecture",
          subtitle = "حماية خصوصية المستندات وعزل محتوى WebView",
          bullet1 = "• No INTERNET permission in AndroidManifest.xml",
          bullet2 = "• Ephemeral session tokens & strict Content-Security-Policy",
          bullet3 = "• Instant text extraction & clipboard copy for all slides",
          accentHex = "15803D",
          titleColor = "F8FAFC",
          bodyColor = "CBD5E1"
        )
      )
    }
  }

  private fun buildSlideXml(
    bgHex: String,
    title: String,
    subtitle: String,
    bullet1: String,
    bullet2: String,
    bullet3: String,
    accentHex: String,
    titleColor: String,
    bodyColor: String
  ): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
           xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
      <p:cSld>
        <p:bg>
          <p:bgPr>
            <a:solidFill><a:srgbClr val="$bgHex"/></a:solidFill>
          </p:bgPr>
        </p:bg>
        <p:spTree>
          <p:nvGrpSpPr>
            <p:cNvPr id="1" name=""/>
            <p:cNvGrpSpPr/>
            <p:nvPr/>
          </p:nvGrpSpPr>
          <p:grpSpPr/>
          <p:sp>
            <p:nvSpPr>
              <p:cNvPr id="2" name="AccentBar"/>
              <p:cNvSpPr/>
              <p:nvPr/>
            </p:nvSpPr>
            <p:spPr>
              <a:xfrm>
                <a:off x="457200" y="457200"/>
                <a:ext cx="8229600" cy="182880"/>
              </a:xfrm>
              <a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom>
              <a:solidFill><a:srgbClr val="$accentHex"/></a:solidFill>
            </p:spPr>
          </p:sp>
          <p:sp>
            <p:nvSpPr>
              <p:cNvPr id="3" name="TitleBox"/>
              <p:cNvSpPr txBox="1"/>
              <p:nvPr/>
            </p:nvSpPr>
            <p:spPr>
              <a:xfrm>
                <a:off x="548640" y="822960"/>
                <a:ext cx="8046720" cy="1463040"/>
              </a:xfrm>
              <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
            </p:spPr>
            <p:txBody>
              <a:bodyPr wrap="square"/>
              <a:lstStyle/>
              <a:p>
                <a:r>
                  <a:rPr lang="en-US" sz="2800" b="1">
                    <a:solidFill><a:srgbClr val="$titleColor"/></a:solidFill>
                  </a:rPr>
                  <a:t>$title</a:t>
                </a:r>
              </a:p>
              <a:p>
                <a:r>
                  <a:rPr lang="ar-SA" sz="1800">
                    <a:solidFill><a:srgbClr val="$accentHex"/></a:solidFill>
                  </a:rPr>
                  <a:t>$subtitle</a:t>
                </a:r>
              </a:p>
            </p:txBody>
          </p:sp>
          <p:sp>
            <p:nvSpPr>
              <p:cNvPr id="4" name="BodyBox"/>
              <p:cNvSpPr txBox="1"/>
              <p:nvPr/>
            </p:nvSpPr>
            <p:spPr>
              <a:xfrm>
                <a:off x="548640" y="2468880"/>
                <a:ext cx="8046720" cy="2194560"/>
              </a:xfrm>
              <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
            </p:spPr>
            <p:txBody>
              <a:bodyPr wrap="square"/>
              <a:lstStyle/>
              <a:p>
                <a:r>
                  <a:rPr lang="en-US" sz="1800">
                    <a:solidFill><a:srgbClr val="$bodyColor"/></a:solidFill>
                  </a:rPr>
                  <a:t>$bullet1</a:t>
                </a:r>
              </a:p>
              <a:p>
                <a:r>
                  <a:rPr lang="ar-SA" sz="1800">
                    <a:solidFill><a:srgbClr val="$bodyColor"/></a:solidFill>
                  </a:rPr>
                  <a:t>$bullet2</a:t>
                </a:r>
              </a:p>
              <a:p>
                <a:r>
                  <a:rPr lang="en-US" sz="1600">
                    <a:solidFill><a:srgbClr val="$bodyColor"/></a:solidFill>
                  </a:rPr>
                  <a:t>$bullet3</a:t>
                </a:r>
              </a:p>
            </p:txBody>
          </p:sp>
        </p:spTree>
      </p:cSld>
    </p:sld>
  """

  private fun generateRealOpenXmlXlsx(target: File) {
    val sharedStrings = listOf(
      "Department / القسم",
      "Q1 Budget",
      "Q2 Actual",
      "Variance",
      "Engineering / الهندسة",
      "Operations / العمليات",
      "Design / التصميم",
      "Total / الإجمالي",
      "Metric / المؤشر",
      "Target",
      "Achieved",
      "Status",
      "Sheets Rendered",
      "Passed",
      "Access Tables",
      "RTL Support"
    )
    val ssXml = buildString {
      append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
      append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${sharedStrings.size}\" uniqueCount=\"${sharedStrings.size}\">")
      sharedStrings.forEach { append("<si><t>${escapeXml(it)}</t></si>") }
      append("</sst>")
    }

    ZipOutputStream(FileOutputStream(target)).use { zip ->
      fun entry(name: String, xml: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(xml.trimIndent().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }

      entry(
        "[Content_Types].xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.sharedStrings+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
        """
      )

      entry(
        "_rels/.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
        """
      )

      entry(
        "xl/_rels/workbook.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
          <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
        """
      )

      entry(
        "xl/workbook.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Budget 2026" sheetId="1" r:id="rId1"/>
            <sheet name="KPI Summary" sheetId="2" r:id="rId2"/>
          </sheets>
        </workbook>
        """
      )

      entry(
        "xl/styles.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
          <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
          <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
        </styleSheet>
        """
      )

      entry("xl/sharedStrings.xml", ssXml)

      entry(
        "xl/worksheets/sheet1.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>0</v></c>
              <c r="B1" t="s"><v>1</v></c>
              <c r="C1" t="s"><v>2</v></c>
              <c r="D1" t="s"><v>3</v></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>4</v></c>
              <c r="B2"><v>45000</v></c>
              <c r="C2"><v>43200</v></c>
              <c r="D2"><f>B2-C2</f><v>1800</v></c>
            </row>
            <row r="3">
              <c r="A3" t="s"><v>5</v></c>
              <c r="B3"><v>32000</v></c>
              <c r="C3"><v>30500</v></c>
              <c r="D3"><f>B3-C3</f><v>1500</v></c>
            </row>
            <row r="4">
              <c r="A4" t="s"><v>6</v></c>
              <c r="B4"><v>18000</v></c>
              <c r="C4"><v>17800</v></c>
              <c r="D4"><f>B4-C4</f><v>200</v></c>
            </row>
            <row r="5">
              <c r="A5" t="s"><v>7</v></c>
              <c r="B5"><f>SUM(B2:B4)</f><v>95000</v></c>
              <c r="C5"><f>SUM(C2:C4)</f><v>91500</v></c>
              <c r="D5"><f>SUM(D2:D4)</f><v>3500</v></c>
            </row>
          </sheetData>
        </worksheet>
        """
      )

      entry(
        "xl/worksheets/sheet2.xml",
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>8</v></c>
              <c r="B1" t="s"><v>9</v></c>
              <c r="C1" t="s"><v>10</v></c>
              <c r="D1" t="s"><v>11</v></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>12</v></c>
              <c r="B2"><v>100</v></c>
              <c r="C2"><v>100</v></c>
              <c r="D2" t="s"><v>13</v></c>
            </row>
            <row r="3">
              <c r="A3" t="s"><v>14</v></c>
              <c r="B3"><v>50</v></c>
              <c r="C3"><v>50</v></c>
              <c r="D3" t="s"><v>13</v></c>
            </row>
            <row r="4">
              <c r="A4" t="s"><v>15</v></c>
              <c r="B4"><v>100</v></c>
              <c r="C4"><v>100</v></c>
              <c r="D4" t="s"><v>13</v></c>
            </row>
          </sheetData>
        </worksheet>
        """
      )
    }
  }

  private fun generateRealAccessAccdb(target: File) {
    if (target.exists()) target.delete()
    com.healthmarketscience.jackcess.DatabaseBuilder(target)
      .setFileFormat(com.healthmarketscience.jackcess.Database.FileFormat.V2016)
      .create()
      .use { db ->
        val deptTable = com.healthmarketscience.jackcess.TableBuilder("Departments")
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "DeptID",
              com.healthmarketscience.jackcess.DataType.LONG
            ).setAutoNumber(true)
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "DepartmentName",
              com.healthmarketscience.jackcess.DataType.TEXT
            )
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "Location",
              com.healthmarketscience.jackcess.DataType.TEXT
            )
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "AnnualBudget",
              com.healthmarketscience.jackcess.DataType.DOUBLE
            )
          )
          .toTable(db)

        deptTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "تقنية المعلومات / IT", "Baghdad HQ", 125000.0)
        deptTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "المالية والمحاسبة / Finance", "Floor 2", 84500.0)
        deptTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "إدارة المشاريع / PMO", "Floor 3", 96000.0)

        val empTable = com.healthmarketscience.jackcess.TableBuilder("Employees")
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "EmpID",
              com.healthmarketscience.jackcess.DataType.LONG
            ).setAutoNumber(true)
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "FullName",
              com.healthmarketscience.jackcess.DataType.TEXT
            )
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "Role",
              com.healthmarketscience.jackcess.DataType.TEXT
            )
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "DeptID",
              com.healthmarketscience.jackcess.DataType.LONG
            )
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "Salary",
              com.healthmarketscience.jackcess.DataType.DOUBLE
            )
          )
          .addColumn(
            com.healthmarketscience.jackcess.ColumnBuilder(
              "Active",
              com.healthmarketscience.jackcess.DataType.BOOLEAN
            )
          )
          .toTable(db)

        empTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "أحمد كريم حسن", "Senior Systems Engineer", 1, 2400.0, true)
        empTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "زينب علي محمد", "Financial Controller", 2, 2150.0, true)
        empTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "Omar Tariq", "Project Lead", 3, 2600.0, true)
        empTable.addRow(com.healthmarketscience.jackcess.Column.AUTO_NUMBER, "نور الهدى سالم", "Database Analyst", 1, 1950.0, false)
      }
  }

  private fun escapeXml(input: String): String =
    input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
