package com.conamobile.pdfkmp.ttf

/**
 * Parses a TrueType / SFNT font [ByteArray] into the [TtfFont] model the
 * pure-Kotlin PDF backend uses to measure, embed, and subset glyph-based text.
 *
 * The parser reads exactly the tables the backend needs:
 *
 * - **table directory** — locates every other table by tag.
 * - **head** — `unitsPerEm` (the design grid) and `indexToLocFormat` (whether
 *   `loca` uses 2- or 4-byte offsets), plus the font bounding box.
 * - **maxp** — `numGlyphs`, the bound on every glyph index.
 * - **hhea + hmtx** — per-glyph advance widths and left side bearings (the
 *   metrics layout measures against and the PDF `W` array reports).
 * - **cmap** — Unicode → glyph id, preferring a format-12 segmented map (full
 *   Unicode, incl. astral planes) and otherwise a format-4 BMP map.
 * - **loca + glyf** — glyph outline data; kept raw so [TtfSubsetter] can resolve
 *   composite-glyph component closures and rewrite a minimal subset.
 * - **OS/2 + post** — vertical metrics and the italic flag for the PDF
 *   `FontDescriptor`; both optional, with hhea / macStyle fallbacks.
 * - **name** — the PostScript name, used to label the embedded font.
 *
 * Only TrueType-outline (`glyf`) fonts are fully supported; a CFF/OpenType-PS
 * font parses far enough to measure but yields an empty `glyf` (the subsetter
 * declines it). The parser is defensive — a missing optional table degrades to
 * a sensible default rather than throwing — but a structurally broken required
 * table (no `head`, `cmap`, `hmtx`, `maxp`, or `glyf`/`loca`) throws
 * [IllegalArgumentException] so the caller can fall back cleanly.
 */
internal object TtfParser {

    /** Sentinel offset for the OpenType-CFF wrapper, which carries no `glyf`. */
    private const val OTTO_TAG = "OTTO"

    fun parse(fontData: ByteArray): TtfFont {
        require(fontData.size >= 12) { "Font data too small to be a valid SFNT font" }
        val reader = TtfReader(fontData)

        val sfntVersion = reader.readTag()
        // A TrueType-collection (ttcf) carries several fonts; the backend only
        // handles single-font files, which keeps offsets absolute and simple.
        require(sfntVersion != "ttcf") { "TrueType collections (.ttc) are not supported; supply a single-font TTF/OTF" }

        val numTables = reader.readUInt16()
        reader.skip(6) // searchRange, entrySelector, rangeShift — derived, unused.

        val tables = HashMap<String, TtfTable>(numTables)
        repeat(numTables) {
            val tag = reader.readTag()
            reader.skip(4) // checksum — recomputed by the subsetter when needed.
            val offset = reader.readUInt32().toInt()
            val length = reader.readUInt32().toInt()
            tables[tag] = TtfTable(tag, offset, length)
        }

        val head = tables["head"] ?: error("Missing required 'head' table")
        val unitsPerEm = reader.uint16At(head.offset + 18)
        val xMin = signed16(reader.uint16At(head.offset + 36))
        val yMin = signed16(reader.uint16At(head.offset + 38))
        val xMax = signed16(reader.uint16At(head.offset + 40))
        val yMax = signed16(reader.uint16At(head.offset + 42))
        val macStyle = reader.uint16At(head.offset + 44)
        val indexToLocFormat = reader.uint16At(head.offset + 50)

        val maxp = tables["maxp"] ?: error("Missing required 'maxp' table")
        val numGlyphs = reader.uint16At(maxp.offset + 4)

        val (advances, lsbs) = parseHmtx(reader, tables, numGlyphs)
        val cmap = parseCmap(reader, tables)

        // glyf/loca are optional for CFF outlines; the subsetter checks for empties.
        val hasOutlines = sfntVersion != OTTO_TAG && tables.containsKey("glyf") && tables.containsKey("loca")
        val loca = if (hasOutlines) parseLoca(reader, tables.getValue("loca"), numGlyphs, indexToLocFormat) else IntArray(0)
        val glyf = if (hasOutlines) sliceTable(fontData, tables.getValue("glyf")) else ByteArray(0)

        val vertical = parseVerticalMetrics(reader, tables, yMax, yMin)
        val ascent = vertical.ascent
        val descent = vertical.descent
        val capHeight = vertical.capHeight
        val italic = vertical.italic || (macStyle and 0x0002) != 0
        val postScriptName = parseName(reader, tables["name"])

        return TtfFont(
            data = fontData,
            tables = tables,
            unitsPerEm = if (unitsPerEm == 0) 1000 else unitsPerEm,
            numGlyphs = numGlyphs,
            indexToLocFormat = indexToLocFormat,
            cmap = cmap,
            advanceWidths = advances,
            leftSideBearings = lsbs,
            loca = loca,
            glyf = glyf,
            bbox = intArrayOf(xMin, yMin, xMax, yMax),
            ascent = ascent,
            descent = descent,
            capHeight = capHeight,
            italic = italic,
            postScriptName = postScriptName,
        )
    }

