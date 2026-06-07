package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.node.Span
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextScript
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.test.FixedWidthFontMetrics
import com.conamobile.pdfkmp.unit.Sp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests for [layoutRichText]'s script sizing and justification passes. */
class RichTextLayoutTest {

    private val metrics = FixedWidthFontMetrics(charWidth = 1f)

    @Test
    fun superscript_shrinksFontAndShiftsBaseline() {
        val base = TextStyle(fontSize = 10.sp)
        val result = layoutRichText(
            spans = listOf(
                Span("x", base),
                Span("2", base.copy(script = TextScript.Superscript)),
            ),
            maxWidth = 100f,
            align = TextAlign.Start,
            paragraphLineHeight = Sp.Zero,
            metrics = metrics,
        )
        val line = result.lines.single()
        val normal = line.segments.first()
        val sup = line.segments.last()

        assertEquals(10f, normal.style.fontSize.value)
        assertEquals(0f, normal.yOffset)
        // 62% scale: 10sp → 6.2sp.
        assertEquals(6.2f, sup.style.fontSize.value, absoluteTolerance = 0.001f)
        // Raised: the superscript's top sits above where a top-aligned
        // segment of its size would sit.
        assertTrue(sup.yOffset < 0f, "superscript must shift up, got ${sup.yOffset}")
    }

    @Test
    fun subscript_shiftsBaselineDown() {
        val base = TextStyle(fontSize = 10.sp)
        val result = layoutRichText(
            spans = listOf(
                Span("H", base),
                Span("2", base.copy(script = TextScript.Subscript)),
                Span("O", base),
            ),
            maxWidth = 100f,
            align = TextAlign.Start,
            paragraphLineHeight = Sp.Zero,
            metrics = metrics,
        )
        val line = result.lines.single()
        val sub = line.segments[1]
        // Lowered baseline: ownAscent (6.2 × .8 = 4.96) vs line ascent 8
        // plus the downward shift keeps yOffset strictly positive.
        assertTrue(sub.yOffset > 0f, "subscript must shift down, got ${sub.yOffset}")
    }

    @Test
    fun justify_stretchesInterWordSpaces_exceptLastLine() {
        val base = TextStyle(fontSize = 1.sp)
        val result = layoutRichText(
            spans = listOf(Span("aa bb cc dd", base)),
            maxWidth = 10f,
            align = TextAlign.Justify,
            paragraphLineHeight = Sp.Zero,
            metrics = metrics,
        )
        // Wrap: "aa bb cc " (9 ≤ 10) then "dd".
        assertEquals(2, result.lines.size)
        val first = result.lines[0]
        assertEquals(10f, first.totalWidth)
        // The invisible trailing space is dropped, so the visible words
        // (2+2+2 = 6) spread slack 4 over the two interior gaps and the
        // last word's right edge lands flush on the margin.
        val wordOffsets = first.segments.filter { it.text.isNotBlank() }.map { it.xOffset }
        assertEquals(listOf(0f, 4f, 8f), wordOffsets)
        val lastWord = first.segments.last { it.text.isNotBlank() }
        assertEquals(10f, lastWord.xOffset + lastWord.width)
        // The final line keeps its natural width.
        assertEquals(2f, result.lines[1].totalWidth)
    }
}
