package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.test.DrawCall
import com.conamobile.pdfkmp.test.FakePdfDriverFactory
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the "double alignment" bug where a parent container's
 * cross-axis alignment (column [HorizontalAlignment], box [BoxAlignment],
 * table cell horizontal alignment) used to stack on top of the child's
 * internal [TextAlign] — pushing aligned text past its parent's right edge
 * by `widest - intrinsic` PDF points.
 *
 * Every assertion measures the rendered text's right edge against the slot
 * it should occupy. Failure here means the parent and the text both shifted
 * the line, doubling the offset.
 */
class AlignmentRegressionTest {

    private val charWidth = 1f

    @Test
    fun column_endAlignment_keepsAllAlignedTextsInsideSlot() {
        // A5 width is 420pt. With Padding.Zero the column receives the full
        // page width as its slot. The column has `horizontalAlignment = End`
        // and every text inside it uses `align = End` — pre-fix, the second
        // and third texts overflowed past the page's right edge by the gap
        // between their intrinsic width and the widest sibling.
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                column(horizontalAlignment = HorizontalAlignment.End) {
                    text("LONG_TITLE_WIDEST") { fontSize = 10.sp; align = TextAlign.End }
                    text("short") { fontSize = 10.sp; align = TextAlign.End }
                    text("medium") { fontSize = 10.sp; align = TextAlign.End }
                }
            }
        }

        val pageWidth = PageSize.A5.width.value
        val texts = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>()
        assertEquals(3, texts.size)

        for (text in texts) {
            val width = text.text.length * charWidth * text.style.fontSize.value
            val rightEdge = text.x + width
            assertEqualsApprox(
                expected = pageWidth,
                actual = rightEdge,
                tag = "text='${text.text}' rightEdge",
            )
        }
    }

    @Test
    fun column_centerAlignment_centersAllAlignedTexts() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                column(horizontalAlignment = HorizontalAlignment.Center) {
                    text("WIDEST_LINE_HERE") { fontSize = 10.sp; align = TextAlign.Center }
                    text("short") { fontSize = 10.sp; align = TextAlign.Center }
                }
            }
        }

        val pageWidth = PageSize.A5.width.value
        val texts = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>()
        assertEquals(2, texts.size)

        for (text in texts) {
            val width = text.text.length * charWidth * text.style.fontSize.value
            val center = text.x + width / 2f
            assertEqualsApprox(
                expected = pageWidth / 2f,
                actual = center,
                tag = "text='${text.text}' center",
            )
        }
    }

    @Test
    fun column_startAlignment_preservesIntrinsicHugging() {
        // Default `horizontalAlignment = Start` must keep the existing
        // behaviour so callers like `row { column { ... } sibling }` keep
        // working: the column hugs its widest child rather than expanding
        // to the parent's full slot.
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                column(horizontalAlignment = HorizontalAlignment.Start) {
                    text("hello") { fontSize = 10.sp }
                    text("world") { fontSize = 10.sp }
                }
            }
        }

        val texts = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>()
        assertEquals(2, texts.size)
        // Both texts should land flush against the page's left edge.
        for (text in texts) assertEqualsApprox(0f, text.x, "text='${text.text}' x")
    }

    @Test
    fun box_centerEndAlignment_doesNotDoubleShiftAlignedText() {
        // A 200pt-wide explicit box with the only child anchored to
        // `CenterEnd` and using `TextAlign.End`. Pre-fix, the box shifted
        // the text by `200 - intrinsic` and the text shifted itself by the
        // same amount, ending up at `400 - intrinsic` — far outside the box.
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                box(width = 200.dp, height = 50.dp) {
                    aligned(BoxAlignment.CenterEnd) {
                        text("hi") { fontSize = 10.sp; align = TextAlign.End }
                    }
                }
            }
        }

        val texts = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>()
        val text = texts.single()
        val width = text.text.length * charWidth * text.style.fontSize.value
        // Box left = 0 (page padding zero). Right edge of the box = 200.
        assertEqualsApprox(200f, text.x + width, "box-aligned text right edge")
    }

    @Test
    fun box_centerCenterAlignment_centersAlignedText() {
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                box(width = 200.dp, height = 50.dp) {
                    aligned(BoxAlignment.Center) {
                        text("hi") { fontSize = 10.sp; align = TextAlign.Center }
                    }
                }
            }
        }

        val text = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>().single()
        val width = text.text.length * charWidth * text.style.fontSize.value
        assertEqualsApprox(100f, text.x + width / 2f, "box-centered text center")
    }

    @Test
    fun tableCell_endAlignment_doesNotDoubleShiftAlignedText() {
        // Single-column 200pt-wide table with `horizontalAlignment = End`
        // on the cell and `TextAlign.End` on the inner text.
        val factory = FakePdfDriverFactory()
        pdf(factory = factory) {
            page(PageSize.A5) {
                padding = Padding.Zero
                table(
                    columns = listOf(TableColumn.Fixed(200.dp)),
                    border = TableBorder.None,
                    cellPadding = Padding.Zero,
                ) {
                    row {
                        cell(horizontalAlignment = HorizontalAlignment.End) {
                            text("hi") { fontSize = 10.sp; align = TextAlign.End }
                        }
                    }
                }
            }
        }

        val text = factory.drivers.single().pages.single().canvas.calls
            .filterIsInstance<DrawCall.Text>().single()
        val width = text.text.length * charWidth * text.style.fontSize.value
        assertEqualsApprox(200f, text.x + width, "table-cell aligned text right edge")
    }

    private fun assertEqualsApprox(
        expected: Float,
        actual: Float,
        tag: String,
        tolerance: Float = 0.01f,
    ) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tolerance,
            "$tag: expected $expected, got $actual",
        )
    }
}
