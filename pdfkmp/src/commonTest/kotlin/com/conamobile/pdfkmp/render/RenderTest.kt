package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.TableColumn
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
    fun sliceStrategy_splitsColumnsRecursively() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.A5) {
                padding = Padding.Zero
                spacing = 0.dp
                // One undecorated column holding 100 lines (10pt each =
                // 1000pt) — far taller than an A5 page (595pt). Recursive
                // slicing must spread the children across pages instead of
                // moving the whole column.
                column(spacing = 0.dp) {
                    repeat(100) { i -> text("item $i") { fontSize = 10.sp } }
                }
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.pages.size >= 2, "expected the column to slice across pages")
        val texts = driver.pages.flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
        assertEquals(100, texts.size)
        // First page must contain the first item, last page the last item.
        assertEquals("item 0", driver.pages.first().canvas.calls.filterIsInstance<DrawCall.Text>().first().text)
        assertEquals("item 99", driver.pages.last().canvas.calls.filterIsInstance<DrawCall.Text>().last().text)
        // Nothing may be drawn past the page's bottom edge.
        for (page in driver.pages) {
            for (text in page.canvas.calls.filterIsInstance<DrawCall.Text>()) {
                assertTrue(text.y + 10f <= 595f, "line at y=${text.y} overflows the A5 page")
            }
        }
    }

    @Test
    fun sliceStrategy_splitsTableBetweenRows_andRepeatsHeader() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.A5) {
                padding = Padding.Zero
                spacing = 0.dp
                table(
                    columns = listOf(TableColumn.Weight(1f)),
                ) {
                    header { cell("HEADER") }
                    repeat(60) { i -> row { cell("row $i") } }
                }
            }
        }
        val driver = factory.drivers.single()
        assertTrue(driver.pages.size >= 2, "expected the table to slice across pages")
        // Every page that has table content must start with the header.
        for (page in driver.pages) {
            val texts = page.canvas.calls.filterIsInstance<DrawCall.Text>().map { it.text }
            if (texts.isNotEmpty()) {
                assertEquals("HEADER", texts.first(), "page must repeat the header row")
            }
        }
        // All 60 body rows appear exactly once, in order.
        val rows = driver.pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
            .map { it.text }
            .filter { it.startsWith("row ") }
        assertEquals((0 until 60).map { "row $it" }, rows)
    }

    @Test
    fun tableOfContents_resolvesTitlesAndPageNumbers() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.MoveToNextPage
            page(PageSize.A5) {
                text("Contents") { fontSize = 20.sp }
                tableOfContents()
            }
            page(PageSize.A5) {
                bookmark("First Chapter")
                text("First chapter body")
            }
            page(PageSize.A5) {
                bookmark("Second Chapter")
                bookmark("Hidden Section", level = 2)
                text("Second chapter body")
            }
        }
        val driver = factory.drivers.single()
        val tocTexts = driver.pages.first().canvas.calls
            .filterIsInstance<DrawCall.Text>()
            .map { it.text }
        // Entries with their resolved physical page numbers; the level-2
        // bookmark stays out (default maxLevel = 1).
        assertTrue("First Chapter" in tocTexts, "TOC must list the first chapter")
        assertTrue("Second Chapter" in tocTexts, "TOC must list the second chapter")
        assertTrue("Hidden Section" !in tocTexts, "level-2 bookmark must be filtered out")
        assertTrue("2" in tocTexts, "first chapter page number missing: $tocTexts")
        assertTrue("3" in tocTexts, "second chapter page number missing: $tocTexts")
        // Entries link to the anchors injected next to the bookmarks.
        val tocLinks = driver.pages.first().canvas.calls
            .filterIsInstance<DrawCall.LinkToDestination>()
        assertEquals(2, tocLinks.size)
        // And the bookmark pages register those destinations.
        val destinations = driver.pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.NamedDestination>() }
            .map { it.name }
        assertTrue(tocLinks.all { it.name in destinations }, "TOC links must target registered anchors")
    }

    @Test
    fun rotationAndOpacity_wrapTheContainer() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                padding = Padding.Zero
                box(width = 100.dp, height = 50.dp, rotation = 45f, opacity = 0.5f) {
                    text("rotated")
                }
            }
        }
        val calls = factory.drivers.single().pages.single().canvas.calls
        val rotate = calls.filterIsInstance<DrawCall.Rotate>().single()
        assertEquals(45f, rotate.degrees)
        // Pivot is the container's centre.
        assertEquals(50f, rotate.pivotX)
        assertEquals(25f, rotate.pivotY)
        val begin = calls.filterIsInstance<DrawCall.BeginTransparencyGroup>().single()
        assertEquals(0.5f, begin.alpha)
        assertEquals(1, calls.filterIsInstance<DrawCall.EndTransparencyGroup>().size)
        // The transform opens before the text and closes after it.
        assertTrue(calls.indexOf(rotate) < calls.indexOfFirst { it is DrawCall.Text })
        assertTrue(calls.indexOfFirst { it is DrawCall.Text } < calls.indexOf(DrawCall.EndTransparencyGroup))
    }

    @Test
    fun keepTogether_movesWholeGroup_insteadOfSlicing() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.custom(width = 200.dp, height = 100.dp)) {
                padding = Padding.Zero
                spacing = 0.dp
                // 8 lines fill 80pt of the 100pt page…
                repeat(8) { i -> text("filler $i") { fontSize = 10.sp } }
                // …and this 40pt group would slice at 2 lines without the
                // wrapper. keepTogether must move it to page 2 whole.
                keepTogether {
                    repeat(4) { i -> text("group $i") { fontSize = 10.sp } }
                }
            }
        }
        val driver = factory.drivers.single()
        assertEquals(2, driver.pages.size)
        val secondPageTexts = driver.pages[1].canvas.calls
            .filterIsInstance<DrawCall.Text>()
            .map { it.text }
        assertEquals(listOf("group 0", "group 1", "group 2", "group 3"), secondPageTexts)
    }

    @Test
    fun widowControl_pullsLinesForward() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.custom(width = 200.dp, height = 100.dp)) {
                padding = Padding.Zero
                // 11 hard lines of 10pt: 10 fit the page, leaving a lone
                // widow. minLinesAfterBreak = 3 must pull two lines back.
                val elevenLines = (1..11).joinToString(separator = "\n") { "ln$it" }
                text(elevenLines) {
                    fontSize = 10.sp
                    minLinesAfterBreak = 3
                }
            }
        }
        val driver = factory.drivers.single()
        assertEquals(2, driver.pages.size)
        val secondPage = driver.pages[1].canvas.calls
            .filterIsInstance<DrawCall.Text>()
            .map { it.text }
        assertEquals(listOf("ln9", "ln10", "ln11"), secondPage)
    }

    @Test
    fun multiColumn_balancesChildrenAcrossColumns() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                columns(count = 2, gap = 20.dp, spacing = 0.dp) {
                    repeat(10) { i -> text("p$i") { fontSize = 10.sp } }
                }
            }
        }
        val texts = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>()
        assertEquals(10, texts.size)
        // A5 is 420 wide → two 200pt columns with a 20pt gap.
        val columnsX = texts.map { it.x }.distinct().sorted()
        assertEquals(listOf(0f, 220f), columnsX)
        // Equal-height children split evenly.
        assertEquals(5, texts.count { it.x == 0f })
        assertEquals(5, texts.count { it.x == 220f })
    }

    @Test
    fun rtlText_anchorsToTheRightEdge() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                // 9 chars × 10pt = 90pt wide at the fixed-width fake metrics.
                text("שלום עולם") { fontSize = 10.sp }
            }
        }
        val page = factory.drivers.single().pages.single()
        val text = page.canvas.calls.filterIsInstance<DrawCall.Text>().single()
        // A5 is 420pt wide; an RTL paragraph's Start edge is the right one.
        assertEquals(420f - 90f, text.x)
    }

    @Test
    fun tableOfContents_worksInsideColumnsAndKeepTogether() {
        // Regression: the TOC tree walkers must descend into columns{} and
        // keepTogether{} wrappers — previously an unexpanded TocNode
        // reached the layout engine and crashed the render, and nested
        // bookmarks were dropped from the TOC.
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                keepTogether { tableOfContents() }
            }
            page(PageSize.A5) {
                columns(count = 2) {
                    bookmark("Nested Chapter")
                    text("body")
                }
            }
        }
        val tocTexts = factory.drivers.single().pages.first().canvas.calls
            .filterIsInstance<DrawCall.Text>()
            .map { it.text }
        assertTrue("Nested Chapter" in tocTexts, "bookmark inside columns{} must reach the TOC: $tocTexts")
    }

    @Test
    fun slicedTable_withoutRepeatHeader_stillDrawsHeaderOnce() {
        // Regression: a table starting too low for header+first row used to
        // lose its header entirely when repeatHeader = false.
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page(PageSize.custom(width = 200.dp, height = 100.dp)) {
                padding = Padding.Zero
                spacing = 0.dp
                // Fill the page so the table starts with less room than
                // header + one row (~2 × ~21pt with default cell padding).
                repeat(7) { i -> text("filler $i") { fontSize = 10.sp } }
                table(
                    columns = listOf(TableColumn.Weight(1f)),
                    repeatHeader = false,
                ) {
                    header { cell("HEAD") }
                    repeat(3) { i -> row { cell("r$i") } }
                }
            }
        }
        val texts = factory.drivers.single().pages
            .flatMap { it.canvas.calls.filterIsInstance<DrawCall.Text>() }
            .map { it.text }
        assertEquals(1, texts.count { it == "HEAD" }, "header must be drawn exactly once: $texts")
        assertEquals(listOf("r0", "r1", "r2"), texts.filter { it.startsWith("r") })
    }

    @Test
    fun navigationMarkers_reachTheCanvas() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page {
                bookmark("Chapter 1")
                anchor("ch1")
                text("Chapter 1 content")
                linkToAnchor(anchor = "ch1") { text("back to top") }
            }
        }
        val calls = factory.drivers.single().pages.single().canvas.calls
        val bookmark = calls.filterIsInstance<DrawCall.Bookmark>().single()
        assertEquals("Chapter 1", bookmark.title)
        assertEquals(0, bookmark.level)
        assertEquals("ch1", calls.filterIsInstance<DrawCall.NamedDestination>().single().name)
        val link = calls.filterIsInstance<DrawCall.LinkToDestination>().single()
        assertEquals("ch1", link.name)
        assertTrue(link.width > 0f && link.height > 0f)
    }

    @Test
    fun oversizedImage_scalesDownToFitThePage_underMoveToNextPage() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            defaultPageBreakStrategy = PageBreakStrategy.MoveToNextPage
            page(PageSize.A5) {
                padding = Padding.Zero
                // Bytes are not a real image, so layout falls back to the
                // explicit dimensions: far taller than an A5 page (595).
                image(bytes = ByteArray(16), width = 100.dp, height = 2000.dp)
            }
        }
        val driver = factory.drivers.single()
        val page = driver.pages.single()
        val image = page.canvas.calls.filterIsInstance<DrawCall.Image>().single()

        // Scaled to the frame height, aspect preserved (2000→595 ⇒ ×0.2975).
        assertEquals(595f, image.height)
        assertEquals(100f * (595f / 2000f), image.width)
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
