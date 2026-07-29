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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Forward-progress guarantees for the Slice page-break strategy: every
 * "nothing fits on a fresh page" branch must place content anyway (and
 * overflow) rather than retry the same frame forever. Regression tests for
 * hangs where a single line taller than the frame, or a frame with zero
 * usable height, looped `beginPage` indefinitely.
 */
class SliceProgressTest {

    @AfterTest
    fun resetLogger() {
        PdfLog.logger = null
    }

    @Test
    fun sliceText_placesSingleLineTallerThanTheWholeFrame() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                padding = Padding.Zero
                text("Hi") { fontSize = 700.sp }
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.finished)
        val texts = driver.pages.map { page -> page.canvas.calls.filterIsInstance<DrawCall.Text>().map { it.text } }
        assertEquals(listOf(listOf("H"), listOf("i")), texts)
    }

    @Test
    fun slicePlacements_terminate_whenPaddingConsumesEntirePage() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                padding = Padding.all(400.dp)
                text("hello") { fontSize = 10.sp }
                image(byteArrayOf(1, 2, 3), width = 100.dp, height = 500.dp)
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.finished)
        assertTrue(driver.pages.size <= 6, "expected a bounded page count, got ${driver.pages.size}")
    }

    @Test
    fun sliceTable_placesHeaderOnlyTable() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                table(columns = listOf(TableColumn.Weight(1f))) {
                    header { cell("Only Header") }
                }
            }
        }
        val driver = factory.drivers.single()
        val texts = driver.pages.flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
        assertEquals(1, texts.count { it.text == "Only Header" })
    }

    @Test
    fun sliceTable_headerRowSpanIntoBody_suppressesHeaderRepeatWithWarning_andRenderingCompletes() {
        val warnings = mutableListOf<String>()
        PdfLog.logger = { warnings += it }
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                padding = Padding.Zero
                table(columns = listOf(TableColumn.Weight(1f))) {
                    header { cell("Sticky", rowSpan = 3) }
                    repeat(80) { i -> row { cell("row $i") { fontSize = 10.sp } } }
                }
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.finished)
        assertTrue(driver.pages.size >= 2)
        assertTrue(warnings.any { "rowSpan" in it }, "expected a rowSpan warning, got $warnings")
        // The header must not repeat on continuation pages: its merged cell
        // paints taller than the header row, so a repeat would overpaint the
        // first body row of every chunk.
        val headerDrawCount = driver.pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
            .count { it.text == "Sticky" }
        assertEquals(1, headerDrawCount, "spanning header must be drawn exactly once")
    }

    @Test
    fun sliceRow_tallerThanFrame_atTopOfPage_doesNotEmitLeadingBlankPage() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                pageBreakStrategy = PageBreakStrategy.Slice
                padding = Padding.Zero
                row { text("Hi") { fontSize = 700.sp } }
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.finished)
        assertEquals(
            1,
            driver.pages.size,
            "an oversize non-sliceable node at the top of a fresh page must overflow in " +
                "place, not open a spurious blank page first",
        )
    }
}