    private fun signed16(v: Int): Int = if (v >= 0x8000) v - 0x10000 else v

    private fun sliceTable(data: ByteArray, table: TtfTable): ByteArray =
        data.copyOfRange(table.offset, table.offset + table.length)

    // -- hmtx -------------------------------------------------------------

    /**
     * Reads per-glyph advance widths and left side bearings from `hmtx`. Only
     * the first `numberOfHMetrics` glyphs carry an explicit advance; every glyph
     * after that reuses the *last* advance (monospaced-tail compression that
     * TrueType uses for runs of equal-width glyphs) and carries only its own lsb.
     */
    private fun parseHmtx(reader: TtfReader, tables: Map<String, TtfTable>, numGlyphs: Int): Pair<IntArray, IntArray> {
        val hhea = tables["hhea"] ?: error("Missing required 'hhea' table")
        val hmtx = tables["hmtx"] ?: error("Missing required 'hmtx' table")
        val numberOfHMetrics = reader.uint16At(hhea.offset + 34)

        val advances = IntArray(numGlyphs)
        val lsbs = IntArray(numGlyphs)
        var pos = hmtx.offset
        var lastAdvance = 0
        for (i in 0 until numGlyphs) {
            if (i < numberOfHMetrics) {
                lastAdvance = reader.uint16At(pos)
                val lsb = signed16(reader.uint16At(pos + 2))
                advances[i] = lastAdvance
                lsbs[i] = lsb
                pos += 4
            } else {
                advances[i] = lastAdvance
                // Trailing block holds lsb-only int16 entries.
                lsbs[i] = signed16(reader.uint16At(pos))
                pos += 2
            }
        }
        return advances to lsbs
    }

    // -- cmap -------------------------------------------------------------

    /**
     * Builds the Unicode → glyph map. The cmap table holds several encoding
     * subtables; the backend wants the broadest Unicode coverage, so it prefers
     * (3,10) or (0,6) format-12 segmented maps (full Unicode incl. supplementary
     * planes), then (3,1) or (0,3) format-4 BMP maps. A font with neither yields
     * an empty map (text through it can't be glyph-mapped — the caller falls back
     * to `.notdef` / replacement).
     */
    private fun parseCmap(reader: TtfReader, tables: Map<String, TtfTable>): Map<Int, Int> {
        val cmap = tables["cmap"] ?: error("Missing required 'cmap' table")
        val base = cmap.offset
        val numSubtables = reader.uint16At(base + 2)

        var bestFormat4: Int = -1
        var bestFormat12: Int = -1
        var pos = base + 4
        repeat(numSubtables) {
            val platformId = reader.uint16At(pos)
            val encodingId = reader.uint16At(pos + 2)
            val subtableOffset = base + reader.uint32At(pos + 4).toInt()
            pos += 8

            val format = reader.uint16At(subtableOffset)
            val unicode = (platformId == 3 && (encodingId == 1 || encodingId == 10)) ||
                (platformId == 0)
            if (!unicode) return@repeat
            when (format) {
                12 -> if (bestFormat12 < 0) bestFormat12 = subtableOffset
                4 -> if (bestFormat4 < 0) bestFormat4 = subtableOffset
            }
        }

        return when {
            bestFormat12 >= 0 -> parseCmapFormat12(reader, bestFormat12)
            bestFormat4 >= 0 -> parseCmapFormat4(reader, bestFormat4)
            else -> emptyMap()
        }
    }

