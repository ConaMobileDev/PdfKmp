package com.conamobile.pdfkmp.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.samples.Samples
import com.conamobile.pdfkmp.viewer.KmpPdfViewer
import com.conamobile.pdfkmp.viewer.PdfViewerTopBar

/**
 * # PdfKmp Android sample
 *
 * Two screens, one purpose: showcase what `:pdfkmp` (the generator)
 * and `:pdfkmp-viewer` (the renderer + chrome) can do together when
 * dropped into a real Compose app.
 *
 * - **List** (this file → [SampleApp]) — eight categories of bundled
 *   sample documents. Tap one and the activity navigates to the
 *   detail.
 * - **Detail** — a *single* call into [`KmpPdfViewer.open`][KmpPdfViewer.open]
 *   which gives you the full topbar + search bar morph + share /
 *   save / hyperlink launcher + page indicator + gesture-driven
 *   zoom / pan / selection — no extra wiring required by the host.
 *
 * If you're a developer evaluating PdfKmp for your own product, the
 * relevant call site is **17 lines down** from where you scroll into
 * the [DetailScreen] composable. That's the entire integration.
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

// ─────────────────────────────────────────────────────────────────────
// Bundled sample catalogue — grouped so the list reads like a tour
// rather than a flat dump. Each entry has a short *description*
// alongside its title; the list surfaces both so a developer
// browsing the demo knows which feature each sample exercises.
// ─────────────────────────────────────────────────────────────────────

private data class SampleEntry(
    val id: String,
    val title: String,
    val description: String,
    val build: suspend (SampleAssets) -> PdfDocument,
)

private data class SampleCategory(
    val name: String,
    val entries: List<SampleEntry>,
)

/**
 * Resources passed into the platform-agnostic [Samples] builders —
 * the demo PNG that powers the image samples lives in `assets/` so
 * we don't ship demo media inside the library itself.
 */
private class SampleAssets(val sampleImagePng: ByteArray)

private val SAMPLE_CATEGORIES = listOf(
    SampleCategory(
        name = "Getting started",
        entries = listOf(
            SampleEntry(
                id = "brochure",
                title = "⭐ Brochure",
                description = "README hero — gradient cover, feature cards, table.",
                build = { Samples.brochure() },
            ),
            SampleEntry(
                id = "hello-world",
                title = "Hello world",
                description = "Smallest working pdf { } block — single line of text.",
                build = { Samples.helloWorld() },
            ),
        ),
    ),
    SampleCategory(
        name = "Typography & text",
        entries = listOf(
            SampleEntry(
                id = "typography",
                title = "Typography",
                description = "Decorations, alignment, rich-text spans, kerning.",
                build = { Samples.typography() },
            ),
            SampleEntry(
                id = "page-chrome",
                title = "Page chrome",
                description = "Header, footer, page#, watermark, links, i18n fonts.",
                build = { Samples.pageChrome() },
            ),
        ),
    ),
    SampleCategory(
        name = "Layout",
        entries = listOf(
            SampleEntry(
                id = "row-column",
                title = "Row & Column with weights",
                description = "Flex-style children with `weight()` that fight for space.",
                build = { Samples.rowAndColumn() },
            ),
            SampleEntry(
                id = "space-between",
                title = "Column SpaceBetween",
                description = "Pin children to the top + bottom of a Column slot.",
                build = { Samples.columnSpaceBetween() },
            ),
            SampleEntry(
                id = "padding",
                title = "Custom padding",
                description = "Different padding values per node — outer + inner.",
                build = { Samples.customPadding() },
            ),
        ),
    ),
    SampleCategory(
        name = "Tables & lists",
        entries = listOf(
            SampleEntry(
                id = "table",
                title = "Tables & lists",
                description = "Column widths, header rows, alternating zebra fills.",
                build = { Samples.tableShowcase() },
            ),
            SampleEntry(
                id = "compose-resources",
                title = "Compose Resources",
                description = "`Res.drawable.*` references resolved into PDF images.",
                build = { ComposeResourcesDemo.build() },
            ),
        ),
    ),
    SampleCategory(
        name = "Vector graphics",
        entries = listOf(
            SampleEntry(
                id = "vector",
                title = "Vector primitives",
                description = "Lines, circles, ellipses, paths — all rendered as vectors.",
                build = { Samples.vectorShowcase() },
            ),
            SampleEntry(
                id = "vector-advanced",
                title = "Gradients & transforms",
                description = "Linear / radial gradients, arcs, rotate / scale / translate.",
                build = { Samples.vectorAdvanced() },
            ),
            SampleEntry(
                id = "custom-designs",
                title = "Custom designs",
                description = "Decorations — gradients, corners, borders on container nodes.",
                build = { Samples.customDesigns(it.sampleImagePng) },
            ),
        ),
    ),
    SampleCategory(
        name = "Images",
        entries = listOf(
            SampleEntry(
                id = "image",
                title = "Image (Fit + Crop)",
                description = "ContentScale modes — Fit preserves ratio, Crop fills the box.",
                build = { Samples.withImage(it.sampleImagePng) },
            ),
            SampleEntry(
                id = "sliced-image",
                title = "Tall image — sliced",
                description = "An image taller than the page splits across multiple pages.",
                build = { Samples.slicedImage(it.sampleImagePng) },
            ),
        ),
    ),
    SampleCategory(
        name = "Long documents",
        entries = listOf(
            SampleEntry(
                id = "long-body",
                title = "Long body — MoveToNextPage",
                description = "Overflowing content jumps to the next page as a whole.",
                build = { Samples.longBody() },
            ),
            SampleEntry(
                id = "sliced-body",
                title = "Long body — Slice",
                description = "Overflowing content splits at line boundaries across pages.",
                build = { Samples.slicedBody() },
            ),
        ),
    ),
    SampleCategory(
        name = "Showcase",
        entries = listOf(
            SampleEntry(
                id = "showcase",
                title = "Every v1 feature",
                description = "Single PDF that exercises every public DSL primitive.",
                build = { Samples.showcase() },
            ),
        ),
    ),
)

