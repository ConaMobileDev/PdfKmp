package com.conamobile.pdfkmp.layout

import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextOverflow
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.test.FixedWidthFontMetrics
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [layoutText]. The deterministic [FixedWidthFontMetrics] used here
 * makes the expected wrap points exactly predictable.
 */
class TextLayoutTest {

    @Test
    fun shortText_fitsOnOneLine() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "Sample Text1",
            style = style,
            maxWidth = 100f,
            metrics = metrics,
        )
        assertEquals(1, result.lines.size)
        assertEquals("Sample Text1", result.lines[0].text)
    }

    @Test
    fun longText_wrapsAtWordBoundaries() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "Hello world foo bar baz",
            style = style,
            maxWidth = 11f,
            metrics = metrics,
        )
        // Each char is one point; max width 11.
        // Greedy: "Hello world" (11), then "foo bar baz" (11).
        assertEquals(listOf("Hello world", "foo bar baz"), result.lines.map { it.text })
    }

    @Test
    fun hardLineBreaks_arePreserved() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "Line1\nLine2\nLine3",
            style = style,
            maxWidth = 100f,
            metrics = metrics,
        )
        assertEquals(listOf("Line1", "Line2", "Line3"), result.lines.map { it.text })
    }

    @Test
    fun veryLongWord_isSplitMidWord_soNoLineOverflows() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "abcdefghij short",
            style = style,
            maxWidth = 5f,
            metrics = metrics,
        )
        // The 10-char word cannot fit on a 5-point line, so it breaks
        // mid-word into full-width chunks; nothing overflows.
        assertEquals(listOf("abcde", "fghij", "short"), result.lines.map { it.text })
        assertTrue(result.lines.all { it.width <= 5f })
    }

    @Test
    fun totalHeight_equalsLineHeightTimesLines() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 10.sp)
        val result = layoutText(
            text = "one two three four",
            style = style,
            maxWidth = 8f,
            metrics = metrics,
        )
        val perLine = result.lines.first().height
        assertEquals(perLine * result.lines.size, result.size.height)
    }

    @Test
    fun justify_spreadsWords_exceptParagraphLastLine() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, align = TextAlign.Justify)
        val result = layoutText(
            text = "aa bb cc dd",
            style = style,
            maxWidth = 10f,
            metrics = metrics,
        )
        // Wrap: "aa bb cc" (8 ≤ 10), then "dd".
        assertEquals(2, result.lines.size)
        val first = result.lines[0]
        // Word widths 2+2+2 = 6; slack (10−6) splits into two gaps of 2.
        assertEquals(listOf(0f, 4f, 8f), first.justifiedWords.map { it.x })
        assertEquals(10f, first.width)
        // The paragraph's last line stays start-aligned by convention.
        assertTrue(result.lines[1].justifiedWords.isEmpty())
    }

    @Test
    fun maxLines_clipsExtraLines() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, maxLines = 2)
        val result = layoutText(
            text = "aaa bbb ccc",
            style = style,
            maxWidth = 5f,
            metrics = metrics,
        )
        assertEquals(listOf("aaa", "bbb"), result.lines.map { it.text })
    }

    @Test
    fun maxLines_withEllipsis_marksTheCut() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val result = layoutText(
            text = "aaa bbb ccc",
            style = style,
            maxWidth = 5f,
            metrics = metrics,
        )
        assertEquals(listOf("aaa", "bbb…"), result.lines.map { it.text })
        assertTrue(result.lines.all { it.width <= 5f })
    }

    @Test
    fun softHyphen_breaksWithVisibleHyphen() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "abc­def",
            style = style,
            maxWidth = 4f,
            metrics = metrics,
        )
        assertEquals(listOf("abc-", "def"), result.lines.map { it.text })
    }

    @Test
    fun direction_autoDetectsRtlFromContent() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val hebrew = layoutText("שלום עולם", style, maxWidth = 100f, metrics = metrics)
        assertEquals(TextDirection.Rtl, hebrew.resolvedDirection)
        val arabic = layoutText("مرحبا", style, maxWidth = 100f, metrics = metrics)
        assertEquals(TextDirection.Rtl, arabic.resolvedDirection)
        val latin = layoutText("hello", style, maxWidth = 100f, metrics = metrics)
        assertEquals(TextDirection.Ltr, latin.resolvedDirection)
        // Forced direction wins over content.
        val forced = layoutText("hello", style.copy(direction = TextDirection.Rtl), 100f, metrics)
        assertEquals(TextDirection.Rtl, forced.resolvedDirection)
    }

    @Test
    fun softHyphen_isInvisible_whenNoBreakNeeded() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "ab­cd",
            style = style,
            maxWidth = 10f,
            metrics = metrics,
        )
        assertEquals(listOf("abcd"), result.lines.map { it.text })
    }
}