    /**
     * Parses a format-4 (segment-mapping, BMP) cmap subtable. Each segment maps
     * a contiguous `[startCode, endCode]` range either by a flat `idDelta`
     * (code + delta = glyph) or indirectly through the `glyphIdArray` via
     * `idRangeOffset` — the classic, fiddly TrueType indirection that the obscure
     * offset arithmetic below implements exactly per the spec.
     */
    private fun parseCmapFormat4(reader: TtfReader, offset: Int): Map<Int, Int> {
        val segCountX2 = reader.uint16At(offset + 6)
        val segCount = segCountX2 / 2
        val endCodesBase = offset + 14
        val startCodesBase = endCodesBase + segCountX2 + 2 // +2 skips reservedPad.
        val idDeltasBase = startCodesBase + segCountX2
        val idRangeOffsetsBase = idDeltasBase + segCountX2

        val map = HashMap<Int, Int>()
        for (seg in 0 until segCount) {
            val endCode = reader.uint16At(endCodesBase + seg * 2)
            val startCode = reader.uint16At(startCodesBase + seg * 2)
            val idDelta = reader.uint16At(idDeltasBase + seg * 2)
            val idRangeOffset = reader.uint16At(idRangeOffsetsBase + seg * 2)

            for (code in startCode..endCode) {
                if (code == 0xFFFF) continue // segment terminator sentinel.
                val glyph: Int = if (idRangeOffset == 0) {
                    (code + idDelta) and 0xFFFF
                } else {
                    // The spec's pointer trick: index into glyphIdArray relative
                    // to the idRangeOffset slot's own address.
                    val glyphIndexAddr =
                        idRangeOffsetsBase + seg * 2 + idRangeOffset + (code - startCode) * 2
                    val gid = reader.uint16At(glyphIndexAddr)
                    if (gid == 0) 0 else (gid + idDelta) and 0xFFFF
                }
                if (glyph != 0) map[code] = glyph
            }
        }
        return map
    }

    /**
     * Parses a format-12 (segmented coverage) cmap subtable: groups of
     * `[startCharCode, endCharCode] → startGlyphID` covering the full Unicode
     * range, including code points above the BMP. Used for fonts with emoji /
     * CJK extension coverage.
     */
    private fun parseCmapFormat12(reader: TtfReader, offset: Int): Map<Int, Int> {
        val numGroups = reader.uint32At(offset + 12).toInt()
        val map = HashMap<Int, Int>()
        var pos = offset + 16
        repeat(numGroups) {
            val startCharCode = reader.uint32At(pos).toInt()
            val endCharCode = reader.uint32At(pos + 4).toInt()
            val startGlyphId = reader.uint32At(pos + 8).toInt()
            pos += 12
            // Guard against a corrupt group claiming an enormous range.
            val span = endCharCode - startCharCode
            if (span in 0..0x10FFFF) {
                for (i in 0..span) {
                    val gid = startGlyphId + i
                    if (gid != 0) map[startCharCode + i] = gid
                }
            }
        }
        return map
    }

    // -- loca -------------------------------------------------------------

    /**
     * Reads the `loca` index, which holds `numGlyphs + 1` offsets into `glyf`
     * (glyph `g` occupies `[loca[g], loca[g+1])`). The short format stores
     * offsets divided by two; the long format stores them directly.
     */
    private fun parseLoca(reader: TtfReader, loca: TtfTable, numGlyphs: Int, indexToLocFormat: Int): IntArray {
        val count = numGlyphs + 1
        val out = IntArray(count)
        if (indexToLocFormat == 0) {
            for (i in 0 until count) out[i] = reader.uint16At(loca.offset + i * 2) * 2
        } else {
            for (i in 0 until count) out[i] = reader.uint32At(loca.offset + i * 4).toInt()
        }
        return out
    }

