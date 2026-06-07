package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.ttf.TtfSubsetter

/**
 * Serialises a [KmpEmbeddedFont] into the five PDF objects a CIDFontType2 /
 * Type0 embedding needs, and reports the top-level Type0 object number that the
 * page `/Font` dictionary references.
 *
 * ## Object set (Identity-H, CIDToGIDMap /Identity)
 *
 * 1. **Type0 font** — `/Subtype /Type0 /Encoding /Identity-H`, points at the
 *    descendant CIDFont and the ToUnicode CMap. This is the object pages refer to.
 * 2. **CIDFontType2** — the descendant; `/CIDToGIDMap /Identity` means CID == GID
 *    so the content stream's two-byte codes are glyph ids directly, and the `W`
 *    width array is keyed by glyph id.
 * 3. **FontDescriptor** — flags / bbox / ascent / descent / capHeight / stemV
 *    plus `/FontFile2` pointing at the subset stream.
 * 4. **FontFile2** — the [TtfSubsetter] output, Flate-compressed, with
 *    `/Length1` = the uncompressed subset length (required for `FontFile2`).
 * 5. **ToUnicode CMap** — maps the glyph ids back to Unicode so text extraction
 *    and copy/paste recover the original characters; also Flate-compressed.
 *
 * Identity-H + Identity CIDToGIDMap is the layout [TtfSubsetter] is built for:
 * preserving original glyph numbering means no renumbering anywhere in this
 * writer — the subset, the `W` array, the content stream, and the ToUnicode CMap
 * all speak the same glyph ids.
 */
internal class KmpEmbeddedFontWriter(private val embedded: KmpEmbeddedFont) {

    /** Number of indirect objects this font contributes. */
    val objectCount: Int = 5

    /**
     * Writes all five objects given the [base]..[base]+4 object numbers (the
     * Type0 dictionary is at [base]). Returns the Type0 object number for the
     * page `/Font` dictionary.
     */
    fun write(writer: PdfObjectWriter, base: Int): Int {
        val type0Obj = base
        val cidFontObj = base + 1
        val descriptorObj = base + 2
        val fontFileObj = base + 3
        val toUnicodeObj = base + 4

        val subsetBytes = TtfSubsetter.subset(embedded.font, embedded.subsetGlyphIds())

        writeType0(writer, type0Obj, cidFontObj, toUnicodeObj)
        writeCidFont(writer, cidFontObj, descriptorObj)
        writeDescriptor(writer, descriptorObj, fontFileObj)
        writeFontFile(writer, fontFileObj, subsetBytes)
        writeToUnicode(writer, toUnicodeObj)
        return type0Obj
    }

    private fun writeType0(writer: PdfObjectWriter, obj: Int, cidFontObj: Int, toUnicodeObj: Int) {
        writer.writeObject(
            obj,
            "<< /Type /Font /Subtype /Type0 /BaseFont /${embedded.baseName} " +
                "/Encoding /Identity-H /DescendantFonts [$cidFontObj 0 R] /ToUnicode $toUnicodeObj 0 R >>",
        )
    }

    private fun writeCidFont(writer: PdfObjectWriter, obj: Int, descriptorObj: Int) {
        val body = buildString {
            append("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /${embedded.baseName}")
            append(" /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>")
            append(" /FontDescriptor $descriptorObj 0 R")
            append(" /CIDToGIDMap /Identity")
            append(" /DW 1000")
            append(" /W ").append(widthArray())
            append(" >>")
        }
        writer.writeObject(obj, body)
    }

    /**
     * The `W` array: per-glyph widths in glyph-space, grouped as
     * `firstGid [w0 w1 …]` runs over the subset glyphs (sorted by id so adjacent
     * ids coalesce into one run). Widths come from the source font's hmtx scaled
     * to 1/1000 em, matching what [KmpFontMetrics] measured against.
     */
    private fun widthArray(): String {
        val gids = embedded.subsetGlyphIds().sorted()
        if (gids.isEmpty()) return "[]"
        val sb = StringBuilder()
        sb.append('[')
        var i = 0
        var first = true
        while (i < gids.size) {
            val start = gids[i]
            var j = i
            // Extend a run of consecutive glyph ids.
            while (j + 1 < gids.size && gids[j + 1] == gids[j] + 1) j++
            if (!first) sb.append(' ')
            first = false
            sb.append(start).append(" [")
            for (k in i..j) {
                if (k > i) sb.append(' ')
                sb.append(embedded.advanceThousandths(gids[k]))
            }
            sb.append(']')
            i = j + 1
        }
        sb.append(']')
        return sb.toString()
    }

