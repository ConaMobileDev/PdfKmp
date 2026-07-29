package com.conamobile.pdfkmp.vector

import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    // ---- Shape elements ------------------------------------------------------

    @Test
    fun rect_squareCorners_isFourLinesAndClose() {
        val xml = """<svg viewBox="0 0 100 100"><rect x="10" y="20" width="30" height="40" fill="red"/></svg>"""
        val path = VectorImage.parse(xml).paths.first()
        val cmds = path.commands

        assertEquals(PdfColor.Red, path.fillColor)
        // MoveTo + 3 LineTo + Close.
        assertEquals(5, cmds.size)
        assertEquals(PathCommand.MoveTo(10f, 20f), cmds[0])
        assertEquals(PathCommand.LineTo(40f, 20f), cmds[1])
        assertEquals(PathCommand.LineTo(40f, 60f), cmds[2])
        assertEquals(PathCommand.LineTo(10f, 60f), cmds[3])
        assertTrue(cmds.last() is PathCommand.Close)
    }

    @Test
    fun rect_roundedCorners_emitsCubicsAtCorners() {
        val xml = """<svg viewBox="0 0 100 100"><rect x="0" y="0" width="20" height="20" rx="5" fill="black"/></svg>"""
        val cmds = VectorImage.parse(xml).paths.first().commands

        // Starts after the top-left corner at (rx, 0) and contains 4 corner cubics.
        assertEquals(PathCommand.MoveTo(5f, 0f), cmds.first())
        assertEquals(4, cmds.count { it is PathCommand.CubicTo })
        assertTrue(cmds.last() is PathCommand.Close)
    }

    @Test
    fun rect_onlyRyGiven_appliesSymmetricRadius() {
        // SVG: when only one of rx/ry is set, it applies to both axes.
        val xml = """<svg viewBox="0 0 100 100"><rect width="20" height="20" ry="4" fill="black"/></svg>"""
        val cmds = VectorImage.parse(xml).paths.first().commands
        assertEquals(PathCommand.MoveTo(4f, 0f), cmds.first())
        assertEquals(4, cmds.count { it is PathCommand.CubicTo })
    }

    @Test
    fun rect_zeroSize_producesNoPath() {
        val xml = """<svg viewBox="0 0 100 100"><rect x="1" y="1" width="0" height="10" fill="black"/></svg>"""
        assertTrue(VectorImage.parse(xml).paths.isEmpty())
    }

    @Test
    fun circle_isFourCubicsAroundCenter() {
        val xml = """<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="10" fill="blue"/></svg>"""
        val path = VectorImage.parse(xml).paths.first()
        val cmds = path.commands

        assertEquals(PdfColor.Blue, path.fillColor)
        // Starts at the right vertex (cx + r, cy).
        assertEquals(PathCommand.MoveTo(60f, 50f), cmds.first())
        assertEquals(4, cmds.count { it is PathCommand.CubicTo })
        assertTrue(cmds.last() is PathCommand.Close)
        // The last cubic returns to the right vertex.
        val last = cmds[cmds.size - 2] as PathCommand.CubicTo
        assertTrue(kotlin.math.abs(last.x - 60f) < 0.01f)
        assertTrue(kotlin.math.abs(last.y - 50f) < 0.01f)
    }

    @Test
    fun ellipse_usesSeparateRadii() {
        val xml = """<svg viewBox="0 0 100 100"><ellipse cx="40" cy="30" rx="20" ry="10" fill="black"/></svg>"""
        val cmds = VectorImage.parse(xml).paths.first().commands
        assertEquals(PathCommand.MoveTo(60f, 30f), cmds.first())
        assertEquals(4, cmds.count { it is PathCommand.CubicTo })
    }

    @Test
    fun line_isMoveToThenLineTo() {
        val xml = """<svg viewBox="0 0 100 100"><line x1="1" y1="2" x2="8" y2="9" stroke="black"/></svg>"""
        val path = VectorImage.parse(xml).paths.first()

        assertEquals(listOf(PathCommand.MoveTo(1f, 2f), PathCommand.LineTo(8f, 9f)), path.commands)
        assertEquals(PdfColor.Black, path.strokeColor)
        // No fill default applies because a bare <line> has zero area; but
        // the SVG model still fills it black — we just assert the stroke here.
    }

    @Test
    fun polyline_isOpen_polygon_isClosed() {
        val openXml = """<svg viewBox="0 0 100 100"><polyline points="0,0 10,0 10,10" fill="none" stroke="black"/></svg>"""
        val open = VectorImage.parse(openXml).paths.first().commands
        assertEquals(PathCommand.MoveTo(0f, 0f), open.first())
        assertEquals(PathCommand.LineTo(10f, 10f), open.last())
        assertTrue(open.none { it is PathCommand.Close })

        val polyXml = """<svg viewBox="0 0 100 100"><polygon points="0,0 10,0 10,10" fill="black"/></svg>"""
        val poly = VectorImage.parse(polyXml).paths.first().commands
        assertTrue(poly.last() is PathCommand.Close)
        assertEquals(3, poly.count { it is PathCommand.LineTo } + poly.count { it is PathCommand.MoveTo })
    }

    @Test
    fun polygon_malformedPoints_producesNoPathInsteadOfCrashing() {
        // Odd number of coordinates is malformed — should be skipped, not throw.
        val xml = """<svg viewBox="0 0 100 100"><polygon points="0,0 10" fill="black"/></svg>"""
        assertTrue(VectorImage.parse(xml).paths.isEmpty())
    }

    // ---- Group transform inheritance ----------------------------------------

    @Test
    fun groupTranslate_appliesToChildShape() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <g transform="translate(10,20)">
                <rect x="0" y="0" width="5" height="5" fill="black"/>
              </g>
            </svg>
        """.trimIndent()
        val cmds = VectorImage.parse(xml).paths.first().commands
        assertEquals(PathCommand.MoveTo(10f, 20f), cmds.first())
    }

    @Test
    fun nestedGroupTranslates_compose() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <g transform="translate(10,10)">
                <g transform="translate(5,5)">
                  <rect x="0" y="0" width="2" height="2" fill="black"/>
                </g>
              </g>
            </svg>
        """.trimIndent()
        val cmds = VectorImage.parse(xml).paths.first().commands
        // (0,0) translated by (10,10) then (5,5) → (15,15).
        assertEquals(PathCommand.MoveTo(15f, 15f), cmds.first())
    }

    @Test
    fun groupScaleThenChildTranslate_composeLeftToRight() {
        // Group scales by 2; the child line moves to (3,4) inside that space → (6,8).
        val xml = """
            <svg viewBox="0 0 100 100">
              <g transform="scale(2)">
                <line x1="3" y1="4" x2="3" y2="4" stroke="black"/>
              </g>
            </svg>
        """.trimIndent()
        val cmds = VectorImage.parse(xml).paths.first().commands
        assertEquals(PathCommand.MoveTo(6f, 8f), cmds.first())
    }

    @Test
    fun transformOnShape_composesWithGroupTransform() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <g transform="translate(100,0)">
                <rect x="0" y="0" width="1" height="1" transform="translate(0,50)" fill="black"/>
              </g>
            </svg>
        """.trimIndent()
        val cmds = VectorImage.parse(xml).paths.first().commands
        assertEquals(PathCommand.MoveTo(100f, 50f), cmds.first())
    }

    @Test
    fun groupRotate90_rotatesChildPoint() {
        // rotate(90) maps (10,0) → (0,10).
        val xml = """
            <svg viewBox="0 0 100 100">
              <g transform="rotate(90)">
                <line x1="10" y1="0" x2="10" y2="0" stroke="black"/>
              </g>
            </svg>
        """.trimIndent()
        val cmds = VectorImage.parse(xml).paths.first().commands
        val move = cmds.first() as PathCommand.MoveTo
        assertTrue(kotlin.math.abs(move.x) < 0.001f)
        assertTrue(kotlin.math.abs(move.y - 10f) < 0.001f)
    }

    // ---- Style inheritance & resolution -------------------------------------

    @Test
    fun groupFill_isInheritedByChildren() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <g fill="red">
                <rect x="0" y="0" width="5" height="5"/>
              </g>
            </svg>
        """.trimIndent()
        assertEquals(PdfColor.Red, VectorImage.parse(xml).paths.first().fillColor)
    }

    @Test
    fun childFill_overridesInheritedGroupFill() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <g fill="red">
                <rect x="0" y="0" width="5" height="5" fill="blue"/>
              </g>
            </svg>
        """.trimIndent()
        assertEquals(PdfColor.Blue, VectorImage.parse(xml).paths.first().fillColor)
    }

    @Test
    fun inlineStyle_overridesPresentationAttribute() {
        // Presentation attr says red, inline style says blue → blue wins.
        val xml = """<svg viewBox="0 0 100 100"><rect width="5" height="5" fill="red" style="fill:blue"/></svg>"""
        assertEquals(PdfColor.Blue, VectorImage.parse(xml).paths.first().fillColor)
    }

    @Test
    fun inlineStyle_setsStrokeAndWidth() {
        val xml = """<svg viewBox="0 0 100 100"><rect width="5" height="5" style="fill:none;stroke:green;stroke-width:3"/></svg>"""
        val path = VectorImage.parse(xml).paths.first()
        assertNull(path.fillColor)
        // CSS "green" is the dark 0,128,0 green.
        assertEquals(PdfColor(0f, 128f / 255f, 0f), path.strokeColor)
        assertEquals(3f, path.strokeWidth)
    }

    @Test
    fun fillNone_yieldsNullFill() {
        val xml = """<svg viewBox="0 0 100 100"><rect width="5" height="5" fill="none" stroke="black"/></svg>"""
        assertNull(VectorImage.parse(xml).paths.first().fill)
    }

    @Test
    fun defaultFill_isBlackWhenUnspecified() {
        val xml = """<svg viewBox="0 0 100 100"><rect width="5" height="5"/></svg>"""
        assertEquals(PdfColor.Black, VectorImage.parse(xml).paths.first().fillColor)
    }

    @Test
    fun fillOpacity_isFoldedIntoAlpha() {
        val xml = """<svg viewBox="0 0 100 100"><rect width="5" height="5" fill="black" fill-opacity="0.5"/></svg>"""
        val color = VectorImage.parse(xml).paths.first().fillColor
        assertNotNull(color)
        assertTrue(kotlin.math.abs(color.alpha - 0.5f) < 0.001f)
    }

    @Test
    fun elementOpacity_multipliesWithGroupOpacity() {
        // Group opacity 0.5 × element fill-opacity 0.5 → 0.25 alpha.
        val xml = """
            <svg viewBox="0 0 100 100">
              <g opacity="0.5">
                <rect width="5" height="5" fill="black" fill-opacity="0.5"/>
              </g>
            </svg>
        """.trimIndent()
        val color = VectorImage.parse(xml).paths.first().fillColor
        assertNotNull(color)
        assertTrue(kotlin.math.abs(color.alpha - 0.25f) < 0.001f)
    }

    // ---- Colour parsing ------------------------------------------------------

    @Test
    fun namedColors_resolve() {
        fun fillOf(name: String): PdfColor? =
            VectorImage.parse("""<svg viewBox="0 0 10 10"><rect width="5" height="5" fill="$name"/></svg>""")
                .paths.first().fillColor

        assertEquals(PdfColor(1f, 1f, 0f), fillOf("yellow"))
        assertEquals(PdfColor(1f, 165f / 255f, 0f), fillOf("orange"))
        assertEquals(PdfColor(0f, 1f, 1f), fillOf("cyan"))
        assertEquals(PdfColor(0f, 0f, 128f / 255f), fillOf("navy"))
        assertEquals(PdfColor(128f / 255f, 128f / 255f, 0f), fillOf("olive"))
        // Case-insensitive.
        assertEquals(PdfColor(1f, 0f, 1f), fillOf("MAGENTA"))
    }

    @Test
    fun rgbFunction_parsesIntegerChannels() {
        val xml = """<svg viewBox="0 0 10 10"><rect width="5" height="5" fill="rgb(255, 128, 0)"/></svg>"""
        val color = VectorImage.parse(xml).paths.first().fillColor
        assertNotNull(color)
        assertTrue(kotlin.math.abs(color.red - 1f) < 0.001f)
        assertTrue(kotlin.math.abs(color.green - 128f / 255f) < 0.001f)
        assertTrue(kotlin.math.abs(color.blue) < 0.001f)
    }

    @Test
    fun rgbFunction_parsesPercentChannels() {
        val xml = """<svg viewBox="0 0 10 10"><rect width="5" height="5" fill="rgb(100%, 0%, 50%)"/></svg>"""
        val color = VectorImage.parse(xml).paths.first().fillColor
        assertNotNull(color)
        assertTrue(kotlin.math.abs(color.red - 1f) < 0.001f)
        assertTrue(kotlin.math.abs(color.green) < 0.001f)
        assertTrue(kotlin.math.abs(color.blue - 0.5f) < 0.001f)
    }

    @Test
    fun shortHexColor_expands() {
        val xml = """<svg viewBox="0 0 10 10"><rect width="5" height="5" fill="#f0a"/></svg>"""
        val color = VectorImage.parse(xml).paths.first().fillColor
        assertEquals(PdfColor.fromHex("#FF00AA"), color)
    }

    @Test
    fun eightDigitHex_carriesAlpha() {
        val xml = """<svg viewBox="0 0 10 10"><rect width="5" height="5" fill="#80FF0000"/></svg>"""
        val color = VectorImage.parse(xml).paths.first().fillColor
        assertNotNull(color)
        assertTrue(kotlin.math.abs(color.alpha - 128f / 255f) < 0.001f)
        assertTrue(kotlin.math.abs(color.red - 1f) < 0.001f)
    }

    // ---- viewBox handling ----------------------------------------------------

    @Test
    fun viewBoxOffset_shiftsCoordinates() {
        // viewBox min-x/min-y of (10,20) translates content by (-10,-20).
        val xml = """<svg viewBox="10 20 100 100"><rect x="10" y="20" width="5" height="5" fill="black"/></svg>"""
        val image = VectorImage.parse(xml)
        assertEquals(100f, image.viewportWidth)
        assertEquals(100f, image.viewportHeight)
        assertEquals(PathCommand.MoveTo(0f, 0f), image.paths.first().commands.first())
    }

    @Test
    fun pixelUnitsOnWidthHeight_areStripped() {
        val xml = """<svg width="48px" height="48px" viewBox="0 0 24 24"><rect width="1" height="1" fill="black"/></svg>"""
        val image = VectorImage.parse(xml)
        assertEquals(48f, image.intrinsicWidth)
        assertEquals(48f, image.intrinsicHeight)
        assertEquals(24f, image.viewportWidth)
    }

    @Test
    fun percentageWidthHeight_fallBackToViewBoxDims() {
        val xml = """<svg width="100%" height="100%" viewBox="0 0 32 16"><rect width="1" height="1" fill="black"/></svg>"""
        val image = VectorImage.parse(xml)
        assertEquals(32f, image.intrinsicWidth)
        assertEquals(16f, image.intrinsicHeight)
    }

    // ---- Graceful handling of ignored / unknown elements --------------------

    @Test
    fun defsTitleDescStyle_areIgnored() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <title>An icon</title>
              <desc>Just a square</desc>
              <defs><rect width="5" height="5" fill="red"/></defs>
              <style>.x { fill: red; }</style>
              <rect width="5" height="5" fill="black"/>
            </svg>
        """.trimIndent()
        val paths = VectorImage.parse(xml).paths
        // Only the top-level rect is drawn; the one inside <defs> is not.
        assertEquals(1, paths.size)
        assertEquals(PdfColor.Black, paths.first().fillColor)
    }

    @Test
    fun useElement_isSkipped() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <defs><rect id="r" width="5" height="5" fill="red"/></defs>
              <use href="#r"/>
              <rect width="5" height="5" fill="black"/>
            </svg>
        """.trimIndent()
        val paths = VectorImage.parse(xml).paths
        assertEquals(1, paths.size)
        assertEquals(PdfColor.Black, paths.first().fillColor)
    }

    @Test
    fun unknownWrapperElement_stillRendersItsChildren() {
        // <a> is an unknown drawable wrapper — its child shape should still render.
        val xml = """
            <svg viewBox="0 0 100 100">
              <a transform="translate(5,5)">
                <rect x="0" y="0" width="2" height="2" fill="black"/>
              </a>
            </svg>
        """.trimIndent()
        val paths = VectorImage.parse(xml).paths
        assertEquals(1, paths.size)
        assertEquals(PathCommand.MoveTo(5f, 5f), paths.first().commands.first())
    }

    @Test
    fun gradientFillByUrlReference_resolvesToGradientPaint() {
        val xml = """
            <svg viewBox="0 0 100 100">
              <defs>
                <linearGradient id="g" x1="0" y1="0" x2="100" y2="0">
                  <stop offset="0" stop-color="red"/>
                  <stop offset="1" stop-color="blue"/>
                </linearGradient>
              </defs>
              <rect width="100" height="100" fill="url(#g)"/>
            </svg>
        """.trimIndent()
        val fill = VectorImage.parse(xml).paths.first().fill
        assertTrue(fill is PdfPaint.LinearGradient)
    }

    // ---- Malformed input -----------------------------------------------------

    @Test
    fun malformedTransform_throwsVectorParseException() {
        val xml = """<svg viewBox="0 0 100 100"><rect width="5" height="5" transform="translate(" fill="black"/></svg>"""
        assertFailsWith<VectorParseException> { VectorImage.parse(xml) }
    }

    @Test
    fun mismatchedClosingTag_throwsVectorParseException() {
        val xml = """<svg viewBox="0 0 100 100"><g><rect width="5" height="5"/></svg>"""
        assertFailsWith<VectorParseException> { VectorImage.parse(xml) }
    }

    @Test
    fun malformedPathData_throwsVectorParseException() {
        val xml = """<svg viewBox="0 0 100 100"><path d="M0,0 L" fill="black"/></svg>"""
        assertFailsWith<VectorParseException> { VectorImage.parse(xml) }
    }

    @Test
    fun pathData_numberAfterClose_throwsInsteadOfHanging() {
        val xml = """<svg viewBox="0 0 10 10"><path d="M0,0 L1,1 Z5" fill="black"/></svg>"""
        assertFailsWith<VectorParseException> { VectorImage.parse(xml) }
    }

    @Test
    fun pathData_repeatedCloseCommands_parseNormally() {
        val xml = """<svg viewBox="0 0 10 10"><path d="M0,0 L1,1 Z Z" fill="black"/></svg>"""
        val commands = VectorImage.parse(xml).paths.single().commands
        assertEquals(2, commands.filterIsInstance<PathCommand.Close>().size)
    }

    @Test
    fun excessiveElementNesting_throwsVectorParseException() {
        val xml = "<svg viewBox=\"0 0 1 1\">" + "<g>".repeat(400) + "</g>".repeat(400) + "</svg>"
        assertFailsWith<VectorParseException> { VectorImage.parse(xml) }
    }

    @Test
    fun numericCharacterReference_decodesSupplementaryCodePointAsSurrogatePair() {
        val xml = "<svg viewBox=\"0 0 1 1\"><path d=\"M0,0\" data-note=\"&#x1F600;\" fill=\"black\"/></svg>"
        val root = MiniXml.parse(xml)
        val decoded = root.children.single().attributes.getValue("data-note")
        // Compare surrogate halves directly — String.codePointAt is a
        // JVM-only API and this test also compiles for iOS/wasm.
        assertEquals(2, decoded.length)
        assertEquals('\uD83D', decoded[0])
        assertEquals('\uDE00', decoded[1])
    }

    @Test
    fun numericCharacterReference_surrogateOrNulCodePoint_isLeftAsRawText() {
        val xml = "<svg viewBox=\"0 0 1 1\"><path d=\"M0,0\" data-note=\"&#xD800;\" fill=\"black\"/></svg>"
        val root = MiniXml.parse(xml)
        val decoded = root.children.single().attributes.getValue("data-note")
        assertEquals("&#xD800;", decoded)
    }
}
