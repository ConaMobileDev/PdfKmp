package com.conamobile.pdfkmp.ttf

/**
 * A parsed TrueType font: the slices and tables [TtfParser] needs to measure
 * text, build PDF font dictionaries, and drive [TtfSubsetter].
 *
 * Only the data the pure-Kotlin PDF backend actually consumes is retained:
 * the original table bytes (so the subsetter can copy `prep`/`fpgm`/`cvt `
 * verbatim), the unitsPerEm scale every advance and metric is expressed in,
 * the character-to-glyph cmap, per-glyph advance widths, and the raw `glyf` /
 * `loca` data the subsetter rewrites. The vertical metrics + bounding box feed
 * the PDF `FontDescriptor`.
 *
 * All metric fields are in font design units (font-space); divide by
 * [unitsPerEm] and multiply by the point size to get points. A PDF glyph-space
 * value (1/1000 em) is `metric * 1000 / unitsPerEm`.
 */
internal class TtfFont(
    /** The original, complete font bytes — the subsetter copies tables out of these. */
    val data: ByteArray,
    /** Table directory: tag → (offset, length) into [data]. */
    val tables: Map<String, TtfTable>,
    /** Design-unit grid size; advances and bbox are in these units. */
    val unitsPerEm: Int,
    /** Number of glyphs (`maxp.numGlyphs`). */
    val numGlyphs: Int,
    /** `head.indexToLocFormat`: 0 = short `loca` (offset/2), 1 = long `loca`. */
    val indexToLocFormat: Int,
    /** Unicode code point → glyph id, built from the cmap. */
    val cmap: Map<Int, Int>,
    /** Per-glyph advance width in font units, indexed by glyph id. */
    val advanceWidths: IntArray,
    /** Per-glyph left side bearing in font units, indexed by glyph id. */
    val leftSideBearings: IntArray,
    /** `loca` offsets into the `glyf` table, length numGlyphs + 1. */
    val loca: IntArray,
    /** The raw `glyf` table bytes, or an empty array for CFF/empty fonts. */
    val glyf: ByteArray,
    /** Font bounding box in font units: [xMin, yMin, xMax, yMax] from `head`. */
    val bbox: IntArray,
    /** Ascent in font units (OS/2 typo ascender, falling back to hhea). */
    val ascent: Int,
    /** Descent in font units, positive-down (OS/2 typo descender magnitude). */
    val descent: Int,
    /** Cap height in font units (OS/2 sCapHeight), or 0 when the table omits it. */
    val capHeight: Int,
    /** OS/2 fsSelection / macStyle italic flag, used for the PDF /Flags italic bit. */
    val italic: Boolean,
    /** PostScript name from the `name` table, or `null` if absent. */
    val postScriptName: String?,
) {
    /**
     * The glyph id for [codePoint], or `0` (the `.notdef` glyph) when the font
     * has no glyph for it. Callers treat `0` as "not covered".
     */
    fun glyphForCodePoint(codePoint: Int): Int = cmap[codePoint] ?: 0

    /** `true` when the font has a real (non-`.notdef`) glyph for [codePoint]. */
    fun covers(codePoint: Int): Boolean = (cmap[codePoint] ?: 0) != 0

    /** Advance width of [glyphId] in font units, clamped to the table bounds. */
    fun advanceOf(glyphId: Int): Int =
        if (glyphId in advanceWidths.indices) advanceWidths[glyphId] else 0

    /** Byte range of glyph [glyphId] within [glyf]; empty (start == end) for blank glyphs. */
    fun glyphRange(glyphId: Int): IntRange {
        if (glyphId < 0 || glyphId + 1 >= loca.size) return IntRange.EMPTY
        return loca[glyphId] until loca[glyphId + 1]
    }
}

/** A single SFNT table directory entry: where the table lives in the font bytes. */
internal class TtfTable(val tag: String, val offset: Int, val length: Int)
