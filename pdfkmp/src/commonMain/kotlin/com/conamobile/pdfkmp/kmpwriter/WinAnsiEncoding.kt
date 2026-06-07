package com.conamobile.pdfkmp.kmpwriter

/**
 * Maps Unicode code points onto WinAnsiEncoding (Windows code page 1252) byte
 * codes, the encoding PdfKmp's pure-Kotlin writer pins its Standard-14 fonts to.
 *
 * The Standard-14 base fonts (Helvetica & friends) ship advance-width tables
 * indexed by WinAnsi code, and a PDF font dictionary that declares
 * `/Encoding /WinAnsiEncoding` tells every viewer to interpret the single-byte
 * content-stream codes through this same table — so measurement, drawing, and
 * display all agree. Only the WinAnsi repertoire is representable; any code
 * point outside it is reported as unmappable so the caller can substitute the
 * `?` glyph (code 63) and warn once per document.
 *
 * The 0x20..0x7E range is plain ASCII (identical to Unicode); 0xA0..0xFF is
 * Latin-1, which WinAnsi reuses unchanged; the 0x80..0x9F block is where
 * WinAnsi diverges from Latin-1, mapping the C1 control range onto printable
 * punctuation (smart quotes, em dash, euro, …). Those divergent entries are
 * listed explicitly below; everything else in the printable Latin-1 range maps
 * to its own code point.
 */
internal object WinAnsiEncoding {

    /** The WinAnsi code substituted for any code point this encoding can't represent. */
    const val REPLACEMENT: Int = '?'.code

    /**
     * Unicode code points that WinAnsi places in the 0x80..0x9F block (where
     * Latin-1 keeps control characters). Mapping is many-to-one in the reverse
     * direction only for a few legacy aliases, so a plain map is exact here.
     */
    private val highPunctuation: Map<Int, Int> = mapOf(
        0x20AC to 0x80, // EURO SIGN
        0x201A to 0x82, // SINGLE LOW-9 QUOTATION MARK
        0x0192 to 0x83, // LATIN SMALL LETTER F WITH HOOK (florin)
        0x201E to 0x84, // DOUBLE LOW-9 QUOTATION MARK
        0x2026 to 0x85, // HORIZONTAL ELLIPSIS
        0x2020 to 0x86, // DAGGER
        0x2021 to 0x87, // DOUBLE DAGGER
        0x02C6 to 0x88, // MODIFIER LETTER CIRCUMFLEX ACCENT
        0x2030 to 0x89, // PER MILLE SIGN
        0x0160 to 0x8A, // LATIN CAPITAL LETTER S WITH CARON
        0x2039 to 0x8B, // SINGLE LEFT-POINTING ANGLE QUOTATION MARK
        0x0152 to 0x8C, // LATIN CAPITAL LIGATURE OE
        0x017D to 0x8E, // LATIN CAPITAL LETTER Z WITH CARON
        0x2018 to 0x91, // LEFT SINGLE QUOTATION MARK
        0x2019 to 0x92, // RIGHT SINGLE QUOTATION MARK
        0x201C to 0x93, // LEFT DOUBLE QUOTATION MARK
        0x201D to 0x94, // RIGHT DOUBLE QUOTATION MARK
        0x2022 to 0x95, // BULLET
        0x2013 to 0x96, // EN DASH
        0x2014 to 0x97, // EM DASH
        0x02DC to 0x98, // SMALL TILDE
        0x2122 to 0x99, // TRADE MARK SIGN
        0x0161 to 0x9A, // LATIN SMALL LETTER S WITH CARON
        0x203A to 0x9B, // SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
        0x0153 to 0x9C, // LATIN SMALL LIGATURE OE
        0x017E to 0x9E, // LATIN SMALL LETTER Z WITH CARON
        0x0178 to 0x9F, // LATIN CAPITAL LETTER Y WITH DIAERESIS
    )

    /**
     * Returns the WinAnsi byte code for [codePoint], or `null` when the code
     * point has no WinAnsi representation (the caller substitutes [REPLACEMENT]
     * and warns). The space..tilde and nbsp..ÿ ranges map to themselves; the
     * 0x80..0x9F punctuation block is resolved through [highPunctuation].
     */
    fun encode(codePoint: Int): Int? = when (codePoint) {
        in 0x20..0x7E -> codePoint
        in 0xA0..0xFF -> codePoint
        else -> highPunctuation[codePoint]
    }
}