// ─────────────────────────────────────────────────────────────────────
// Top-level navigator. Owns the selected entry, asynchronously builds
// the document for the detail screen, and routes between list /
// loading / KmpPdfViewer.open.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SampleApp() {
    var selected by remember { mutableStateOf<SampleEntry?>(null) }
    var document by remember(selected) { mutableStateOf<PdfDocument?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(selected) {
        val current = selected ?: return@LaunchedEffect
        val assets = SampleAssets(
            sampleImagePng = context.assets.open("sample.png").use { it.readBytes() },
        )
        document = current.build(assets)
    }

    val entry = selected
    val built = document

    when {
        entry == null -> ListScreen(onPick = { selected = it })
        built == null -> LoadingScreen(entry = entry, onBack = { selected = null })
        else -> DetailScreen(
            entry = entry,
            document = built,
            onBack = { selected = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// List screen — categorised catalogue of bundled samples. Pure UI, no
// PdfKmp surface area touched here.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ListScreen(onPick: (SampleEntry) -> Unit) {
    Scaffold(
        topBar = {
            PdfViewerTopBar(
                title = "PdfKmp samples",
                showBack = false,
                showShare = false,
                showDownload = false,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SAMPLE_CATEGORIES.forEachIndexed { index, category ->
                item(key = "header-${category.name}") {
                    CategoryHeader(name = category.name, isFirst = index == 0)
                }
                items(category.entries, key = { "${category.name}-${it.id}" }) { entry ->
                    SampleRow(entry = entry, onClick = { onPick(entry) })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String, isFirst: Boolean) {
    Text(
        text = name.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (isFirst) 16.dp else 28.dp,
                bottom = 8.dp,
            ),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SampleRow(entry: SampleEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = entry.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = entry.description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Loading screen — between tap and document ready. Shares the topbar
// shape with the detail screen so the transition reads as one screen.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen(entry: SampleEntry, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            PdfViewerTopBar(
                title = entry.title,
                backLabel = "Samples",
                onBack = onBack,
                showSearch = false,
                showShare = false,
                showDownload = false,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Building…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Detail screen — THE INTEGRATION POINT.
//
// Every chrome layer the design handoff covers (topbar, search bar
// morph, share + save + URL launchers, page indicator, gestures) is
// owned by `KmpPdfViewer.open`. The host's only job is to hand it
// the document, a title, and a back callback.
//
// Replace the bundled `Samples.brochure()` with your own PDF source
// (network bytes, a `pdf { }` you authored, a `content://` URI from
// a system picker, …) and you have a complete viewer screen for the
// price of one composable call.
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun DetailScreen(
    entry: SampleEntry,
    document: PdfDocument,
    onBack: () -> Unit,
) {
    KmpPdfViewer.open(
        document = document,
        title = entry.title,
        fileName = "${entry.id}.pdf",
        backLabel = "Samples",
        onBack = onBack,
    )
}