    private fun writeDescriptor(writer: PdfObjectWriter, obj: Int, fontFileObj: Int) {
        val font = embedded.font
        val scale = 1000.0 / font.unitsPerEm
        fun s(v: Int): Int = (v * scale).toInt()
        val bbox = font.bbox
        // Flags: bit 3 (Serif=off here, treat as non-serif sans), bit 6 Nonsymbolic
        // (uses standard Latin set semantics), bit 2 Italic when slanted. We mark
        // 32 (Nonsymbolic) and optionally 64 (Italic). Bit 1 (FixedPitch) and
        // others are left off — viewers treat these as hints only.
        var flags = 32 // Nonsymbolic
        if (font.italic) flags = flags or 64
        // Cap height ≈ 0.7 × ascent when the font omits sCapHeight.
        val capHeight = if (font.capHeight != 0) s(font.capHeight) else embedded.ascentThousandths * 7 / 10
        val body = buildString {
            append("<< /Type /FontDescriptor /FontName /${embedded.baseName}")
            append(" /Flags $flags")
            append(" /FontBBox [${s(bbox[0])} ${s(bbox[1])} ${s(bbox[2])} ${s(bbox[3])}]")
            append(" /ItalicAngle ${if (font.italic) -12 else 0}")
            append(" /Ascent ${embedded.ascentThousandths}")
            append(" /Descent ${-embedded.descentThousandths}")
            append(" /CapHeight $capHeight")
            // StemV has no reliable source in a bare TTF; 80 is the conventional
            // medium-weight default viewers accept for a sans-serif face.
            append(" /StemV 80")
            append(" /FontFile2 $fontFileObj 0 R")
            append(" >>")
        }
        writer.writeObject(obj, body)
    }

    private fun writeFontFile(writer: PdfObjectWriter, obj: Int, subsetBytes: ByteArray) {
        val compressed = Deflate.zlibCompress(subsetBytes)
        // /Length1 is the uncompressed subset size (mandatory for FontFile2);
        // /Length (the compressed size) is spliced in by the stream writer.
        writer.writeStreamObject(
            obj,
            "<< /Length1 ${subsetBytes.size} /Filter /FlateDecode >>",
            compressed,
        )
    }

    /**
     * Builds and writes the ToUnicode CMap: a small PostScript-syntax CMap that
     * maps each used glyph id (the two-byte code in the content stream) to its
     * UTF-16BE Unicode value, so viewers can extract and copy the real text.
     */
    private fun writeToUnicode(writer: PdfObjectWriter, obj: Int) {
        val cmap = buildToUnicodeCMap(embedded.codePointMappings())
        val compressed = Deflate.zlibCompress(cmap.encodeToByteArray())
        writer.writeStreamObject(obj, "<< /Filter /FlateDecode >>", compressed)
    }

    /**
     * Renders the ToUnicode CMap text. Entries are `bfchar` lines mapping a
     * `<glyphId>` (4 hex digits) to `<unicode>` (UTF-16BE hex), emitted in blocks
     * of at most 100 as the CMap spec requires. Glyph ids beyond the BMP map to a
     * UTF-16 surrogate pair (still valid UTF-16BE).
     */
    private fun buildToUnicodeCMap(mappings: Map<Int, Int>): String {
        // Deduplicate by glyph id (several code points could map to one glyph;
        // pick the first to keep extraction deterministic).
        val byGlyph = LinkedHashMap<Int, Int>()
        for ((cp, gid) in mappings) {
            if (gid != 0 && gid !in byGlyph) byGlyph[gid] = cp
        }
        val entries = byGlyph.entries.sortedBy { it.key }

        val sb = StringBuilder()
        sb.append("/CIDInit /ProcSet findresource begin\n")
        sb.append("12 dict begin\nbegincmap\n")
        sb.append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
        sb.append("/CMapName /Adobe-Identity-UCS def\n")
        sb.append("/CMapType 2 def\n")
        sb.append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n")

        var index = 0
        while (index < entries.size) {
            val chunk = entries.subList(index, minOf(index + 100, entries.size))
            sb.append(chunk.size).append(" beginbfchar\n")
            for ((gid, cp) in chunk) {
                sb.append('<').append(hex4(gid)).append("> <").append(utf16BeHex(cp)).append(">\n")
            }
            sb.append("endbfchar\n")
            index += chunk.size
        }

        sb.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        return sb.toString()
    }

    private fun hex4(v: Int): String {
        val s = v.toString(16).uppercase()
        return "0".repeat((4 - s.length).coerceAtLeast(0)) + s
    }

    /** UTF-16BE hex of a Unicode code point (surrogate pair for astral planes). */
    private fun utf16BeHex(codePoint: Int): String {
        return if (codePoint <= 0xFFFF) {
            hex4(codePoint)
        } else {
            val v = codePoint - 0x10000
            val high = 0xD800 + (v shr 10)
            val low = 0xDC00 + (v and 0x3FF)
            hex4(high) + hex4(low)
        }
    }
}