    // -- OS/2 + hhea vertical metrics -------------------------------------

    private class VerticalMetrics(
        val ascent: Int,
        val descent: Int,
        val capHeight: Int,
        val italic: Boolean,
    )

    /**
     * Resolves ascent / descent / cap height for the PDF `FontDescriptor`.
     * Prefers the OS/2 typographic metrics (sTypoAscender / sTypoDescender /
     * sCapHeight) which are the values most viewers expect, and falls back to the
     * `hhea` ascender / descender when OS/2 is absent. Descent is normalised to a
     * positive magnitude. The OS/2 `fsSelection` italic bit (1) is reported too.
     */
    private fun parseVerticalMetrics(
        reader: TtfReader,
        tables: Map<String, TtfTable>,
        yMax: Int,
        yMin: Int,
    ): VerticalMetrics {
        val os2 = tables["OS/2"]
        if (os2 != null && os2.length >= 70) {
            val version = reader.uint16At(os2.offset)
            val typoAscender = signed16(reader.uint16At(os2.offset + 68))
            val typoDescender = signed16(reader.uint16At(os2.offset + 70))
            val fsSelection = reader.uint16At(os2.offset + 62)
            val italic = (fsSelection and 0x0001) != 0
            // sCapHeight lives at +88 and only exists from OS/2 version 2 on.
            val capHeight = if (version >= 2 && os2.length >= 90) signed16(reader.uint16At(os2.offset + 88)) else 0
            val ascent = if (typoAscender != 0) typoAscender else yMax
            val descent = if (typoDescender != 0) -typoDescender else -yMin
            return VerticalMetrics(ascent, kotlin.math.abs(descent), capHeight, italic)
        }

        val hhea = tables["hhea"]
        if (hhea != null) {
            val ascender = signed16(reader.uint16At(hhea.offset + 4))
            val descender = signed16(reader.uint16At(hhea.offset + 6))
            return VerticalMetrics(ascender, kotlin.math.abs(descender), 0, false)
        }
        return VerticalMetrics(yMax, kotlin.math.abs(yMin), 0, false)
    }

    // -- name -------------------------------------------------------------

    /**
     * Reads the PostScript name (name id 6) from the `name` table, preferring a
     * Windows/Unicode (platform 3) record and decoding its UTF-16BE bytes, then
     * any Macintosh (platform 1) record as Latin-1. Returns `null` when no
     * usable record exists — the embedder then synthesises a name.
     */
    private fun parseName(reader: TtfReader, name: TtfTable?): String? {
        if (name == null) return null
        val base = name.offset
        val count = reader.uint16At(base + 2)
        val stringOffset = base + reader.uint16At(base + 4)

        var macName: String? = null
        var pos = base + 6
        repeat(count) {
            val platformId = reader.uint16At(pos)
            val nameId = reader.uint16At(pos + 6)
            val length = reader.uint16At(pos + 8)
            val recordOffset = stringOffset + reader.uint16At(pos + 10)
            pos += 12
            if (nameId != 6) return@repeat
            when (platformId) {
                3, 0 -> return decodeUtf16Be(reader, recordOffset, length)
                1 -> if (macName == null) macName = decodeLatin1(reader, recordOffset, length)
            }
        }
        return macName
    }

    private fun decodeUtf16Be(reader: TtfReader, offset: Int, length: Int): String {
        val sb = StringBuilder(length / 2)
        var i = 0
        while (i + 1 < length) {
            val code = reader.uint16At(offset + i)
            sb.append(code.toChar())
            i += 2
        }
        return sb.toString()
    }

    private fun decodeLatin1(reader: TtfReader, offset: Int, length: Int): String {
        val sb = StringBuilder(length)
        reader.seek(offset)
        repeat(length) { sb.append((reader.readUInt8()).toChar()) }
        return sb.toString()
    }
}
