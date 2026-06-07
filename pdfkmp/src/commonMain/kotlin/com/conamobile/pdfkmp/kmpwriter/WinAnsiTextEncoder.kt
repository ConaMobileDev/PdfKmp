package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Converts arbitrary text into WinAnsi byte codes for the Standard-14 fonts and
 * tracks the two "your text wasn't fully representable" conditions this phase-1
 * backend can't honour, warning about each at most once per document.
 *
 * One instance is shared between [KmpFontMetrics] and [KmpPdfCanvas] for a whole
 * document so measurement and drawing always make the same substitution
 * decisions (a glyph dropped at measure time must be dropped at draw time too)
 * and so each warning fires exactly once no matter how many runs trigger it:
 *
 * - A code point outside WinAnsi (CJK, emoji, most non-Latin scripts) is
 *   replaced with `?` ([WinAnsiEncoding.REPLACEMENT]) — the only repertoire the
 *   non-embedded base fonts can show — and warns once.
 * - A [PdfFont.Custom] reference can't be embedded yet by this backend, so it
 *   maps to the Helvetica face matching its weight/style and warns once.
 *
 * Surrogate pairs are decoded to their full code point before lookup so an
 * astral character counts as one (unmappable) glyph rather than two stray `?`s.
 */
internal class WinAnsiTextEncoder {

    private var warnedUnmappable = false
    private var warnedCustomFont = false

    /**
     * Records that a run used [font], warning once if it is a [PdfFont.Custom]
     * (which this backend can't embed yet). Called by both the metrics and the
     * canvas so the warning fires regardless of which path sees the font first.
     */
    fun noteFont(font: PdfFont) {
        if (font is PdfFont.Custom && !warnedCustomFont) {
            warnedCustomFont = true
            PdfLog.warn(
                "Custom font '${font.name}' is not embedded by the pure-Kotlin PDF " +
                    "backend yet; falling back to the Standard-14 Helvetica face.",
            )
        }
    }

    /**
     * Encodes [text] to a list of WinAnsi byte codes, substituting
     * [WinAnsiEncoding.REPLACEMENT] for any code point WinAnsi can't represent
     * and warning once per document the first time that happens.
     */
    fun encodeToWinAnsi(text: String): IntArray {
        if (text.isEmpty()) return IntArray(0)
        val out = ArrayList<Int>(text.length)
        var i = 0
        while (i < text.length) {
            val codePoint = codePointAt(text, i)
            i += if (codePoint > 0xFFFF) 2 else 1
            val mapped = WinAnsiEncoding.encode(codePoint)
            if (mapped != null) {
                out.add(mapped)
            } else {
                out.add(WinAnsiEncoding.REPLACEMENT)
                if (!warnedUnmappable) {
                    warnedUnmappable = true
                    PdfLog.warn(
                        "Text contains characters outside WinAnsiEncoding; the pure-Kotlin " +
                            "PDF backend substitutes '?' for them (custom-font embedding is not " +
                            "implemented yet).",
                    )
                }
            }
        }
        return out.toIntArray()
    }

    /**
     * Decodes the Unicode code point at [index] in [text], combining a
     * high/low surrogate pair into a single astral code point. Implemented with
     * stdlib char arithmetic so it stays wasm-compatible (no `Character.*`).
     */
    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) {
                return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            }
        }
        return high.code
    }
}
