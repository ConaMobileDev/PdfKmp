package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.ttf.TtfFont

/**
 * A document-scoped embedded TrueType font: the bridge between a parsed
 * [TtfFont] and the Type0 / CIDFontType2 object set the assembler writes.
 *
 * One instance backs one font face for the whole document. As text is measured
 * and drawn, [use] records every (code point → glyph id) pair the document
 * actually shows; at finish the assembler asks for the subset glyph set, the per
 * glyph widths, and the ToUnicode mapping, all keyed by the *original* glyph id
 * (the embedding uses `/CIDToGIDMap /Identity`, so CID == GID == glyph id — see
 * [TtfSubsetter] for why that numbering is preserved).
 *
 * The content stream addresses glyphs by their two-byte glyph id under
 * Identity-H, so [encodeGlyphs] turns a string into the big-endian glyph-id byte
 * sequence the `Tj` operator needs.
 */
internal class KmpEmbeddedFont(
    /** The parsed source font. */
    val font: TtfFont,
    /** The PostScript-style base name written into the font dictionaries. */
    val baseName: String,
) {

    /** Original glyph ids the document has shown, in first-use order for stable subsets. */
    private val usedGlyphs = LinkedHashSet<Int>()

    /** code point → glyph id for every shown character, feeding the ToUnicode CMap. */
    private val codePointToGlyph = HashMap<Int, Int>()

    /** `true` when [codePoint] has a real glyph in this font. */
    fun covers(codePoint: Int): Boolean = font.covers(codePoint)

    /**
     * Records that [codePoint] is shown by this font and returns its glyph id.
     * A code point the font lacks resolves to glyph 0 (`.notdef`); the caller
     * decides whether to fall back elsewhere before calling, so this still tracks
     * it (a `.notdef` box is a legitimate, if ugly, rendering).
     */
    fun use(codePoint: Int): Int {
        val gid = font.glyphForCodePoint(codePoint)
        usedGlyphs.add(gid)
        codePointToGlyph[codePoint] = gid
        return gid
    }

    /** Glyph id for [codePoint] without recording usage (for measurement). */
    fun glyphFor(codePoint: Int): Int = font.glyphForCodePoint(codePoint)

    /** Advance of [glyphId] in PDF glyph-space (1/1000 em). */
    fun advanceThousandths(glyphId: Int): Int {
        val units = font.advanceOf(glyphId)
        return (units * 1000) / font.unitsPerEm
    }

    /** Ascent in PDF glyph-space (1/1000 em). */
    val ascentThousandths: Int get() = (font.ascent * 1000) / font.unitsPerEm

    /** Descent magnitude in PDF glyph-space (1/1000 em), positive-down. */
    val descentThousandths: Int get() = (font.descent * 1000) / font.unitsPerEm

    /** The set of glyph ids to embed (every used glyph plus their composite closure). */
    fun subsetGlyphIds(): Set<Int> = usedGlyphs.toSet()

    /** Snapshot of the code-point → glyph-id map for the ToUnicode CMap. */
    fun codePointMappings(): Map<Int, Int> = codePointToGlyph.toMap()
}
