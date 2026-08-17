package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.test.DrawCall
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A header cell may span into the body. Its merged rectangle is drawn from the
 * cell's measured `spannedHeight`, which is fixed at measure time and never
 * recomputed per chunk — so every row that cell covers has to land in the same
 * chunk as the header, or the rectangle paints past the chunk's bottom edge
 * into the margin while the rows it should cover render on the next page.
 */
class TableSliceSpanTest {

    @AfterTest
    fun resetLogger() {
        PdfLog.logger = null
    }

    @Test
    fun headerRowSpan_keepsEverySpannedBodyRowInTheHeadersChunk() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                padding = Padding.Zero
                table(columns = listOf(TableColumn.Weight(1f), TableColumn.Weight(1f))) {
                    header {
                        cell("GROUP", rowSpan = 3) { fontSize = 90.sp }
                        cell("values") { fontSize = 90.sp }
                    }
                    // Columns 0 of these two rows are claimed by the header's
                    // merged cell, so each declares a single cell.
                    row { cell("body-1") { fontSize = 90.sp } }
                    row { cell("body-2") { fontSize = 90.sp } }
                    repeat(6) { i ->
                        row {
                            cell("tail-${i}a") { fontSize = 90.sp }
                            cell("tail-${i}b") { fontSize = 90.sp }
                        }
                    }
                }
            }
        }

        // At this font size a cell wraps mid-word, so the recorded draw calls
        // are glyph runs, not whole labels — rejoin them per page before
        // looking for a label.
        val pages = factory.drivers.single().pages.map { page ->
            page.canvas.calls.filterIsInstance<DrawCall.Text>().joinToString("") { it.text }
        }
        assertTrue(pages.size > 1, "table must split for this test to mean anything; got ${pages.size} page(s)")

        val headerPage = pages.indexOfFirst { "GROUP" in it }
        assertTrue(headerPage >= 0, "header was never drawn: $pages")
        assertTrue(
            "body-1" in pages[headerPage] && "body-2" in pages[headerPage],
            "the header cell spans rows 1..2, so both must share its chunk — " +
                "page $headerPage held: ${pages[headerPage]}",
        )
    }

    @Test
    fun headerRowSpan_isReportedAndDrawnOnceInsteadOfRepeating() {
        val warnings = mutableListOf<String>()
        PdfLog.logger = { warnings += it }
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                padding = Padding.all(4.dp)
                table(columns = listOf(TableColumn.Weight(1f), TableColumn.Weight(1f))) {
                    header {
                        cell("GROUP", rowSpan = 2) { fontSize = 80.sp }
                        cell("values") { fontSize = 80.sp }
                    }
                    row { cell("body-1") { fontSize = 80.sp } }
                    repeat(8) { i -> row { cell("r$i") { fontSize = 80.sp }; cell("v$i") { fontSize = 80.sp } } }
                }
            }
        }

        val pages = factory.drivers.single().pages.map { page ->
            page.canvas.calls.filterIsInstance<DrawCall.Text>().joinToString("") { it.text }
        }
        assertTrue(pages.size > 1, "table must split for this test to mean anything; got ${pages.size} page(s)")
        val headerPages = pages.count { "GROUP" in it }
        assertTrue(headerPages == 1, "a body-spanning header must be drawn exactly once, got $headerPages")
        assertTrue(warnings.any { "rowSpan" in it }, "expected a spanning-header warning, got $warnings")
    }
}
