package com.conamobile.pdfkmp.sampledesktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.samples.Samples
import com.conamobile.pdfkmp.viewer.KmpPdfViewer

/**
 * Compose for Desktop sample for PdfKmp.
 *
 * Builds each bundled [Samples] document with the *same* DSL used on
 * Android and iOS — here encoded by the JVM / PdfBox backend — and shows it
 * in [KmpPdfViewer], which rasterises pages through PdfBox's `PDFRenderer`.
 * The chips switch between samples; the topbar's download/share write to
 * `~/Downloads` / open the OS default handler.
 *
 * Run with: `./gradlew :sample-desktop:run`
 */
private data class DesktopSample(val label: String, val fileName: String, val build: () -> PdfDocument)

private val samples = listOf(
    DesktopSample("Brochure", "brochure.pdf") { Samples.brochure() },
    DesktopSample("Showcase", "showcase.pdf") { Samples.showcase() },
    DesktopSample("Typography", "typography.pdf") { Samples.typography() },
    DesktopSample("Table", "table.pdf") { Samples.tableShowcase() },
    DesktopSample("Vector", "vector.pdf") { Samples.vectorAdvanced() },
)

fun main() {
    // Generate one document up front so a headless run still proves the JVM
    // backend works (visible in the console before the window appears).
    val first = samples.first().build()
    println("PdfKmp Desktop sample — JVM/PdfBox backend OK: '${samples.first().label}' = ${first.size} bytes")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PdfKmp · Desktop (JVM) Sample",
        ) {
            MaterialTheme {
                var selected by remember { mutableStateOf(0) }
                val document = remember(selected) { samples[selected].build() }

                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        samples.forEachIndexed { index, sample ->
                            FilterChip(
                                selected = index == selected,
                                onClick = { selected = index },
                                label = { Text(sample.label) },
                            )
                        }
                    }
                    KmpPdfViewer(
                        document = document,
                        modifier = Modifier.weight(1f),
                        title = samples[selected].label,
                        fileName = samples[selected].fileName,
                        showSearch = false,
                    )
                }
            }
        }
    }
}
