package com.example

import android.content.Context
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.documentviewer.data.AppLanguage
import com.example.documentviewer.ui.SortOrder
import com.example.documentviewer.engine.PdfAnnotationSaver
import com.example.documentviewer.model.DocumentFormat
import com.example.documentviewer.model.SupportLevel
import com.example.documentviewer.resolver.DocumentTypeResolver
import com.example.documentviewer.resolver.Ole2LegacyParser
import com.example.documentviewer.security.DocumentSecurityManager
import com.example.documentviewer.ui.HomeScreen
import com.example.documentviewer.ui.HomeTab
import com.example.documentviewer.ui.HomeUiState
import com.example.documentviewer.ui.LocalizedAppContainer
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class DocumentViewerRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val resolver = DocumentTypeResolver()

  @Test
  fun `generates and resolves real multi-page PDF, DOCX, PPTX, XLSX, and ACCDB sample documents`() {
    val samples = kotlinx.coroutines.runBlocking {
      com.example.documentviewer.sample.RealSampleDocumentsGenerator.ensureSampleDocuments(context)
    }
    assertEquals(5, samples.size)

    val pdfSample = samples.first { it.displayName.endsWith(".pdf") }
    val resolvedPdf = resolver.resolveFromFile(pdfSample.file, pdfSample.displayName, pdfSample.mimeType)
    assertEquals(DocumentFormat.PDF, resolvedPdf.format)
    assertEquals(SupportLevel.FULL, resolvedPdf.supportLevel)
    assertTrue(resolvedPdf.warnings.isEmpty())

    val docxSample = samples.first { it.displayName.endsWith(".docx") }
    val resolvedDocx = resolver.resolveFromFile(docxSample.file, docxSample.displayName, docxSample.mimeType)
    assertEquals(DocumentFormat.DOCX, resolvedDocx.format)
    assertEquals(SupportLevel.FULL, resolvedDocx.supportLevel)

    val pptxSample = samples.first { it.displayName.endsWith(".pptx") }
    val resolvedPptx = resolver.resolveFromFile(pptxSample.file, pptxSample.displayName, pptxSample.mimeType)
    assertEquals(DocumentFormat.PPTX, resolvedPptx.format)
    assertEquals(SupportLevel.FULL, resolvedPptx.supportLevel)
    assertEquals(3, resolvedPptx.estimatedPageCount)

    val xlsxSample = samples.first { it.displayName.endsWith(".xlsx") }
    val resolvedXlsx = resolver.resolveFromFile(xlsxSample.file, xlsxSample.displayName, xlsxSample.mimeType)
    assertEquals(DocumentFormat.XLSX, resolvedXlsx.format)
    assertEquals(SupportLevel.FULL, resolvedXlsx.supportLevel)
    assertEquals(2, resolvedXlsx.estimatedPageCount)

    val accdbSample = samples.first { it.displayName.endsWith(".accdb") }
    val resolvedAccdb = resolver.resolveFromFile(accdbSample.file, accdbSample.displayName, accdbSample.mimeType)
    assertEquals(DocumentFormat.ACCDB, resolvedAccdb.format)
    assertEquals(SupportLevel.FULL, resolvedAccdb.supportLevel)

    val extracted = com.example.documentviewer.engine.AccessDatabaseExtractor.extractToJson(
      context = context,
      cachedFile = accdbSample.file,
      uri = null,
      displayName = accdbSample.displayName
    )
    assertTrue(extracted is com.example.documentviewer.engine.AccessExtractionResult.Success)
    val ok = extracted as com.example.documentviewer.engine.AccessExtractionResult.Success
    assertEquals(2, ok.tableCount)
    val jsonStr = String(ok.jsonBytes, Charsets.UTF_8)
    assertTrue(jsonStr.contains("Employees"))
    assertTrue(jsonStr.contains("Departments"))
    assertTrue(jsonStr.contains("أحمد كريم حسن"))
  }

  @Test
  fun `PdfAnnotationSaver parses stroke JSON and bakes annotations into real PDF file`() {
    val json = """
      {
        "1": [
          {
            "tool": "pen",
            "color": "#E11D48",
            "size": 3.5,
            "points": [[0.15, 0.20], [0.45, 0.35], [0.75, 0.25]]
          },
          {
            "tool": "highlighter",
            "color": "#FACC15",
            "size": 4.0,
            "points": [[0.10, 0.50], [0.80, 0.50]]
          }
        ]
      }
    """.trimIndent()

    val parsed = PdfAnnotationSaver.parseAnnotationsJson(json)
    assertEquals(1, parsed.size)
    assertEquals(2, parsed[1]?.size)
    assertEquals("pen", parsed[1]?.get(0)?.tool)
    assertEquals("highlighter", parsed[1]?.get(1)?.tool)

    val samples = kotlinx.coroutines.runBlocking {
      com.example.documentviewer.sample.RealSampleDocumentsGenerator.ensureSampleDocuments(context)
    }
    val pdfSample = samples.first { it.displayName.endsWith(".pdf") }
    val copyPdf = File(context.cacheDir, "annotated_test.pdf")
    pdfSample.file.copyTo(copyPdf, overwrite = true)

    val bakeRes = kotlinx.coroutines.runBlocking {
      PdfAnnotationSaver.bakeAnnotationsIntoPdf(
        context = context,
        targetPdfFile = copyPdf,
        annotationsByPage = parsed
      )
    }
    assertTrue(bakeRes.isSuccess)
    assertTrue(copyPdf.exists() && copyPdf.length() > 100)
    copyPdf.delete()
  }

  @Test
  fun `resolves real DOCX archive and detects comments warning`() {
    val docxFile = File(context.cacheDir, "report_rtl.docx")
    ZipOutputStream(FileOutputStream(docxFile)).use { zip ->
      zip.putNextEntry(ZipEntry("[Content_Types].xml"))
      zip.write("<Types></Types>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("word/document.xml"))
      zip.write("<w:document><w:body><w:p><w:r><w:t>تقرير عربي</w:t></w:r></w:p></w:body></w:document>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("word/comments.xml"))
      zip.write("<w:comments/>".toByteArray())
      zip.closeEntry()
    }

    val resolved = resolver.resolveFromFile(docxFile, "report_rtl.docx", null)
    assertEquals(DocumentFormat.DOCX, resolved.format)
    assertEquals(SupportLevel.FULL, resolved.supportLevel)
    assertTrue(resolved.warnings.any { it.code == "COMMENTS" })
    docxFile.delete()
  }

  @Test
  fun `resolves PPTX and detects SmartArt and Chart unsupported elements`() {
    val pptxFile = File(context.cacheDir, "deck.pptx")
    ZipOutputStream(FileOutputStream(pptxFile)).use { zip ->
      zip.putNextEntry(ZipEntry("[Content_Types].xml"))
      zip.write("<Types></Types>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/presentation.xml"))
      zip.write("<p:presentation/>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
      zip.write("<p:sld/>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/slides/slide2.xml"))
      zip.write("<p:sld/>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/diagrams/layout1.xml"))
      zip.write("<dgm/>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/charts/chart1.xml"))
      zip.write("<c:chartSpace/>".toByteArray())
      zip.closeEntry()
    }

    val resolved = resolver.resolveFromFile(pptxFile, "deck.pptx", null)
    assertEquals(DocumentFormat.PPTX, resolved.format)
    assertEquals(SupportLevel.FULL, resolved.supportLevel)
    assertEquals(2, resolved.estimatedPageCount)
    assertTrue(resolved.warnings.any { it.code == "SMARTART" })
    assertTrue(resolved.warnings.any { it.code == "CHARTS" })
    pptxFile.delete()
  }

  @Test
  fun `resolves PPSX slideshow as PARTIAL support`() {
    val ppsxFile = File(context.cacheDir, "show.ppsx")
    ZipOutputStream(FileOutputStream(ppsxFile)).use { zip ->
      zip.putNextEntry(ZipEntry("[Content_Types].xml"))
      zip.write("<Types><Override ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml\"/></Types>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/presentation.xml"))
      zip.write("<p:presentation/>".toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
      zip.write("<p:sld/>".toByteArray())
      zip.closeEntry()
    }

    val resolved = resolver.resolveFromFile(ppsxFile, "show.ppsx", null)
    assertEquals(DocumentFormat.PPSX, resolved.format)
    assertEquals(SupportLevel.PARTIAL, resolved.supportLevel)
    assertTrue(resolved.warnings.any { it.code == "PPSX_TIMINGS" })
    ppsxFile.delete()
  }

  @Test
  fun `Ole2LegacyParser parses CFBF WordDocument and Workbook streams`() {
    val oleBytes = buildMinimalOle2(
      streamName = "WordDocument",
      payload = "مرحبا بكم في المستند القديم\rHello Legacy Word".toByteArray(Charsets.UTF_16LE)
    )
    val result = Ole2LegacyParser.inspect(ByteArrayInputStream(oleBytes))
    assertEquals(Ole2LegacyParser.LegacyKind.WORD_DOC, result.kind)
    assertTrue(result.extractedParagraphs.any { it.contains("مرحبا") || it.contains("Hello Legacy Word") })

    val xlsBytes = buildMinimalOle2(
      streamName = "Workbook",
      payload = ByteArray(16)
    )
    val xlsResult = Ole2LegacyParser.inspect(ByteArrayInputStream(xlsBytes))
    assertEquals(Ole2LegacyParser.LegacyKind.EXCEL_XLS, xlsResult.kind)
  }

  @Test
  fun `resolves CSV spreadsheets as EXCEL category with FULL support`() {
    val csvFile = File(context.cacheDir, "budget_ar.csv")
    csvFile.writeText("القسم,الميزانية\nالهندسة,45000\n")
    val resolved = resolver.resolveFromFile(csvFile, "budget_ar.csv", "text/csv")
    assertEquals(DocumentFormat.XLS, resolved.format)
    assertEquals(SupportLevel.FULL, resolved.supportLevel)
    csvFile.delete()
  }

  @Test
  fun `DocumentSecurityManager blocks external origins and allows active ephemeral doc token only`() {
    val sec = DocumentSecurityManager(context)
    val payload = "%PDF-1.4\n%%EOF".toByteArray()
    val session = sec.createSession(
      fileName = "secret.pdf",
      mimeType = "application/pdf",
      sourceUri = null,
      cachedFile = null,
      inlinePayloadBytes = payload
    )
    val docUrl = sec.documentUrlFor(session)
    val loader = sec.buildAssetLoader()
    val client = sec.buildStrictWebViewClient(loader)

    val dummyWebView = android.webkit.WebView(context)
    val allowedRes = client.shouldInterceptRequest(dummyWebView, fakeRequest(docUrl))
    assertNotNull(allowedRes)
    assertEquals(200, allowedRes!!.statusCode)

    val evilRes = client.shouldInterceptRequest(dummyWebView, fakeRequest("https://evil.example.com/exfil"))
    assertNotNull(evilRes)
    assertEquals(403, evilRes!!.statusCode)

    val wrongTokenRes = client.shouldInterceptRequest(
      dummyWebView,
      fakeRequest("${DocumentSecurityManager.SANDBOX_ORIGIN}/doc/wrong-token/secret.pdf")
    )
    assertNotNull(wrongTokenRes)
    assertEquals(403, wrongTokenRes!!.statusCode)

    val blobRes = client.shouldInterceptRequest(
      dummyWebView,
      fakeRequest("blob:${DocumentSecurityManager.SANDBOX_ORIGIN}/1234-5678")
    )
    assertEquals(null, blobRes)
  }

  @Test
  fun `DocumentSecurityManager handles onRenderProcessGone safely and serves binary assets without UTF-8 encoding`() {
    val sec = DocumentSecurityManager(context)
    val loader = sec.buildAssetLoader()
    var callbackCrashFlag: Boolean? = null
    val client = sec.buildStrictWebViewClient(
      assetLoader = loader,
      onRenderProcessGone = { didCrash -> callbackCrashFlag = didCrash }
    )

    val container = FrameLayout(context)
    val webView = android.webkit.WebView(context)
    container.addView(webView)
    assertEquals(1, container.childCount)

    val detail = object : RenderProcessGoneDetail() {
      override fun didCrash(): Boolean = true
      override fun rendererPriorityAtExit(): Int = android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT
    }

    val handled = client.onRenderProcessGone(webView, detail)
    assertTrue(handled)
    assertEquals(true, callbackCrashFlag)
    assertEquals(0, container.childCount)

    // Calling safelyDestroyWebView again must be an idempotent no-op
    sec.safelyDestroyWebView(webView)

    // Verify asset MIME & encoding for text vs binary resources
    val htmlRes = client.shouldInterceptRequest(
      android.webkit.WebView(context),
      fakeRequest("${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/pdf/pdf_viewer.html")
    )
    assertNotNull(htmlRes)
    assertEquals("text/html", htmlRes!!.mimeType)
    assertEquals("UTF-8", htmlRes.encoding)

    val binaryRes = client.shouldInterceptRequest(
      android.webkit.WebView(context),
      fakeRequest("${DocumentSecurityManager.SANDBOX_ORIGIN}/assets/viewer/pdf/cmaps/Adobe-GB1-0.bcmap")
    )
    assertNotNull(binaryRes)
    assertEquals("application/octet-stream", binaryRes!!.mimeType)
    assertEquals(null, binaryRes.encoding)
  }

  @Test
  fun `SystemFoldersManager creates folder and automatically discovers newly added document files`() {
    val manager = com.example.documentviewer.data.SystemFoldersManager(context, resolver)
    val createdRes = kotlinx.coroutines.runBlocking {
      manager.createFolder("ابحاث_ومستندات_جديدة")
    }
    assertTrue(createdRes.isSuccess)
    val folder = createdRes.getOrThrow()
    assertEquals("ابحاث_ومستندات_جديدة", folder.folderName)

    // Add a new PDF file directly into the created folder's directory on the filesystem
    val folderDir = File(folder.localDirectoryPath!!)
    val samples = kotlinx.coroutines.runBlocking {
      com.example.documentviewer.sample.RealSampleDocumentsGenerator.ensureSampleDocuments(context)
    }
    val pdfSample = samples.first { it.displayName.endsWith(".pdf") }
    val newPdfInFolder = File(folderDir, "Auto_Discovered_Report.pdf")
    pdfSample.file.copyTo(newPdfInFolder, overwrite = true)

    // Scan all folders and verify the newly added file appears automatically inside the folder
    val scanResult = kotlinx.coroutines.runBlocking {
      manager.scanAllSystemFoldersAndDocuments()
    }
    val discoveredFolder = scanResult.folders.firstOrNull { it.folderName == "ابحاث_ومستندات_جديدة" }
    assertNotNull(discoveredFolder)
    assertTrue(discoveredFolder!!.files.any { it.displayName == "Auto_Discovered_Report.pdf" })
    assertTrue(scanResult.allFiles.any { it.displayName == "Auto_Discovered_Report.pdf" })
  }

  @Test
  fun `resolves HTML and Code source files with FULL support`() {
    val htmlFile = File(context.cacheDir, "page.html")
    htmlFile.writeText("<!DOCTYPE html><html><body><h1>DocuView</h1></body></html>")
    val resolvedHtml = resolver.resolveFromFile(htmlFile, "page.html", "text/html")
    assertEquals(DocumentFormat.HTML, resolvedHtml.format)
    assertEquals(SupportLevel.FULL, resolvedHtml.supportLevel)
    htmlFile.delete()

    val ktFile = File(context.cacheDir, "Sample.kt")
    ktFile.writeText("package com.example\nclass Sample { fun run() = Unit }\n")
    val resolvedCode = resolver.resolveFromFile(ktFile, "Sample.kt", "text/plain")
    assertEquals(DocumentFormat.CODE, resolvedCode.format)
    assertEquals(SupportLevel.FULL, resolvedCode.supportLevel)
    ktFile.delete()
  }

  @Test
  fun `HomeScreen renders compact open button, newest-oldest filter chips, Folders tab, and Settings tab`() {
    var currentTab = HomeTab.RECENT
    var currentSort = com.example.documentviewer.ui.SortOrder.RECENT_OPENED
    composeTestRule.setContent {
      LocalizedAppContainer(appLanguage = AppLanguage.ARABIC) { isArabic ->
        MyApplicationTheme(darkTheme = false) {
          HomeScreen(
            uiState = HomeUiState(selectedTab = currentTab),
            isArabic = isArabic,
            onPickDocument = {},
            onOpenDocument = {},
            onToggleFavorite = {},
            onRemoveRecent = {},
            onSelectTab = { currentTab = it },
            onSelectCategory = {},
            onSearchQueryChange = {},
            onSelectSortOrder = { currentSort = it },
            onThemeModeChange = {},
            onLanguageChange = {},
            onContinuousPdfChange = {},
            onKeepScreenOnChange = {},
            onClearHistory = {},
            onErrorConsumed = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("open_document_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sort_newest_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sort_oldest_chip").performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.documentviewer.ui.SortOrder.OLDEST_OPENED, currentSort)

    composeTestRule.onNodeWithTag("filter_chips_row").performScrollToNode(hasTestTag("filter_excel"))
    composeTestRule.onNodeWithTag("filter_excel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filter_chips_row").performScrollToNode(hasTestTag("filter_access"))
    composeTestRule.onNodeWithTag("filter_access").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filter_chips_row").performScrollToNode(hasTestTag("filter_html"))
    composeTestRule.onNodeWithTag("filter_html").assertIsDisplayed()
    composeTestRule.onNodeWithTag("filter_chips_row").performScrollToNode(hasTestTag("filter_code"))
    composeTestRule.onNodeWithTag("filter_code").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("100% Offline", substring = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("بدون إنترنت 100%", substring = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("load_sample_docs_chip").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("empty_load_samples_button").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("Sample Files", substring = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("ملفات تجريبية", substring = true).fetchSemanticsNodes().size)

    composeTestRule.onNodeWithTag("tab_recent").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tab_folders").performClick()
    composeTestRule.waitForIdle()
    assertEquals(HomeTab.FOLDERS, currentTab)

    composeTestRule.onNodeWithTag("tab_favorites").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tab_formats").performClick()
    composeTestRule.waitForIdle()
    assertEquals(HomeTab.ENGINES, currentTab)
  }

  @Test
  fun `SettingsTabContent removes Sample Files button and displays centered Clear History button`() {
    var clearClicked = false
    composeTestRule.setContent {
      LocalizedAppContainer(appLanguage = AppLanguage.ARABIC) { isArabic ->
        MyApplicationTheme(darkTheme = false) {
          HomeScreen(
            uiState = HomeUiState(selectedTab = HomeTab.ENGINES),
            isArabic = isArabic,
            onPickDocument = {},
            onOpenDocument = {},
            onToggleFavorite = {},
            onRemoveRecent = {},
            onSelectTab = {},
            onSelectCategory = {},
            onSearchQueryChange = {},
            onThemeModeChange = {},
            onLanguageChange = {},
            onContinuousPdfChange = {},
            onKeepScreenOnChange = {},
            onClearHistory = { clearClicked = true },
            onErrorConsumed = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    assertEquals(0, composeTestRule.onAllNodesWithTag("settings_load_samples_button").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("Sample Files", substring = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("ملفات تجريبية", substring = true).fetchSemanticsNodes().size)
    composeTestRule.onNodeWithTag("clear_history_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertTrue(clearClicked)
  }

  @Test
  fun `TextCopyAndExtractSheet handles extracting, empty-text-layer, and multi-page extraction cleanly`() {
    var extractAllCalled = false
    var copiedText = ""
    composeTestRule.setContent {
      LocalizedAppContainer(appLanguage = AppLanguage.ARABIC) {
        MyApplicationTheme(darkTheme = true) {
          com.example.documentviewer.ui.TextCopyAndExtractSheet(
            extractedText = "نص عربي قابل للتحديد والنسخ",
            currentPage = 10,
            isExtractingText = false,
            hasNoTextLayer = false,
            totalPages = 21,
            onExtractAllPages = { extractAllCalled = true },
            onCopyText = { copiedText = it },
            onDismiss = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("extract_text_field").assertIsDisplayed()
    composeTestRule.onNodeWithTag("extract_all_pages_button").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    assertTrue(extractAllCalled)
    composeTestRule.onNodeWithTag("copy_all_page_button").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    assertEquals("نص عربي قابل للتحديد والنسخ", copiedText)
  }

  @Test
  fun `TextCopyAndExtractSheet shows warning banner when PDF page has no text layer`() {
    composeTestRule.setContent {
      LocalizedAppContainer(appLanguage = AppLanguage.ARABIC) {
        MyApplicationTheme(darkTheme = true) {
          com.example.documentviewer.ui.TextCopyAndExtractSheet(
            extractedText = "",
            currentPage = 3,
            isExtractingText = false,
            hasNoTextLayer = true,
            totalPages = 5,
            onExtractAllPages = {},
            onCopyText = {},
            onDismiss = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("extract_no_text_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("copy_all_page_button").assertIsNotEnabled()
  }

  @Test
  fun `SystemFoldersManager persists cached folder snapshot and retains pinned empty folders`() {
    val manager = com.example.documentviewer.data.SystemFoldersManager(context, resolver)
    val initialScan = kotlinx.coroutines.runBlocking {
      manager.createFolder("data")
      manager.scanAllSystemFoldersAndDocuments()
    }
    assertTrue(initialScan.folders.any { it.folderName == "data" })

    manager.saveCachedScanSnapshot(initialScan)
    val loaded = manager.loadCachedScanSnapshot()
    assertNotNull(loaded)
    assertEquals(initialScan.folders.size, loaded!!.folders.size)
    assertTrue(loaded.folders.any { it.folderName == "data" })
  }

  @Test
  fun `word viewer enables review comments and uses compact copy pill styles across viewers`() {
    val wordJs = context.assets.open("viewer/word/word_host.js").bufferedReader().use { it.readText() }
    assertTrue(wordJs.contains("renderComments: true"))
    assertTrue(wordJs.contains("word/comments.xml"))
    assertTrue(!wordJs.contains("نسخ النص المحدد • Copy"))

    val pdfJs = context.assets.open("viewer/pdf/pdf_host.js").bufferedReader().use { it.readText() }
    assertTrue(!pdfJs.contains("نسخ النص المحدد • Copy"))

    val pdfCss = context.assets.open("viewer/pdf/pdf_host.css").bufferedReader().use { it.readText() }
    assertTrue(pdfCss.contains("white-space: nowrap;"))
    assertTrue(pdfCss.contains("font-size: 11px;"))
  }

  @Test
  fun `stale WhatsApp content URI is rejected safely without SecurityException and createLaunchIntent avoids FLAG_GRANT_READ_URI_PERMISSION`() {
    val staleDescriptor = com.example.documentviewer.model.DocumentDescriptor(
      documentKey = "stale-wa-key",
      uriString = "content://com.whatsapp.provider.media/item/c7358f90-1817-4ca8-9cb3-4677d270c6d2",
      displayName = "contract.pdf",
      mimeType = "application/pdf",
      format = DocumentFormat.PDF,
      supportLevel = SupportLevel.FULL,
      sizeBytes = 1024L,
      cachedFilePath = null
    )

    val launchIntent = com.example.documentviewer.DocumentViewerActivity.createLaunchIntent(context, staleDescriptor)
    assertEquals(null, launchIntent.data)
    assertEquals(0, launchIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    assertEquals(
      staleDescriptor.uriString,
      launchIntent.getStringExtra(com.example.documentviewer.DocumentViewerActivity.EXTRA_SOURCE_URI)
    )

    val openManager = com.example.documentviewer.open.DocumentOpenManager(context)
    assertEquals(false, openManager.isDescriptorReadable(staleDescriptor))

    val repo = com.example.documentviewer.data.DocumentRepository(context)
    val prepResult = kotlinx.coroutines.runBlocking {
      repo.prepareDescriptorForOpen(staleDescriptor)
    }
    assertTrue(prepResult.isFailure)
    assertTrue(prepResult.exceptionOrNull() is com.example.documentviewer.data.FileUnavailableException)
  }

  @Test
  fun `prepareDocumentFromUri copies file into filesDir imported_docs and generates FileProvider share URI`() {
    val samples = kotlinx.coroutines.runBlocking {
      com.example.documentviewer.sample.RealSampleDocumentsGenerator.ensureSampleDocuments(context)
    }
    val pdfSample = samples.first { it.displayName.endsWith(".pdf") }
    val incomingUri = Uri.fromFile(pdfSample.file)

    val repo = com.example.documentviewer.data.DocumentRepository(context)
    val prepared = kotlinx.coroutines.runBlocking {
      repo.prepareDocumentFromUri(incomingUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.getOrThrow()

    assertNotNull(prepared.cachedFilePath)
    val cachedFile = File(prepared.cachedFilePath!!)
    assertTrue(cachedFile.exists() && cachedFile.length() > 0L)
    assertTrue(prepared.uriString.startsWith("file://"))
    assertTrue(cachedFile.absolutePath.contains("imported_docs"))

    val shareUri = repo.openManager.resolveShareableContentUri(context, prepared)
    assertNotNull(shareUri)
    assertEquals("content", shareUri!!.scheme)
    assertTrue(shareUri.authority!!.endsWith(".fileprovider"))
  }

  @Test
  fun `hardenWebView and all viewer CSS and JS enable native multi-touch pinch-to-zoom`() {
    val sec = DocumentSecurityManager(context)
    val webView = android.webkit.WebView(context)
    sec.hardenWebView(webView)
    assertTrue(webView.settings.supportZoom())
    assertTrue(webView.settings.builtInZoomControls)
    assertEquals(false, webView.settings.displayZoomControls)
    assertTrue(webView.settings.useWideViewPort)

    val viewerCssPaths = listOf(
      "viewer/pdf/pdf_host.css",
      "viewer/word/word_host.css",
      "viewer/pptx/pptx_host.css",
      "viewer/excel/excel_host.css",
      "viewer/access/access_host.css",
      "viewer/code/code_host.css"
    )
    viewerCssPaths.forEach { path ->
      val css = context.assets.open(path).bufferedReader().use { it.readText() }
      assertTrue("Expected pinch-zoom in $path", css.contains("pinch-zoom"))
      assertTrue("Unexpected bare pan-x pan-y in $path", !css.contains("touch-action: pan-x pan-y;"))
    }

    val pdfJs = context.assets.open("viewer/pdf/pdf_host.js").bufferedReader().use { it.readText() }
    assertTrue(!pdfJs.contains("pinchStartDist"))
    assertTrue(pdfJs.contains("visualViewport"))
  }

  @Test
  fun `excel and access viewers auto-fit columns horizontally without breaking words character-by-character`() {
    val excelCss = context.assets.open("viewer/excel/excel_host.css").bufferedReader().use { it.readText() }
    assertTrue(excelCss.contains("table-layout: fixed;"))
    assertTrue(excelCss.contains("width: max-content;"))
    assertTrue(excelCss.contains("word-break: normal;"))
    assertTrue(!excelCss.contains("overflow-wrap: anywhere;"))
    assertTrue(!excelCss.contains("word-break: break-word;"))

    val excelJs = context.assets.open("viewer/excel/excel_host.js").bufferedReader().use { it.readText() }
    assertTrue(excelJs.contains("computeAutoFitColumnWidths"))
    assertTrue(excelJs.contains("colgroup"))
    assertTrue(excelJs.contains("measureCellTextWidthPx"))

    val accessCss = context.assets.open("viewer/access/access_host.css").bufferedReader().use { it.readText() }
    assertTrue(accessCss.contains("width: max-content;"))
    assertTrue(accessCss.contains("word-break: normal;"))
    assertTrue(!accessCss.contains("overflow-wrap: anywhere;"))
  }

  private fun fakeRequest(url: String): WebResourceRequest = object : WebResourceRequest {
    override fun getUrl(): Uri = Uri.parse(url)
    override fun isForMainFrame(): Boolean = false
    override fun isRedirect(): Boolean = false
    override fun hasGesture(): Boolean = false
    override fun getMethod(): String = "GET"
    override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
  }

  private fun buildMinimalOle2(streamName: String, payload: ByteArray): ByteArray {
    // 512-byte header + Sector 0 (FAT) + Sector 1 (Dir) + Sector 2 (Stream data)
    val sectorSize = 512
    val total = ByteArray(sectorSize * 4)
    val hdr = ByteBuffer.wrap(total, 0, sectorSize).slice().order(ByteOrder.LITTLE_ENDIAN)
    hdr.putLong(0, 0xE11AB1A1E011CFD0UL.toLong())
    hdr.putShort(30, 9.toShort()) // 512-byte sectors
    hdr.putInt(44, 1) // 1 FAT sector
    hdr.putInt(48, 1) // First dir sector = 1
    hdr.putInt(76, 0) // DIFAT[0] = sector 0

    // Sector 0 = FAT
    val fat = ByteBuffer.wrap(total, sectorSize, sectorSize).slice().order(ByteOrder.LITTLE_ENDIAN)
    fat.putInt(0, -3) // FATSECT
    fat.putInt(4, -2) // Dir end
    fat.putInt(8, -2) // Stream end

    // Sector 1 = Directory entry 0 (Root) + entry 1 (streamName)
    val dirOffset = sectorSize * 2
    val entry1 = dirOffset + 128
    val nameBytes = (streamName + "\u0000").toByteArray(Charsets.UTF_16LE)
    System.arraycopy(nameBytes, 0, total, entry1, nameBytes.size)
    val eb = ByteBuffer.wrap(total, entry1, 128).slice().order(ByteOrder.LITTLE_ENDIAN)
    eb.putShort(64, nameBytes.size.toShort())
    total[entry1 + 66] = 2 // Stream type
    eb.putInt(116, 2) // Start sector = 2
    eb.putInt(120, minOf(payload.size, sectorSize))

    // Sector 2 = Stream payload
    System.arraycopy(payload, 0, total, sectorSize * 3, minOf(payload.size, sectorSize))
    return total
  }
}
