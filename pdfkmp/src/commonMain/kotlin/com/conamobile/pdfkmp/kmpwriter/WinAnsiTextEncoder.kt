package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Converts text into WinAnsi byte codes for the Standard-14 (Helvetica) text
 * path, and warns once per document when a code point falls through to the `?`
 * replacement.
 *
 * One instance is shared between [KmpFontMetrics] and [KmpPdfCanvas] for a whole
 * document so measurement and drawing make the same substitution decisions (a
 * glyph dropped at measure time must be dropped at draw time too) and so the
 * warning fires exactly once no matter how many runs trigger it.
 *
 * This only governs the Helvetica path now: [KmpFontRegistry] handles the two
 * embedding paths (custom fonts and the bundled-Inter Unicode fallback). The
 * `?` substitution is therefore the *last-resort* path — reached only for a code
 * point that is outside WinAnsi **and** has no glyph in any embeddable font (or
 * whose font failed to parse).
 *
 * Surrogate pairs are decoded to their full code point before lookup so an
 * astral character counts as one (unmappable) glyph rather than two stray `?`s.
 */
internal class WinAnsiTextEncoder {

    private var warnedUnmappable = false

    /**
     * Hook retained for symmetry with the embedding paths; the registry now owns
     * all custom-font diagnostics, so this is a no-op for [PdfFont.Custom].
     */
    @Suppress("UNUSED_PARAMETER")
    fun noteFont(font: PdfFont) {
        // Intentionally empty: custom-font handling moved to KmpFontRegistry.
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
