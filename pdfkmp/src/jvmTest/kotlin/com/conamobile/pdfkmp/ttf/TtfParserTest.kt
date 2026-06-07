package com.conamobile.pdfkmp.ttf

import com.conamobile.pdfkmp.font.BundledFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parser correctness against the bundled Inter Regular face — the same font the
 * pure-Kotlin backend embeds as its Unicode fallback, so a parse bug here would
 * surface as broken embedded text.
 *
 * The assertions pin the structural invariants the embedder and subsetter rely
 * on: a sane `unitsPerEm`, glyph coverage for both a WinAnsi letter (`A`) and a
 * Cyrillic letter (`я`, proving the cmap reaches beyond Latin-1), monotonic
 * `loca`, and at least one composite glyph whose component closure is non-empty.
 */
class TtfParserTest {

    private val inter: TtfFont = TtfParser.parse(BundledFonts.interRegular)

    @Test
    fun parsesHeadAndMaxp() {
        // Inter is drawn on a 2048-unit em (the common TrueType grid); any
        // non-zero power-of-two-ish value is acceptable, but it must be sane.
        assertTrue(inter.unitsPerEm in 16..16384, "unexpected unitsPerEm ${inter.unitsPerEm}")
        assertTrue(inter.numGlyphs > 100, "Inter should have many glyphs, got ${inter.numGlyphs}")
        assertEquals(inter.numGlyphs + 1, inter.loca.size, "loca must hold numGlyphs+1 offsets")
    }

    @Test
    fun cmapLooksUpLatinAndCyrillic() {
        val gidA = inter.glyphForCodePoint('A'.code)
        assertTrue(gidA > 0, "no glyph for 'A'")

        val gidCyrillic = inter.glyphForCodePoint('я'.code)
        assertTrue(gidCyrillic > 0, "no glyph for Cyrillic 'я' (cmap not reaching beyond Latin-1)")

        // 'A' and 'я' must resolve to distinct glyphs.
        assertTrue(gidA != gidCyrillic, "'A' and 'я' collided on one glyph id")
    }

    @Test
    fun advanceWidthsArePositiveForRealGlyphs() {
        val gidA = inter.glyphForCodePoint('A'.code)
        assertTrue(inter.advanceOf(gidA) > 0, "'A' has a non-positive advance")
        // Space has an advance even though its glyph is blank.
        val gidSpace = inter.glyphForCodePoint(' '.code)
        assertTrue(inter.advanceOf(gidSpace) > 0, "space has a non-positive advance")
    }

    @Test
    fun locaIsMonotonic() {
        for (i in 0 until inter.numGlyphs) {
            assertTrue(inter.loca[i] <= inter.loca[i + 1], "loca not monotonic at glyph $i")
        }
        assertEquals(inter.glyf.size, inter.loca[inter.numGlyphs], "loca end must equal glyf length")
    }

    @Test
    fun resolvesAtLeastOneCompositeGlyph() {
        // Accented letters (é, à, ñ, …) are usually composites in TrueType fonts.
        // Find at least one glyph whose closure pulls in extra components.
        val accents = listOf('é', 'à', 'ñ', 'ö', 'ü', 'ç', 'â', 'ê')
        var foundComposite = false
        for (ch in accents) {
            val gid = inter.glyphForCodePoint(ch.code)
            if (gid == 0) continue
            val closure = TtfSubsetter.glyphClosure(inter, setOf(gid))
            // A composite glyph's closure is its own id plus its components (and 0).
            if (closure.size > 2) {
                foundComposite = true
                assertTrue(gid in closure, "closure dropped the seed glyph")
                break
            }
        }
        assertTrue(foundComposite, "no composite glyph found among accented letters")
    }

    @Test
    fun verticalMetricsArePresent() {
        assertTrue(inter.ascent > 0, "ascent not positive")
        assertTrue(inter.descent > 0, "descent magnitude not positive")
    }

    @Test
    fun postScriptNameIsReadable() {
        val name = inter.postScriptName
        assertNotNull(name, "Inter should carry a PostScript name")
        assertTrue(name.contains("Inter", ignoreCase = true), "unexpected PostScript name '$name'")
    }
}
