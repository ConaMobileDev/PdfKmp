package com.conamobile.pdfkmp.ttf

/**
 * Produces a minimal TrueType subset of a [TtfFont] covering a given set of
 * glyph ids, for embedding as a PDF `FontFile2`.
 *
 * ## CID / glyph-numbering layout choice
 *
 * The subset **preserves the original glyph numbering** — glyph id `g` in the
 * source stays glyph id `g` in the subset. Glyphs outside the requested closure
 * are emptied (their `loca` start == end, i.e. blank), so the `glyf` table only
 * carries bytes for the glyphs actually used while the index space is unchanged.
 *
 * This is the deliberate counterpart to embedding the font in the PDF with
 * `/CIDToGIDMap /Identity`: because CID == GID == the original glyph id, the
 * Type0 content stream writes original glyph ids directly, the `W` width array is
 * keyed by original glyph id, and — most importantly — **composite glyphs need
 * no component renumbering**, since their child-glyph references still point at
 * the same indices. The cost is a full-length `loca` (a few KB of mostly-equal
 * offsets), which compresses to almost nothing under FlateDecode; the win is a
 * dramatically simpler, less error-prone rewrite than glyph compaction.
 *
 * ## What the subset contains
 *
 * - `glyf` — concatenated outline data for the closure glyphs (composite
 *   components pulled in transitively), each padded to a 2-byte boundary.
 * - `loca` — rebuilt in the *long* (4-byte) format for all glyphs.
 * - `head` / `maxp` / `hhea` / `hmtx` — copied with `indexToLocFormat` forced to
 *   long and `checkSumAdjustment` zeroed (recomputed by [withCheckSums]).
 * - `cmap` — a fresh format-4 BMP map over the subset's BMP code points (present
 *   so the file is a standalone valid TTF; the PDF embeds Identity-H regardless).
 * - `prep` / `fpgm` / `cvt ` — hinting tables copied verbatim if present.
 *
 * Tables are written in ascending-tag order with corrected per-table checksums
 * and a corrected whole-font `checkSumAdjustment`, so the result is a valid SFNT
 * file that re-parses through [TtfParser].
 */
internal object TtfSubsetter {

    /** Tables copied verbatim when present (hinting + control-value programs). */
    private val PASSTHROUGH_TABLES = listOf("cvt ", "fpgm", "prep")

    /**
     * Computes the closure of [seedGlyphIds] under composite-component references
     * and returns the set of every glyph id the subset must contain. Glyph `0`
     * (`.notdef`) is always included so the font has a defined fallback.
     */
    fun glyphClosure(font: TtfFont, seedGlyphIds: Set<Int>): Set<Int> {
        val closure = HashSet<Int>()
        val pending = ArrayDeque<Int>()
        fun enqueue(g: Int) {
            if (g in 0 until font.numGlyphs && closure.add(g)) pending.addLast(g)
        }
        enqueue(0)
        for (g in seedGlyphIds) enqueue(g)
        while (pending.isNotEmpty()) {
            val g = pending.removeFirst()
            for (component in compositeComponents(font, g)) enqueue(component)
        }
        return closure
    }

    /**
     * Builds the subset font bytes covering [glyphIds] (plus their composite
     * closure). The result is a complete, self-consistent TTF.
     */
    fun subset(font: TtfFont, glyphIds: Set<Int>): ByteArray {
        require(font.glyf.isNotEmpty()) { "Cannot subset a font without a 'glyf' table (CFF/OTF outlines are unsupported)" }
        val keep = glyphClosure(font, glyphIds)

        val newGlyf = buildGlyf(font, keep)
        val newLoca = buildLoca(newGlyf.offsets)
        val newHead = buildHead(font)
        val newMaxp = copyTable(font, "maxp")
        val newHhea = copyTable(font, "hhea")
        val newHmtx = copyTable(font, "hmtx")
        val newCmap = buildCmap(font, keep)

        val tables = LinkedHashMap<String, ByteArray>()
        tables["cmap"] = newCmap
        tables["glyf"] = newGlyf.bytes
        tables["head"] = newHead
        tables["hhea"] = newHhea
        tables["hmtx"] = newHmtx
        tables["loca"] = newLoca
        tables["maxp"] = newMaxp
        for (tag in PASSTHROUGH_TABLES) {
            font.tables[tag]?.let { tables[tag] = sliceTable(font, it) }
        }

        return assembleSfnt(tables)
    }

    // -- Glyph data -------------------------------------------------------

    /** The rebuilt `glyf` table plus the running per-glyph offsets used to derive `loca`. */
    private class GlyfResult(val bytes: ByteArray, val offsets: IntArray)

