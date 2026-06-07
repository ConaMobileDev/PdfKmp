package com.conamobile.pdfkmp.viewer

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the Desktop external-PDF search fallback
 * ([searchPdfBytes]). Builds a real PDF with PdfBox, then drives the
 * PdfBox [org.apache.pdfbox.text.PDFTextStripper] extraction path and
 * asserts the matched-region geometry lands in the viewer's coordinate
 * space (PDF points, top-left origin, Y down).
 */
class PdfTextExtractorTest {

    private val fontSize = 18f
    private val pageHeight = PDRectangle.A4.height

    /**
     * Renders [lines] top-down on an A4 page starting at [startY] points
     * **from the top** (the viewer's convention), spaced by the line
     * height, and returns the encoded bytes. PdfBox draws from the
     * bottom-left, so we flip Y at write time.
     */
    private fun pdfWithLines(
        lines: List<String>,
        startYFromTop: Float = 100f,
        leftX: Float = 72f,
    ): ByteArray {
        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(font, fontSize)
            // Move to the first baseline. Baseline ≈ top + ascent; we just
            // need a deterministic top-down layout, exact baseline maths
            // aren't asserted here.
            stream.newLineAtOffset(leftX, pageHeight - startYFromTop)
            lines.forEachIndexed { i, line ->
                if (i > 0) stream.newLineAtOffset(0f, -(fontSize * 1.4f))
                stream.showText(line)
            }
            stream.endText()
        }
        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()
        return out.toByteArray()
    }

    @Test
    fun findsCaseInsensitiveMatches() = runBlocking {
        val bytes = pdfWithLines(listOf("Hello World", "Another Line"))
        val hits = searchPdfBytes(bytes, "hello")
        assertEquals(1, hits.size, "should find the one 'Hello' occurrence, case-insensitively")
        assertEquals(0, hits.first().pageIndex)
    }

    @Test
    fun blankQueryFindsNothing() = runBlocking {
        val bytes = pdfWithLines(listOf("Hello World"))
        assertTrue(searchPdfBytes(bytes, "   ").isEmpty())
    }

    @Test
    fun emptyBytesFindNothing() = runBlocking {
        assertTrue(searchPdfBytes(ByteArray(0), "hello").isEmpty())
    }

    @Test
    fun malformedBytesDegradeToEmpty() = runBlocking {
        // Not a PDF — must not throw, just find nothing.
        assertTrue(searchPdfBytes("not a pdf at all".encodeToByteArray(), "pdf").isEmpty())
    }

    @Test
    fun matchGeometryIsInTopLeftPointSpace() = runBlocking {
        // A single short line near the top of the page: its highlight must
        // sit in the upper region (small yPoints), confirming the Y axis
        // was flipped from PdfBox's bottom-left origin to top-left.
        val startYFromTop = 80f
        val bytes = pdfWithLines(listOf("Unique"), startYFromTop = startYFromTop)
        val hits = searchPdfBytes(bytes, "Unique")
        assertEquals(1, hits.size)
        val hit = hits.first()
        // Positive, within the page, and clearly in the top quarter — not
        // mirrored to the bottom (which a missing Y-flip would produce).
        assertTrue(hit.yPoints > 0f, "yPoints must be positive (top-left origin)")
        assertTrue(
            hit.yPoints < pageHeight / 4f,
            "a line ${startYFromTop}pt from the top must map to a small yPoints, was ${hit.yPoints}",
        )
        assertTrue(hit.widthPoints > 0f && hit.heightPoints > 0f, "match must have a real box")
        // Left edge near the 72pt margin we drew at.
        assertTrue(hit.xPoints in 60f..90f, "xPoints should be near the left margin, was ${hit.xPoints}")
    }

    @Test
    fun multipleMatchesAreReturnedInDocumentOrder() = runBlocking {
        val bytes = pdfWithLines(listOf("alpha term", "beta", "term again"))
        val hits = searchPdfBytes(bytes, "term")
        assertEquals(2, hits.size, "two 'term' occurrences expected")
        // Document order: first match (line 1) above the second (line 3).
        assertTrue(
            hits[0].yPoints <= hits[1].yPoints,
            "matches must be ordered top-to-bottom",
        )
    }
}
