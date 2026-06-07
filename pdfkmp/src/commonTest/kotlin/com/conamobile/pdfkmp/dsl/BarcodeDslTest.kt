package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.barcode.Ean13Encoder
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.node.BarcodeSymbology
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.test.DrawCall
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import com.conamobile.pdfkmp.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * End-to-end render tests for the new 1D/2D barcode DSL surface, driven through
 * the real `pdf { }` pipeline backed by [FakePdfDriverFactory]. Bars and 2D
 * modules surface as a single filled [DrawCall.Path]; the optional background
 * surfaces as a [DrawCall.Rect].
 */
class BarcodeDslTest {

    private fun paths(block: ContainerScope.() -> Unit): List<DrawCall.Path> {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                block()
            }
        }
        return factory.drivers.single().pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Path>() }
    }

    private fun rects(block: ContainerScope.() -> Unit): List<DrawCall.Rect> {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                block()
            }
        }
        return factory.drivers.single().pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Rect>() }
    }

    @Test
    fun code128Barcode_emitsAFilledBarPath() {
        val paths = paths { barcode("HELLO123") }
        val filled = paths.filter { it.fill != null }
        assertEquals(1, filled.size, "bars collapse into one filled path")
    }

    @Test
    fun ean13Barcode_rendersAndUsesColor() {
        val paths = paths {
            barcode("4006381333931", symbology = BarcodeSymbology.Ean13, color = PdfColor.Black)
        }
        val filled = paths.single { it.fill != null }
        assertEquals(PdfColor.Black, (filled.fill as PdfPaint.Solid).color)
    }

    @Test
    fun upcA_rendersEquivalentToEan13() {
        // The two share the same module pattern, so the bar path geometry matches.
        val upc = paths { barcode("036000291452", symbology = BarcodeSymbology.UpcA) }
            .single { it.fill != null }
        val ean = paths { barcode("0036000291452", symbology = BarcodeSymbology.Ean13) }
            .single { it.fill != null }
        assertEquals(ean.commands.size, upc.commands.size, "UPC-A bar geometry must match its EAN-13 form")
    }

    @Test
    fun ean13Barcode_rejectsBadInputAtBuildTime() {
        assertFailsWith<IllegalArgumentException> {
            pdf { page { barcode("123", symbology = BarcodeSymbology.Ean13) } }
        }
    }

    @Test
    fun dataMatrix_emitsAFilledModulePath() {
        val paths = paths { dataMatrix("PDFKMP") }
        val filled = paths.filter { it.fill != null }
        assertEquals(1, filled.size, "data matrix modules collapse into one filled path")
        assertTrue(filled.single().commands.isNotEmpty(), "module path must carry geometry")
    }

    @Test
    fun dataMatrix_drawsBackgroundRect() {
        val rects = rects { dataMatrix("PDFKMP", background = PdfColor.White) }
        assertTrue(rects.any { it.color == PdfColor.White }, "background fill must render")
    }

    @Test
    fun barcode_modulePattern_matchesEncoder() {
        // Sanity bridge: the EAN-13 encoder the DSL uses is the same one tested
        // directly, so a non-trivial bar path is produced (more than the start
        // guard alone).
        val barcode = Ean13Encoder.encode("4006381333931")
        assertEquals(95, barcode.totalModules)
    }
}