    /**
     * Concatenates the kept glyphs' outline data into a new `glyf` table. Glyphs
     * not in [keep] contribute a zero-length entry (blank glyph). Each glyph's
     * bytes are padded to a 2-byte boundary, the alignment a TrueType `glyf`
     * table requires. `offsets[g]` is glyph `g`'s start; `offsets[numGlyphs]` is
     * the total length, exactly what `loca` needs.
     */
    private fun buildGlyf(font: TtfFont, keep: Set<Int>): GlyfResult {
        val numGlyphs = font.numGlyphs
        val offsets = IntArray(numGlyphs + 1)
        val out = GrowableBytes()
        for (g in 0 until numGlyphs) {
            offsets[g] = out.size
            if (g in keep) {
                val range = font.glyphRange(g)
                if (!range.isEmpty()) {
                    out.append(font.glyf, range.first, range.last + 1)
                    // Pad to an even boundary so the next glyph stays 2-aligned.
                    if (out.size % 2 != 0) out.appendByte(0)
                }
            }
        }
        offsets[numGlyphs] = out.size
        return GlyfResult(out.toByteArray(), offsets)
    }

    /** Builds a long-format (4-byte) `loca` table from glyph [offsets]. */
    private fun buildLoca(offsets: IntArray): ByteArray {
        val out = GrowableBytes(offsets.size * 4)
        for (o in offsets) out.appendUInt32(o.toLong())
        return out.toByteArray()
    }

    // -- head -------------------------------------------------------------

    /**
     * Copies `head`, forcing `indexToLocFormat` (offset +50) to 1 (long `loca`)
     * to match [buildLoca], and zeroing `checkSumAdjustment` (offset +8) so
     * [assembleSfnt] can recompute it over the finished file.
     */
    private fun buildHead(font: TtfFont): ByteArray {
        val head = copyTable(font, "head")
        // checkSumAdjustment (uint32 at +8) — zero now, fix after layout.
        head[8] = 0; head[9] = 0; head[10] = 0; head[11] = 0
        // indexToLocFormat (int16 at +50) — force long.
        head[50] = 0; head[51] = 1
        return head
    }

    // -- cmap -------------------------------------------------------------

    /**
     * Builds a minimal format-4 cmap covering the BMP code points whose glyphs
     * survive in [keep]. The PDF embeds the font with Identity-H + a ToUnicode
     * CMap, so this cmap is not what the viewer uses to render PdfKmp's own text;
     * it exists only so the embedded `FontFile2` is a structurally valid,
     * independently-openable TTF. Supplementary-plane code points are omitted
     * from this convenience cmap (format 4 is BMP-only); they still render in the
     * PDF because Identity-H addresses glyphs directly.
     */
    private fun buildCmap(font: TtfFont, keep: Set<Int>): ByteArray {
        // Reverse the surviving subset's BMP coverage: code point → glyph id.
        val entries = ArrayList<Pair<Int, Int>>()
        for ((cp, gid) in font.cmap) {
            if (cp <= 0xFFFF && gid in keep) entries.add(cp to gid)
        }
        entries.sortBy { it.first }

        // Format-4 needs a terminating 0xFFFF segment mapped to glyph 0.
        // Group consecutive code points sharing a constant (gid - cp) delta into
        // one segment to keep the table compact.
        data class Segment(val start: Int, val end: Int, val delta: Int)
        val segments = ArrayList<Segment>()
        var i = 0
        while (i < entries.size) {
            val (startCp, startGid) = entries[i]
            val delta = (startGid - startCp) and 0xFFFF
            var endCp = startCp
            var j = i + 1
            while (j < entries.size) {
                val (cp, gid) = entries[j]
                if (cp == endCp + 1 && ((gid - cp) and 0xFFFF) == delta) {
                    endCp = cp
                    j++
                } else {
                    break
                }
            }
            segments.add(Segment(startCp, endCp, delta))
            i = j
        }
        // Terminator segment.
        val allSegments = segments + Segment(0xFFFF, 0xFFFF, 1)

        val segCount = allSegments.size
        val segX2 = segCount * 2
        val searchRange = 2 * largestPowerOfTwo(segCount)
        val entrySelector = log2Floor(searchRange / 2)
        val rangeShift = segX2 - searchRange

        val sub = GrowableBytes()
        sub.appendUInt16(4) // format
        // length placeholder (filled after).
        val lengthPos = sub.size
        sub.appendUInt16(0)
        sub.appendUInt16(0) // language
        sub.appendUInt16(segX2)
        sub.appendUInt16(searchRange)
        sub.appendUInt16(entrySelector)
        sub.appendUInt16(rangeShift)
        for (s in allSegments) sub.appendUInt16(s.end)
        sub.appendUInt16(0) // reservedPad
        for (s in allSegments) sub.appendUInt16(s.start)
        for (s in allSegments) sub.appendUInt16(s.delta and 0xFFFF)
        // idRangeOffset all zero: glyph = (code + idDelta) & 0xFFFF.
        for (s in allSegments) sub.appendUInt16(0)
        sub.setUInt16(lengthPos, sub.size)

        // cmap header: version 0, one subtable, platform 3 / encoding 1.
        val out = GrowableBytes()
        out.appendUInt16(0) // version
        out.appendUInt16(1) // numTables
        out.appendUInt16(3) // platformId (Windows)
        out.appendUInt16(1) // encodingId (Unicode BMP)
        out.appendUInt32(12L) // offset to subtable (after this 12-byte header)
        out.append(sub.toByteArray(), 0, sub.size)
        return out.toByteArray()
    }

