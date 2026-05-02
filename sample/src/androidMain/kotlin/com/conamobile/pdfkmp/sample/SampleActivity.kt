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
import com.conamobile.pdfkmp.viewer.PdfViewerScreen
import com.conamobile.pdfkmp.viewer.PdfViewerTopBar

/**
 * Hosts the Android demo for PdfKmp.
 *
 * The list lives in a tiny Scaffold; tapping a sample drops the
 * caller into [`PdfViewerScreen`][PdfViewerScreen], the library's
 * one-call all-in-one viewer. That single composable handles the
 * topbar (Minimal Mono on Android), search bar morph + match
 * highlights, share / save / hyperlink launch, and the page
 * indicator — the host doesn't manage any of that state.
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
        entry == null -> {
            // List screen — minimal header so the demo focuses on the
            // viewer chrome the handoff covers.
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
                SampleList(
                    onPick = { selected = it },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        built == null -> {
            // The PDF is still being built — show a tiny loading
            // screen instead of an empty viewer slot.
            Scaffold(
                topBar = {
                    PdfViewerTopBar(
                        title = entry.title,
                        backLabel = "Samples",
                        onBack = { selected = null },
                        showSearch = false,
                        showShare = false,
                        showDownload = false,
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Text("Building…") }
            }
        }
        else -> {
            // ── The whole detail screen is one composable. PdfViewerScreen
            // owns the topbar + search bar morph, share / save / url
            // launchers, page indicator, and viewer overlays. The sample
            // only configures *which* affordances are exposed.
            PdfViewerScreen(
                document = built,
                title = entry.title,
                fileName = "${entry.title.toFileSlug()}.pdf",
                backLabel = "Samples",
                onBack = { selected = null },
            )
        }
    }
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

private fun String.toFileSlug(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
