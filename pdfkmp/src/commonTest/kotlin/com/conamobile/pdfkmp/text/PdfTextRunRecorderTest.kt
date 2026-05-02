package com.conamobile.pdfkmp.text

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that the recording driver inserted by [pdf] captures a
 * [PdfTextRun] for every wrapped text line that reaches the canvas,
 * with the right position / size / page index.
 *
 * The recorder is the only piece keeping `:pdfkmp-viewer`'s text
 * selection overlay honest — every regression here means consumers
 * either lose selectable text or get drift between the rasterised
 * glyphs and the invisible Compose layer that powers selection.
 */
class PdfTextRunRecorderTest {

    @Test
    fun helloWorld_recordsSingleRunAtContentOrigin() {
        val document = pdf(factory = FakePdfDriverFactory()) {
            page {
                padding = Padding.all(10.dp)
                text("Sample Text1")
            }
        }

        val run = document.textRuns.single()
        assertEquals(0, run.pageIndex)
        assertEquals("Sample Text1", run.text)
        assertEquals(10f, run.xPoints)
        assertEquals(10f, run.yPoints)
        assertTrue(run.widthPoints > 0f, "expected a positive advance width")
        assertTrue(run.heightPoints > 0f, "expected a positive glyph height")
        assertEquals(12f, run.fontSizePoints, "default fontSize is 12.sp")
    }

    @Test
    fun multiplePages_assignsCorrectPageIndices() {
        val document = pdf(factory = FakePdfDriverFactory()) {
            defaultPageBreakStrategy = PageBreakStrategy.MoveToNextPage
            page(PageSize.A5) {
                padding = Padding.Zero
                spacing = 0.dp
                // 80 lines × 10pt height >> A5 height (595pt) → forces a page break.
                repeat(80) { i -> text("line $i") { fontSize = 10.sp } }
            }
        }

        val pageIndices = document.textRuns.map { it.pageIndex }.toSet()
        assertTrue(pageIndices.size >= 2, "expected runs spread across at least two pages")
        assertEquals(0, pageIndices.min(), "first page is index 0")
    }

    @Test
    fun multilineText_recordsOneRunPerWrappedLine() {
        val document = pdf(factory = FakePdfDriverFactory()) {
            page {
                padding = Padding.Zero
                text("alpha\nbeta\ngamma")
            }
        }

        val texts = document.textRuns.map { it.text }
        assertEquals(listOf("alpha", "beta", "gamma"), texts)
        // y advances line by line; each next run starts below the previous.
        val ys = document.textRuns.map { it.yPoints }
        assertTrue(ys.zipWithNext().all { (a, b) -> b > a }, "y must grow per line: $ys")
    }
}
