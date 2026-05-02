package com.conamobile.pdfkmp.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.samples.Samples
import com.conamobile.pdfkmp.viewer.PdfSearchBar
import com.conamobile.pdfkmp.viewer.PdfViewer
import com.conamobile.pdfkmp.viewer.PdfViewerTopBar
import com.conamobile.pdfkmp.viewer.rememberPdfSaveAction
import com.conamobile.pdfkmp.viewer.rememberPdfShareAction
import com.conamobile.pdfkmp.viewer.searchPdfText

/**
 * Hosts the Android demo for PdfKmp.
 *
 * The app shows a list of bundled sample documents; tapping one builds
 * the PDF in-process via PdfKmp and hands the resulting [PdfDocument]
 * straight to the [`:pdfkmp-viewer`][PdfViewer] composable. The viewer
 * itself goes through Android's system [android.graphics.pdf.PdfRenderer]
 * to rasterise pages for display only — the on-disk and shared PDF
 * stays vector and stays sharp at any zoom level.
 */
class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SampleApp()
            }
        }
    }
}

private data class SampleEntry(
    val title: String,
    val build: suspend (SampleAssets) -> PdfDocument,
)

/**
 * Resources that come from the sample app's APK assets and are passed into
 * the platform-agnostic [Samples] builders. Keeping the bytes here (rather
 * than inside `:pdfkmp`) avoids shipping demo media inside the library.
 */
private class SampleAssets(val sampleImagePng: ByteArray)

private val SAMPLES = listOf(
    SampleEntry("⭐ Brochure (README hero)") { Samples.brochure() },
    SampleEntry("🧩 Compose Resources (Res.drawable.*)") { ComposeResourcesDemo.build() },
    SampleEntry("Hello world") { Samples.helloWorld() },
    SampleEntry("Typography — text + decorations + alignment + rich") { Samples.typography() },
    SampleEntry("Row & Column with weights") { Samples.rowAndColumn() },
    SampleEntry("Column SpaceBetween") { Samples.columnSpaceBetween() },
    SampleEntry("Tables & lists") { Samples.tableShowcase() },
    SampleEntry("Vectors + circle/ellipse") { Samples.vectorShowcase() },
    SampleEntry("Vector — gradients + arcs + transforms") { Samples.vectorAdvanced() },
    SampleEntry("Custom designs + decorations (gradients, corners, borders)") {
        Samples.customDesigns(it.sampleImagePng)
    },
    SampleEntry("Page chrome — header / footer / page#  / watermark / links / i18n") {
        Samples.pageChrome()
    },
    SampleEntry("Long body — MoveToNextPage") { Samples.longBody() },
    SampleEntry("Long body — Slice") { Samples.slicedBody() },
    SampleEntry("Custom padding") { Samples.customPadding() },
    SampleEntry("Image (Fit + Crop)") { Samples.withImage(it.sampleImagePng) },
    SampleEntry("Tall image — sliced") { Samples.slicedImage(it.sampleImagePng) },
    SampleEntry("Showcase — every v1 feature in one PDF") { Samples.showcase() },
)

@Composable
private fun SampleApp() {
    var selected by remember { mutableStateOf<SampleEntry?>(null) }
    var document by remember(selected) { mutableStateOf<PdfDocument?>(null) }
    val context = LocalContext.current
    val shareAction = rememberPdfShareAction()
    val saveAction = rememberPdfSaveAction()

    LaunchedEffect(selected) {
        val current = selected ?: return@LaunchedEffect
        val assets = SampleAssets(
            sampleImagePng = context.assets.open("sample.png").use { it.readBytes() },
        )
        document = current.build(assets)
    }

    val entry = selected
    val built = document

    // Search state — owned by the host, fed to PdfSearchBar +
    // forwarded to PdfViewer's `searchHighlights` parameter.
    var searchOpen by remember(entry) { mutableStateOf(false) }
    var searchQuery by remember(entry) { mutableStateOf("") }
    var activeMatchIndex by remember(entry) { mutableStateOf(0) }

    val highlights = remember(built, searchQuery) {
        if (built == null || !searchOpen || searchQuery.isBlank()) emptyList()
        else searchPdfText(built.textRuns, searchQuery)
    }
    // Reset the active index when the result set changes so we don't
    // dangle past the new size.
    LaunchedEffect(highlights.size) {
        activeMatchIndex = if (highlights.isEmpty()) -1 else 0
    }

    Scaffold(
        topBar = {
            when {
                entry == null -> {
                    PdfViewerTopBar(
                        title = "PdfKmp samples",
                        showBack = false,
                        showShare = false,
                        showDownload = false,
                    )
                }
                searchOpen -> {
                    PdfSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        matchCount = highlights.size,
                        activeIndex = activeMatchIndex,
                        onPrevious = {
                            if (highlights.isNotEmpty()) {
                                activeMatchIndex =
                                    (activeMatchIndex - 1 + highlights.size) % highlights.size
                            }
                        },
                        onNext = {
                            if (highlights.isNotEmpty()) {
                                activeMatchIndex =
                                    (activeMatchIndex + 1) % highlights.size
                            }
                        },
                        onClose = {
                            searchOpen = false
                            searchQuery = ""
                            activeMatchIndex = -1
                        },
                    )
                }
                else -> {
                    val fileName = "${entry.title.toFileSlug()}.pdf"
                    val subtitle = built?.let { "PDF · ${formatSize(it.size)}" }
                    PdfViewerTopBar(
                        title = entry.title,
                        subtitle = subtitle,
                        backLabel = "Samples",
                        onBack = { selected = null },
                        onSearch = { searchOpen = true },
                        onShare = { built?.let { shareAction(it.toByteArray(), fileName) } },
                        onDownload = { built?.let { saveAction(it.toByteArray(), fileName) } },
                        showSearch = built != null && built.textRuns.isNotEmpty(),
                        showShare = built != null,
                        showDownload = built != null,
                    )
                }
            }
        },
    ) { padding ->
        if (entry == null) {
            SampleList(
                onPick = { selected = it },
                modifier = Modifier.padding(padding),
            )
        } else {
            SamplePreview(
                document = built,
                searchHighlights = highlights,
                activeSearchHighlightIndex = activeMatchIndex,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Friendly file-size hint for the topbar's meta line. */
private fun formatSize(bytes: Int): String = when {
    bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576f)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun SampleList(onPick: (SampleEntry) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SAMPLES) { entry ->
            Button(onClick = { onPick(entry) }, modifier = Modifier.fillMaxWidth()) {
                Text(entry.title)
            }
        }
    }
}

@Composable
private fun SamplePreview(
    document: PdfDocument?,
    searchHighlights: List<com.conamobile.pdfkmp.viewer.PdfSearchHighlight>,
    activeSearchHighlightIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (document == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Text("Building…") }
    } else {
        PdfViewer(
            document = document,
            modifier = modifier,
            showShareButton = false,
            searchHighlights = searchHighlights,
            activeSearchHighlightIndex = activeSearchHighlightIndex,
        )
    }
}

private fun String.toFileSlug(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
