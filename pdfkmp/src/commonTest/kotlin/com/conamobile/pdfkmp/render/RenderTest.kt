package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.test.DrawCall
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests against the rendering pipeline using a fake driver.
 *
 * These exercise the public `pdf { ... }` entry, the layout engine, and the
 * orchestrator — without touching any platform-specific code.
 */
class RenderTest {

    @Test
    fun helloWorld_drawsTextOnce_atTopLeftOfContentFrame() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                padding = Padding.all(10.dp)
                text("Sample Text1")
            }
        }
        val driver = factory.drivers.single()
        val page = driver.pages.single()
        val text = page.canvas.calls.filterIsInstance<DrawCall.Text>().single()

        assertEquals("Sample Text1", text.text)
        assertEquals(10f, text.x)
        assertEquals(10f, text.y)
    }

    @Test
    fun longContent_overflowsToSecondPage_underMoveToNextPage() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.MoveToNextPage
            page(PageSize.A5) {
                padding = Padding.Zero
                spacing = 0.dp
                // Each line is 10 high (fontSize=10, ascent .8 + descent .2);
                // A5 height is 595, so ~59 lines fit on one page.
                repeat(80) { i -> text("line $i") { fontSize = 10.sp } }
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.pages.size >= 2, "expected page break, got ${driver.pages.size} page(s)")
    }

    @Test
    fun sliceStrategy_splitsTextAtLineBoundaries() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.A5) {
                padding = Padding.Zero
                // A single huge text node that won't fit on one A5 page.
                val long = (1..200).joinToString(separator = "\n") { "row $it" }
                text(long) { fontSize = 10.sp }
            }
        }
        val driver = factory.drivers.single()
        // Each chunk is a separate page; with Slice we expect multiple pages.
        assertTrue(driver.pages.size >= 2, "expected slicing across pages")
        // Together, the slices should cover all 200 rows in order.
        val emittedRows = driver.pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
            .map { it.text }
        assertEquals(200, emittedRows.size)
        assertEquals("row 1", emittedRows.first())
        assertEquals("row 200", emittedRows.last())
    }

    @Test
    fun customFont_isPropagatedToDriver() {
        val factory = FakePdfDriverFactory()
        val custom = com.conamobile.pdfkmp.style.PdfFont.Custom(
            name = "MyFont",
            bytes = byteArrayOf(1, 2, 3),
        )
        pdf(factory = factory) {
            page {
                text("With custom font") { font = custom }
            }
        }
        val driver = factory.drivers.single()
        assertEquals(1, driver.customFonts.size)
        assertEquals("MyFont", driver.customFonts.single().name)
    }

    @Test
    fun finish_isCalledExactlyOnce_perDocument() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page { text("a") }
            page { text("b") }
        }
        assertTrue(factory.drivers.single().finished)
    }

    @Test
    fun image_drawsOnce_atRequestedDimensions() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 200, height = 100)
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                image(bytes = pngBytes, width = 100.dp, height = 50.dp)
            }
        }
        val image = factory.drivers.single().pages.single()
            .canvas.calls.filterIsInstance<DrawCall.Image>().single()
        assertEquals(100f, image.width)
        assertEquals(50f, image.height)
        assertEquals(0f, image.sourceTop)
        assertEquals(1f, image.sourceBottom)
        assertEquals(ContentScale.Fit, image.contentScale)
    }

    @Test
    fun image_widthOnly_derivesHeightFromIntrinsicAspectRatio() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 200, height = 100) // 2:1 aspect
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                image(bytes = pngBytes, width = 60.dp)
            }
        }
        val image = factory.drivers.single().pages.single()
            .canvas.calls.filterIsInstance<DrawCall.Image>().single()
        assertEquals(60f, image.width)
        assertEquals(30f, image.height) // 60 / 2 (aspect ratio)
    }

    @Test
    fun image_slicesAcrossPages_underSliceStrategy() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 100, height = 100)
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.custom(width = 200.dp, height = 100.dp)) {
                padding = Padding.Zero
                // Image is taller than the page so slicing must kick in.
                image(bytes = pngBytes, width = 200.dp, height = 250.dp, contentScale = ContentScale.FillBounds)
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.pages.size >= 3, "expected at least 3 pages from sliced 250pt image on 100pt-tall pages")
        val images = driver.pages.flatMap {
            it.canvas.calls.filterIsInstance<DrawCall.Image>()
        }
        // Each slice must advance through the source.
        val srcTops = images.map { it.sourceTop }
        assertEquals(srcTops.sorted(), srcTops, "source slices must move strictly downward")
        assertEquals(0f, srcTops.first())
        assertEquals(1f, images.last().sourceBottom)
    }

    @Test
    fun image_widerThanContentArea_isClampedToColumnWidth() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 500, height = 250)
        pdf(factory = factory) {
            page(PageSize.custom(width = 400.dp, height = 800.dp)) {
                padding = Padding.all(20.dp)
                // Intrinsic 500pt width vs 360pt content area.
                image(bytes = pngBytes)
            }
        }
        val image = factory.drivers.single().pages.single()
            .canvas.calls.filterIsInstance<DrawCall.Image>().single()
        assertEquals(360f, image.width) // page width 400 - padding 2*20
        assertEquals(180f, image.height) // 360 / (500/250)
    }

    @Test
    fun image_allowDownScale_defaultsToTrue_andFlowsThroughToDrawCall() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 200, height = 100)
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                image(bytes = pngBytes, width = 100.dp, height = 50.dp)
            }
        }
        val image = factory.drivers.single().pages.single()
            .canvas.calls.filterIsInstance<DrawCall.Image>().single()
        assertEquals(true, image.allowDownScale)
    }

    @Test
    fun image_allowDownScale_false_isCarriedAllTheWayToDrawCall() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 200, height = 100)
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                image(bytes = pngBytes, width = 100.dp, height = 50.dp, allowDownScale = false)
            }
        }
        val image = factory.drivers.single().pages.single()
            .canvas.calls.filterIsInstance<DrawCall.Image>().single()
        assertEquals(false, image.allowDownScale)
    }

    @Test
    fun image_allowDownScale_isInheritedAcrossSlicedPages() {
        val factory = FakePdfDriverFactory()
        val pngBytes = pngHeader(width = 100, height = 100)
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.custom(width = 200.dp, height = 100.dp)) {
                padding = Padding.Zero
                image(
                    bytes = pngBytes,
                    width = 200.dp,
                    height = 250.dp,
                    contentScale = ContentScale.FillBounds,
                    allowDownScale = false,
                )
            }
        }
        val images = factory.drivers.single().pages.flatMap {
            it.canvas.calls.filterIsInstance<DrawCall.Image>()
        }
        assertTrue(images.size >= 3, "expected at least 3 sliced draw calls")
        assertTrue(images.all { !it.allowDownScale }, "every slice must keep the original opt-out")
    }

    /** Hand-built PNG header used by the image tests above. */
    private fun pngHeader(width: Int, height: Int): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D,
        0x49, 0x48, 0x44, 0x52,
        ((width ushr 24) and 0xFF).toByte(),
        ((width ushr 16) and 0xFF).toByte(),
        ((width ushr 8) and 0xFF).toByte(),
        (width and 0xFF).toByte(),
        ((height ushr 24) and 0xFF).toByte(),
        ((height ushr 16) and 0xFF).toByte(),
        ((height ushr 8) and 0xFF).toByte(),
        (height and 0xFF).toByte(),
        0x08, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )
}
