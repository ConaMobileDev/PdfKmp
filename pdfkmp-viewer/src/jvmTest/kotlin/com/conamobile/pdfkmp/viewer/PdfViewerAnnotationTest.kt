package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic tests for the in-viewer highlight annotation primitives
 * ([hitTestAnnotation], [buildAnnotationFromDrag]). No Compose UI harness
 * — these run on the JVM the same way the search-geometry tests do.
 */
class PdfViewerAnnotationTest {

    private fun annotation(
        page: Int = 0,
        x: Float = 10f,
        y: Float = 10f,
        w: Float = 50f,
        h: Float = 20f,
    ) = PdfViewerAnnotation(pageIndex = page, x = x, y = y, width = w, height = h)

    // ---- hit-testing -------------------------------------------------

    @Test
    fun tapInsideHits() {
        val a = annotation()
        val hit = hitTestAnnotation(listOf(a), pageIndex = 0, xPoints = 20f, yPoints = 15f)
        assertEquals(0, hit)
    }

    @Test
    fun tapOnEdgeHits() {
        // Corners are inclusive — a tap exactly on the boundary counts.
        val a = annotation(x = 10f, y = 10f, w = 50f, h = 20f)
        assertEquals(0, hitTestAnnotation(listOf(a), 0, 10f, 10f))
        assertEquals(0, hitTestAnnotation(listOf(a), 0, 60f, 30f))
    }

    @Test
    fun tapOutsideMisses() {
        val a = annotation()
        assertEquals(-1, hitTestAnnotation(listOf(a), 0, 200f, 200f))
        assertEquals(-1, hitTestAnnotation(listOf(a), 0, 5f, 15f))  // left of box
    }

    @Test
    fun tapOnOtherPageMisses() {
        val a = annotation(page = 0)
        // Point is geometrically inside the box, but on the wrong page.
        assertEquals(-1, hitTestAnnotation(listOf(a), pageIndex = 1, xPoints = 20f, yPoints = 15f))
    }

    @Test
    fun overlappingAnnotationsReturnTopmost() {
        // Two overlapping boxes on the same page; the later one in list
        // order is painted on top, so a tap in the shared region deletes it.
        val under = annotation(x = 0f, y = 0f, w = 100f, h = 100f)
        val over = annotation(x = 10f, y = 10f, w = 30f, h = 30f)
        val hit = hitTestAnnotation(listOf(under, over), 0, 20f, 20f)
        assertEquals(1, hit, "the later (on-top) annotation should win the tap")
    }

    @Test
    fun emptyListMisses() {
        assertEquals(-1, hitTestAnnotation(emptyList(), 0, 10f, 10f))
    }

    // ---- drag → annotation ------------------------------------------

    @Test
    fun dragBuildsNormalisedBox() {
        // Drag bottom-right → top-left; the result must still be a positive box.
        val a = buildAnnotationFromDrag(
            pageIndex = 2,
            startX = 80f, startY = 60f,
            endX = 20f, endY = 10f,
            pageWidth = 200f, pageHeight = 300f,
        )
        assertNotNull(a)
        assertEquals(2, a.pageIndex)
        assertEquals(20f, a.x)
        assertEquals(10f, a.y)
        assertEquals(60f, a.width)
        assertEquals(50f, a.height)
    }

    @Test
    fun dragClampsToPageBounds() {
        // Corners spill past the page edges; the box must be clamped in.
        val a = buildAnnotationFromDrag(
            pageIndex = 0,
            startX = -30f, startY = -10f,
            endX = 250f, endY = 400f,
            pageWidth = 200f, pageHeight = 300f,
        )
        assertNotNull(a)
        assertEquals(0f, a.x)
        assertEquals(0f, a.y)
        assertEquals(200f, a.width)
        assertEquals(300f, a.height)
    }

    @Test
    fun degenerateDragReturnsNull() {
        // A tap (no movement) has zero area → not a highlight.
        val a = buildAnnotationFromDrag(
            pageIndex = 0,
            startX = 40f, startY = 40f,
            endX = 40f, endY = 40f,
            pageWidth = 200f, pageHeight = 300f,
        )
        assertNull(a)
    }

    @Test
    fun zeroWidthDragReturnsNull() {
        // A purely vertical drag (no horizontal extent) is degenerate too.
        val a = buildAnnotationFromDrag(
            pageIndex = 0,
            startX = 40f, startY = 10f,
            endX = 40f, endY = 90f,
            pageWidth = 200f, pageHeight = 300f,
        )
        assertNull(a)
    }

    @Test
    fun dragHonoursCustomColor() {
        val red = Color.Red
        val a = buildAnnotationFromDrag(
            pageIndex = 0,
            startX = 0f, startY = 0f,
            endX = 50f, endY = 50f,
            pageWidth = 200f, pageHeight = 300f,
            color = red,
        )
        assertNotNull(a)
        assertEquals(red, a.color)
    }

    @Test
    fun defaultColorIsTranslucentHighlight() {
        assertEquals(PdfViewerAnnotation.DefaultHighlightColor, annotation().color)
        // Translucent (alpha well below opaque) so glyphs read through it.
        assertTrue(PdfViewerAnnotation.DefaultHighlightColor.alpha < 1f)
    }
}