    private fun largestPowerOfTwo(n: Int): Int {
        var p = 1
        while (p * 2 <= n) p *= 2
        return p
    }

    private fun log2Floor(n: Int): Int {
        var v = n
        var r = 0
        while (v > 1) { v = v shr 1; r++ }
        return r
    }

    // -- Composite components --------------------------------------------

    /**
     * Returns the component glyph ids referenced by glyph [glyphId] when it is a
     * composite (a glyph assembled from other glyphs, e.g. `é` = `e` + acute).
     * A simple glyph or a blank glyph returns an empty list.
     *
     * A composite glyph's data starts with a header whose `numberOfContours`
     * field is negative (`-1`); each component record then carries flags, the
     * component glyph index, and a variable-length argument/transform block that
     * must be skipped to reach the next record. The flag bits decoded here are
     * exactly those that change the record's byte length.
     */
    private fun compositeComponents(font: TtfFont, glyphId: Int): List<Int> {
        val range = font.glyphRange(glyphId)
        if (range.isEmpty() || range.last - range.first + 1 < 10) return emptyList()
        val reader = TtfReader(font.glyf)
        reader.seek(range.first)
        val numberOfContours = reader.readInt16()
        if (numberOfContours >= 0) return emptyList() // simple glyph

        reader.skip(8) // xMin, yMin, xMax, yMax bounding box.
        val components = ArrayList<Int>()
        while (true) {
            val flags = reader.readUInt16()
            val componentGlyph = reader.readUInt16()
            components.add(componentGlyph)

            // ARG_1_AND_2_ARE_WORDS (0x0001): args are int16 each, else int8 each.
            if (flags and 0x0001 != 0) reader.skip(4) else reader.skip(2)
            // WE_HAVE_A_SCALE (0x0008): one F2Dot14.
            // WE_HAVE_AN_X_AND_Y_SCALE (0x0040): two F2Dot14.
            // WE_HAVE_A_TWO_BY_TWO (0x0080): four F2Dot14.
            when {
                flags and 0x0008 != 0 -> reader.skip(2)
                flags and 0x0040 != 0 -> reader.skip(4)
                flags and 0x0080 != 0 -> reader.skip(8)
            }
            // MORE_COMPONENTS (0x0020): another record follows.
            if (flags and 0x0020 == 0) break
        }
        return components
    }

    // -- Table helpers ----------------------------------------------------

    private fun sliceTable(font: TtfFont, table: TtfTable): ByteArray =
        font.data.copyOfRange(table.offset, table.offset + table.length)

    /** A mutable copy of [tag]'s table bytes, or throws if the required table is absent. */
    private fun copyTable(font: TtfFont, tag: String): ByteArray {
        val table = font.tables[tag] ?: error("Missing required '$tag' table in source font")
        return font.data.copyOfRange(table.offset, table.offset + table.length)
    }

    // -- SFNT assembly ----------------------------------------------------

