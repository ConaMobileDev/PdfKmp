package com.conamobile.pdfkmp.layout

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
    fun veryLongWord_doesNotGetSplit_evenIfLongerThanMaxWidth() {
        val metrics = FixedWidthFontMetrics(charWidth = 1f)
        val style = TextStyle(fontSize = 1.sp)
        val result = layoutText(
            text = "abcdefghij short",
            style = style,
            maxWidth = 5f,
            metrics = metrics,
        )
        // The 10-char word doesn't fit but we don't split mid-word.
        assertTrue(result.lines.any { it.text == "abcdefghij" })
        assertTrue(result.lines.any { it.text == "short" })
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
}
