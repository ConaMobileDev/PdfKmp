package com.conamobile.pdfkmp.vector

import com.conamobile.pdfkmp.style.PdfColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [VectorImage.parse] covering both Android Vector and SVG inputs. */
class VectorParserTest {

    @Test
    fun androidVector_singlePath_parsesViewportAndFillColor() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="24dp"
                android:height="24dp"
                android:viewportWidth="24"
                android:viewportHeight="24">
                <path
                    android:fillColor="#FF1F6FEB"
                    android:pathData="M12,2L2,22h20L12,2z" />
            </vector>
        """.trimIndent()
        val image = VectorImage.parse(xml)

        assertEquals(24f, image.viewportWidth)
        assertEquals(24f, image.viewportHeight)
        assertEquals(24f, image.intrinsicWidth)
        assertEquals(24f, image.intrinsicHeight)
        assertEquals(1, image.paths.size)

        val path = image.paths.first()
        assertEquals(PdfColor.fromArgb(0xFF1F6FEB), path.fillColor)
        assertNull(path.strokeColor)

        // Path data: M12,2 L2,22 h20 L12,2 Z → 1 MoveTo + 3 LineTos + 1 Close.
        assertEquals(5, path.commands.size)
        assertTrue(path.commands.first() is PathCommand.MoveTo)
        assertTrue(path.commands.last() is PathCommand.Close)
    }

    @Test
    fun svg_withViewBox_parsesPathsAndFill() {
        val xml = """
            <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24">
                <path d="M0 0L24 24" fill="#FF0000" />
                <path d="M24 0L0 24" stroke="black" stroke-width="2" />
            </svg>
        """.trimIndent()
        val image = VectorImage.parse(xml)

        assertEquals(24f, image.viewportWidth)
        assertEquals(24f, image.viewportHeight)
        assertEquals(48f, image.intrinsicWidth)
        assertEquals(48f, image.intrinsicHeight)
        assertEquals(2, image.paths.size)
        assertEquals(PdfColor.Red, image.paths[0].fillColor)
        assertNull(image.paths[0].strokeColor)
        assertEquals(PdfColor.Black, image.paths[1].strokeColor)
        assertEquals(2f, image.paths[1].strokeWidth)
    }

    @Test
    fun relativePathCommands_resolveToAbsoluteCoordinates() {
        // m10,10 l5,0 l0,5 z  → MoveTo(10,10), LineTo(15,10), LineTo(15,15), Close.
        val xml = """<svg viewBox="0 0 100 100"><path d="m10,10 l5,0 l0,5 z" fill="black"/></svg>"""
        val commands = VectorImage.parse(xml).paths.first().commands

        assertEquals(PathCommand.MoveTo(10f, 10f), commands[0])
        assertEquals(PathCommand.LineTo(15f, 10f), commands[1])
        assertEquals(PathCommand.LineTo(15f, 15f), commands[2])
        assertEquals(PathCommand.Close, commands[3])
    }

    @Test
    fun horizontalAndVerticalShortcuts_expandToLineTos() {
        val xml = """<svg viewBox="0 0 100 100"><path d="M0,0 H10 V20" fill="black"/></svg>"""
        val commands = VectorImage.parse(xml).paths.first().commands

        assertEquals(PathCommand.MoveTo(0f, 0f), commands[0])
        assertEquals(PathCommand.LineTo(10f, 0f), commands[1])
        assertEquals(PathCommand.LineTo(10f, 20f), commands[2])
    }

    @Test
    fun cubicBezier_isParsed() {
        val xml = """<svg viewBox="0 0 100 100"><path d="M0,0 C10,0 20,10 30,10" fill="black"/></svg>"""
        val commands = VectorImage.parse(xml).paths.first().commands

        assertEquals(PathCommand.MoveTo(0f, 0f), commands[0])
        assertEquals(PathCommand.CubicTo(10f, 0f, 20f, 10f, 30f, 10f), commands[1])
    }

    @Test
    fun smoothCubicShorthand_reflectsPreviousControlPoint() {
        // M0,0 C5,-5 5,5 10,0  S15,5 20,0
        // Smooth-cubic's first control = reflection of (5,5) about (10,0) = (15,-5)
        val xml = """<svg viewBox="0 0 100 100"><path d="M0,0 C5,-5 5,5 10,0 S15,5 20,0" fill="black"/></svg>"""
        val commands = VectorImage.parse(xml).paths.first().commands
        val third = commands[2] as PathCommand.CubicTo
        assertEquals(15f, third.c1x)
        assertEquals(-5f, third.c1y)
    }

    @Test
    fun unknownRoot_throws() {
        val xml = """<png><pathData/></png>"""
        assertFailsWith<IllegalArgumentException> { VectorImage.parse(xml) }
    }

    @Test
    fun arcCommand_isConvertedToCubicBeziers() {
        val xml = """<svg viewBox="0 0 100 100"><path d="M0,0 A10,10 0 0,0 20,0" fill="black"/></svg>"""
        val commands = VectorImage.parse(xml).paths.first().commands

        // First command is the start MoveTo; everything after must be cubic
        // Béziers (the arc decomposition).
        assertEquals(PathCommand.MoveTo(0f, 0f), commands.first())
        assertTrue(commands.drop(1).all { it is PathCommand.CubicTo })
        // The arc ends at (20, 0).
        val last = commands.last() as PathCommand.CubicTo
        assertEquals(20f, last.x)
        assertTrue(kotlin.math.abs(last.y) < 0.01f)
    }
}
