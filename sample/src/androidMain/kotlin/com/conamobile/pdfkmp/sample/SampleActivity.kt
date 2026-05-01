package com.conamobile.pdfkmp.sample

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.samples.Samples
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save
import java.io.File
import androidx.core.graphics.createBitmap

/**
 * Hosts the Android demo for PdfKmp.
 *
 * The app shows a list of bundled sample documents; tapping one renders the
 * PDF in-process via PdfKmp, then displays each page as a vector-rendered
 * preview. The preview goes through Android's system [PdfRenderer], which
 * rasterises pages into bitmaps for display only — the on-disk and
 * over-the-wire PDF stays vector and stays sharp at any zoom level.
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
private fun SamplePreview(entry: SampleEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val filename = "${entry.title.toFileSlug()}.pdf"

    LaunchedEffect(entry) {
        val assets = SampleAssets(
            sampleImagePng = context.assets.open("sample.png").use { it.readBytes() },
        )
        val built = entry.build(assets)
        // Cache'ga saqlaymiz preview uchun. Bu yangi storage API misoli.
        val saved = built.save(StorageLocation.Cache, filename)
        pages = renderPagesToBitmaps(File(saved.path))
    }

    if (pages.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Text("Rendering…") }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(pages) { bitmap ->
                // Each page bitmap fills the available width; height
                // scales proportionally so it always fits the phone
                // screen — no horizontal scrolling needed.
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White),
                )
            }
        }
    }
}


/**
 * Rasterises every page of [pdfFile] into a [Bitmap] for display in the
 * sample. The library's output stays vector — this rasterisation only
 * happens because the Android system PDF viewer needs bitmaps to display
 * inside Compose. Native PDF readers will always render the file as vector.
 */
private fun renderPagesToBitmaps(pdfFile: File, density: Int = 2): List<Bitmap> {
    val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
    return PdfRenderer(descriptor).use { renderer ->
        val out = mutableListOf<Bitmap>()
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                val bitmap = createBitmap(page.width * density, page.height * density)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                out += bitmap
            }
        }
        out
    }
}

private fun String.toFileSlug(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