    /**
     * Lays out [tables] (already keyed by tag) into a finished SFNT file: the
     * offset table, a tag-sorted table directory with correct per-table
     * checksums and 4-byte-padded offsets/lengths, then the table bodies. After
     * layout the whole-font `checkSumAdjustment` is patched into `head`.
     */
    private fun assembleSfnt(tables: Map<String, ByteArray>): ByteArray {
        val sortedTags = tables.keys.sorted()
        val numTables = sortedTags.size

        // Offset table (12 bytes) + directory (16 bytes per table).
        val directorySize = 12 + numTables * 16
        var offset = directorySize
        val tableOffsets = HashMap<String, Int>()
        val paddedLengths = HashMap<String, Int>()
        for (tag in sortedTags) {
            tableOffsets[tag] = offset
            val len = tables.getValue(tag).size
            paddedLengths[tag] = (len + 3) and 3.inv()
            offset += paddedLengths.getValue(tag)
        }
        val totalSize = offset

        val out = GrowableBytes(totalSize)
        // Offset table: sfntVersion 0x00010000 (TrueType outlines).
        out.appendUInt32(0x00010000L)
        out.appendUInt16(numTables)
        val searchRange = largestPowerOfTwo(numTables) * 16
        out.appendUInt16(searchRange)
        out.appendUInt16(log2Floor(largestPowerOfTwo(numTables)))
        out.appendUInt16(numTables * 16 - searchRange)

        // Table directory.
        for (tag in sortedTags) {
            val body = tables.getValue(tag)
            out.appendTag(tag)
            out.appendUInt32(tableChecksum(body).toLong() and 0xFFFFFFFFL)
            out.appendUInt32(tableOffsets.getValue(tag).toLong())
            out.appendUInt32(body.size.toLong()) // actual (unpadded) length
        }

        // Table bodies, each padded to 4 bytes.
        for (tag in sortedTags) {
            val body = tables.getValue(tag)
            out.append(body, 0, body.size)
            val pad = paddedLengths.getValue(tag) - body.size
            repeat(pad) { out.appendByte(0) }
        }

        val bytes = out.toByteArray()

        // checkSumAdjustment = 0xB1B0AFBA - checksum(entire file with the field 0).
        val headOffset = tableOffsets.getValue("head")
        val fileChecksum = tableChecksum(bytes)
        val adjustment = (0xB1B0AFBAL - (fileChecksum.toLong() and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        writeUInt32(bytes, headOffset + 8, adjustment)
        return bytes
    }

    /**
     * The SFNT table checksum: the sum of the table's contents read as a sequence
     * of big-endian uint32 (zero-padded to a 4-byte multiple), truncated to 32
     * bits. Used both per-table in the directory and over the whole file to
     * derive `checkSumAdjustment`.
     */
    private fun tableChecksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i < data.size) {
            var word = 0L
            for (b in 0 until 4) {
                word = word shl 8
                if (i + b < data.size) word = word or (data[i + b].toLong() and 0xFF)
            }
            sum = (sum + word) and 0xFFFFFFFFL
            i += 4
        }
        return sum.toInt()
    }

    private fun writeUInt32(data: ByteArray, offset: Int, value: Long) {
        data[offset] = ((value ushr 24) and 0xFF).toByte()
        data[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }
}

/**
 * A minimal growable byte buffer for SFNT assembly, with big-endian append
 * helpers. Stdlib-only so it compiles for every PdfKmp target (no `java.io`).
 */
private class GrowableBytes(initialCapacity: Int = 1024) {
    private var data = ByteArray(if (initialCapacity < 16) 16 else initialCapacity)
    var size: Int = 0
        private set

    private fun ensure(extra: Int) {
        val needed = size + extra
        if (needed <= data.size) return
        var cap = data.size * 2
        while (cap < needed) cap *= 2
        data = data.copyOf(cap)
    }

    fun appendByte(b: Int) {
        ensure(1)
        data[size++] = b.toByte()
    }

    fun appendUInt16(v: Int) {
        ensure(2)
        data[size++] = ((v ushr 8) and 0xFF).toByte()
        data[size++] = (v and 0xFF).toByte()
    }

    fun appendUInt32(v: Long) {
        ensure(4)
        data[size++] = ((v ushr 24) and 0xFF).toByte()
        data[size++] = ((v ushr 16) and 0xFF).toByte()
        data[size++] = ((v ushr 8) and 0xFF).toByte()
        data[size++] = (v and 0xFF).toByte()
    }

    fun appendTag(tag: String) {
        ensure(4)
        for (i in 0 until 4) data[size++] = (if (i < tag.length) tag[i].code else 0x20).toByte()
    }

    fun append(src: ByteArray, from: Int, to: Int) {
        val len = to - from
        ensure(len)
        src.copyInto(data, size, from, to)
        size += len
    }

    /** Overwrites the big-endian uint16 at [pos] (used to backfill a length field). */
    fun setUInt16(pos: Int, v: Int) {
        data[pos] = ((v ushr 8) and 0xFF).toByte()
        data[pos + 1] = (v and 0xFF).toByte()
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}
