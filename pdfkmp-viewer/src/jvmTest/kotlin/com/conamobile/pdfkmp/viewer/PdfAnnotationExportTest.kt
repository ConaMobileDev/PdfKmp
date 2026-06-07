package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.Color
import com.conamobile.pdfkmp.pdf
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-backend tests for [writeAnnotationsIntoPdf] — the Desktop path that
 * burns in-viewer highlights into the encoded PDF as real `Highlight`
 * text-markup annotations. Each document is produced through the public
 * `pdf { }` DSL (resolving the JVM PdfBox driver) and the result is
 * re-parsed with PdfBox's [Loader] to assert what actually landed.
 */
class PdfAnnotationExportTest {

    private fun twoPageDocument(): ByteArray = pdf {
        page { text("page one") }
        page { text("page two") }
    }.toByteArray()

    @Test
    fun exportIsSupportedOnDesktop() {
        assertTrue(pdfViewerSupportsAnnotationExport)
    }

    @Test
    fun twoHighlightsLandOnTheRightPagesWithRightColors() = runBlocking {
        val original = twoPageDocument()

        val red = Color(0xFFFF0000)   // page 0
        val green = Color(0xFF00FF00) // page 1
        val annotations = listOf(
            PdfViewerAnnotation(pageIndex = 0, x = 50f, y = 60f, width = 120f, height = 18f, color = red),
            PdfViewerAnnotation(pageIndex = 1, x = 40f, y = 100f, width = 80f, height = 14f, color = green),
        )

        val annotated = assertNotNull(
            writeAnnotationsIntoPdf(original, annotations),
            "writeAnnotationsIntoPdf returned null",
        )

        Loader.loadPDF(annotated).use { document ->
            val page0Highlights = document.getPage(0).annotations.filterIsInstance<PDAnnotationHighlight>()
            val page1Highlights = document.getPage(1).annotations.filterIsInstance<PDAnnotationHighlight>()

            assertEquals(1, page0Highlights.size, "page 0 should carry exactly one highlight")
            assertEquals(1, page1Highlights.size, "page 1 should carry exactly one highlight")

            // Colours round-trip into the annotation /C array (sRGB components).
            assertColorMatches(page0Highlights.single(), red)
            assertColorMatches(page1Highlights.single(), green)

            // Interior opacity is the constant translucent value we set.
            assertEquals(0.4f, page0Highlights.single().constantOpacity, 0.001f)
        }
    }

    @Test
    fun yAxisIsFlippedIntoPdfCoordinates() = runBlocking {
        val original = twoPageDocument()
        // A box near the TOP of the page in viewer space (small y) must map to
        // a HIGH y in PDF space (bottom-left origin), i.e. near the page top.
        val annotation = PdfViewerAnnotation(pageIndex = 0, x = 10f, y = 5f, width = 30f, height = 12f)
        val annotated = assertNotNull(writeAnnotationsIntoPdf(original, listOf(annotation)))

        Loader.loadPDF(annotated).use { document ->
            val page = document.getPage(0)
            val mediaTop = page.mediaBox.upperRightY
            val highlight = page.annotations.filterIsInstance<PDAnnotationHighlight>().single()
            val rect = highlight.rectangle
            // upperRightY of the rect should sit just below the page top
            // (mediaTop - y), confirming the flip.
            assertEquals(mediaTop - annotation.y, rect.upperRightY, 0.5f)
            assertEquals(mediaTop - (annotation.y + annotation.height), rect.lowerLeftY, 0.5f)
        }
    }

    @Test
    fun emptyAnnotationListReturnsReadableBytes() = runBlocking {
        val original = twoPageDocument()
        val out = assertNotNull(writeAnnotationsIntoPdf(original, emptyList()))
        // Still a valid, openable PDF with no highlights added.
        Loader.loadPDF(out).use { document ->
            assertEquals(2, document.numberOfPages)
            val total = (0 until document.numberOfPages).sumOf { i ->
                document.getPage(i).annotations.filterIsInstance<PDAnnotationHighlight>().size
            }
            assertEquals(0, total)
        }
    }

    @Test
    fun emptyBytesReturnNull() = runBlocking {
        assertEquals(null, writeAnnotationsIntoPdf(ByteArray(0), emptyList()))
    }

    private fun assertColorMatches(highlight: PDAnnotationHighlight, expected: Color) {
        val components = highlight.color.components
        assertEquals(3, components.size, "expected an RGB colour array")
        assertEquals(expected.red, components[0], 0.01f)
        assertEquals(expected.green, components[1], 0.01f)
        assertEquals(expected.blue, components[2], 0.01f)
    }
}
