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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.conamobile.pdfkmp.viewer.PdfViewer

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleApp() {
    var selected by remember { mutableStateOf<SampleEntry?>(null) }
    var showShareButton by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.title ?: "PdfKmp samples") },
                navigationIcon = {
                    if (selected != null) {
                        Button(
                            onClick = { selected = null },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("Back") }
                    }
                },
                actions = {
                    if (selected != null) {
                        Text("Share", modifier = Modifier.padding(end = 4.dp))
                        Switch(
                            checked = showShareButton,
                            onCheckedChange = { showShareButton = it },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        if (selected == null) {
            SampleList(
                onPick = { selected = it },
                modifier = Modifier.padding(padding),
            )
        } else {
            SamplePreview(
                entry = selected!!,
                showShareButton = showShareButton,
                modifier = Modifier.padding(padding),
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

@Composable
private fun SamplePreview(
    entry: SampleEntry,
    showShareButton: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var document by remember(entry) { mutableStateOf<PdfDocument?>(null) }
    val filename = "${entry.title.toFileSlug()}.pdf"

    LaunchedEffect(entry) {
        val assets = SampleAssets(
            sampleImagePng = context.assets.open("sample.png").use { it.readBytes() },
        )
        document = entry.build(assets)
    }

    val built = document
    if (built == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Text("Building…") }
    } else {
        PdfViewer(
            document = built,
            modifier = modifier,
            showShareButton = showShareButton,
            shareFileName = filename,
        )
    }
}

private fun String.toFileSlug(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
