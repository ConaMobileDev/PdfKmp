package com.conamobile.pdfkmp.ttf

import com.conamobile.pdfkmp.font.BundledFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Subsetter correctness: subset the bundled Inter to a small character set, then
 * re-parse the produced bytes *with the same parser* and assert the kept glyphs
 * survived intact with their original widths.
 *
 * Re-parsing is the strongest practical check short of rasterising — it proves
 * the rebuilt `glyf`/`loca`/`cmap`/`head`/checksum tables form a structurally
 * valid SFNT that a strict reader (PdfBox, every viewer) will accept, and that
 * the original glyph numbering was preserved as the Identity-H embedding needs.
 */
class TtfSubsetterTest {

    private val inter: TtfFont = TtfParser.parse(BundledFonts.interRegular)

    @Test
    fun subsetKeepsRequestedGlyphsAndWidths() {
        val chars = listOf('H', 'e', 'l', 'o', 'я')
        val gidByChar = chars.associateWith { inter.glyphForCodePoint(it.code) }
        for ((ch, gid) in gidByChar) {
            assertTrue(gid > 0, "source font lacks a glyph for '$ch'")
        }

        val subsetBytes = TtfSubsetter.subset(inter, gidByChar.values.toSet())
        val subset = TtfParser.parse(subsetBytes)

        // The subset preserves the original glyph numbering (CID == GID layout):
        // the same glyph ids index the same glyphs, with the same advances.
        for ((ch, gid) in gidByChar) {
            assertEquals(
                inter.advanceOf(gid),
                subset.advanceOf(gid),
                "advance changed for '$ch' (gid $gid)",
            )
            // The kept glyph must have non-empty outline data in the subset
            // (unless the source glyph was itself blank, e.g. space).
            val srcRange = inter.glyphRange(gid)
            val subRange = subset.glyphRange(gid)
            if (!srcRange.isEmpty()) {
                assertTrue(!subRange.isEmpty(), "outline for '$ch' (gid $gid) was dropped from the subset")
            }
        }
    }

    @Test
    fun subsetGlyfIsSmallerThanOriginal() {
        val gids = listOf('H', 'i').map { inter.glyphForCodePoint(it.code) }.toSet()
        val subsetBytes = TtfSubsetter.subset(inter, gids)
        val subset = TtfParser.parse(subsetBytes)
        // Only a handful of glyphs carry data, so the subset glyf must be far
        // smaller than the source's full glyf table.
        assertTrue(
            subset.glyf.size < inter.glyf.size / 2,
            "subset glyf (${subset.glyf.size}) is not meaningfully smaller than source (${inter.glyf.size})",
        )
    }

    @Test
    fun subsetUsesLongLocaFormat() {
        val gids = setOf(inter.glyphForCodePoint('A'.code))
        val subset = TtfParser.parse(TtfSubsetter.subset(inter, gids))
        assertEquals(1, subset.indexToLocFormat, "subset should force the long (4-byte) loca format")
    }

    @Test
    fun compositeComponentsAreIncludedInClosure() {
        // Pick an accented composite letter and verify its component glyphs land
        // in the subset's glyf data, not just the seed glyph.
        val accents = listOf('é', 'à', 'ñ', 'ö', 'ç')
        for (ch in accents) {
            val gid = inter.glyphForCodePoint(ch.code)
            if (gid == 0) continue
            val closure = TtfSubsetter.glyphClosure(inter, setOf(gid))
            if (closure.size <= 2) continue // not a composite — try the next.

            val subset = TtfParser.parse(TtfSubsetter.subset(inter, setOf(gid)))
            for (component in closure) {
                if (component == 0) continue
                val srcRange = inter.glyphRange(component)
                if (srcRange.isEmpty()) continue
                assertTrue(
                    !subset.glyphRange(component).isEmpty(),
                    "composite component glyph $component of '$ch' was dropped from the subset",
                )
            }
            return // one composite proven is enough.
        }
    }

    @Test
    fun glyphZeroIsAlwaysKept() {
        val gids = setOf(inter.glyphForCodePoint('Z'.code))
        val closure = TtfSubsetter.glyphClosure(inter, gids)
        assertTrue(0 in closure, ".notdef (glyph 0) must always be in the subset")
    }
}
