package com.conamobile.pdfkmp.sampledesktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.samples.Samples
import com.conamobile.pdfkmp.viewer.KmpPdfViewer
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Compose for Desktop sample for PdfKmp.
 *
 * A master/detail flow: the launch screen lists every bundled [Samples]
 * document; clicking one opens it in [KmpPdfViewer] (pages rasterised
 * through PdfBox's `PDFRenderer`), with a back affordance to return to the
 * list. Each document is built by the *same* DSL used on Android and iOS,
 * here encoded by the JVM / PdfBox backend.
 *
 * In the viewer: Ctrl/⌘ + scroll-wheel zooms (anchored at the cursor),
 * the wheel scrolls pages, the download button opens a native Save dialog,
 * and share opens the OS default PDF handler.
 *
 * Run with: `./gradlew :sample-desktop:run`
 */
private data class DesktopSample(
    val label: String,
    val fileName: String,
    val description: String,
    val build: () -> PdfDocument,
)

private fun samples(): List<DesktopSample> {
    // A generated image stands in for "a real photo" so the image-backed
    // samples render meaningful content without shipping a binary asset.
    val image = sampleImageBytes()
    return listOf(
        DesktopSample("Hello World", "hello-world.pdf", "The minimal one-page document.") { Samples.helloWorld() },
        DesktopSample("Typography", "typography.pdf", "Weights, italics, sizes, colours, alignment.") { Samples.typography() },
        DesktopSample("Row & Column", "row-column.pdf", "Flex layout with weighted children.") { Samples.rowAndColumn() },
        DesktopSample("Space Between", "space-between.pdf", "Column arrangement: SpaceBetween.") { Samples.columnSpaceBetween() },
        DesktopSample("Alignment", "alignment.pdf", "Every box alignment in one grid.") { Samples.alignmentShowcase() },
        DesktopSample("Custom Padding", "custom-padding.pdf", "Per-page padding overrides.") { Samples.customPadding() },
        DesktopSample("Long Body", "long-body.pdf", "Multi-page text flow & page breaks.") { Samples.longBody() },
        DesktopSample("Sliced Body", "sliced-body.pdf", "Content sliced across physical pages.") { Samples.slicedBody() },
        DesktopSample("Table", "table.pdf", "Styled table: headers, borders, zebra rows.") { Samples.tableShowcase() },
        DesktopSample("Vector Showcase", "vector.pdf", "SVG paths, shapes, fills as vector ops.") { Samples.vectorShowcase() },
        DesktopSample("Vector Advanced", "vector-advanced.pdf", "Gradients, arcs, group transforms.") { Samples.vectorAdvanced() },
        DesktopSample("Brochure", "brochure.pdf", "A polished multi-section marketing doc.") { Samples.brochure() },
        DesktopSample("Page Chrome", "page-chrome.pdf", "Header, footer, page numbers, watermark.") { Samples.pageChrome() },
        DesktopSample("Showcase", "showcase.pdf", "Every feature in a single document.") { Samples.showcase() },
        DesktopSample("With Image", "with-image.pdf", "Raster image, ContentScale Fit + Crop.") { Samples.withImage(image) },
        DesktopSample("Sliced Image", "sliced-image.pdf", "Tall image split across pages.") { Samples.slicedImage(image) },
        DesktopSample("Image Downscale", "image-downscale.pdf", "Down-sampling at 200 DPI.") { Samples.imageDownscale(image) },
        DesktopSample("Custom Designs", "custom-designs.pdf", "Cards, gradients, badges, images.") { Samples.customDesigns(image) },
    )
}

fun main() {
    val all = samples()
    // Generate one document up front so a headless run still proves the JVM
    // backend works (visible in the console before the window appears).
    val first = all.first().build()
    println("PdfKmp Desktop sample — JVM/PdfBox backend OK: '${all.first().label}' = ${first.size} bytes")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PdfKmp · Desktop (JVM) Sample",
        ) {
            MaterialTheme {
                var selected by remember { mutableStateOf<DesktopSample?>(null) }

                val current = selected
                if (current == null) {
                    SampleList(all, onPick = { selected = it })
                } else {
                    // `remember(current)` rebuilds the document only when the
                    // selection changes, not on every recomposition.
                    val document = remember(current) { current.build() }
                    KmpPdfViewer(
                        document = document,
                        modifier = Modifier.fillMaxSize(),
                        title = current.label,
                        fileName = current.fileName,
                        onBack = { selected = null },
                        showSearch = true,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SampleList(samples: List<DesktopSample>, onPick: (DesktopSample) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "PdfKmp Samples",
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 4.dp),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Desktop (JVM) · rendered with the PdfBox backend — click a sample to open it",
                modifier = Modifier.padding(horizontal = 24.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(samples) { sample ->
                    Card(
                        onClick = { onPick(sample) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(sample.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                sample.description,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Builds a small gradient PNG used by the image-backed samples. */
private fun sampleImageBytes(): ByteArray {
    val width = 800
    val height = 600
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.paint = GradientPaint(
            0f, 0f, Color(0x4F46E5),
            width.toFloat(), height.toFloat(), Color(0xEC4899),
        )
        g.fillRect(0, 0, width, height)
        g.color = Color(0xFFFFFF)
        g.fillOval(width / 4, height / 4, width / 2, height / 2)
    } finally {
        g.dispose()
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}
