package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight

/**
 * Bundled Adobe Font Metrics (AFM) advance-width tables for the Helvetica
 * branch of the PDF Standard-14 base fonts, indexed by WinAnsiEncoding byte
 * code (0..255). Widths are in 1/1000 em (glyph-space) units, the unit AFM and
 * PDF text-space both use, so a glyph's point advance is `width / 1000 × size`.
 *
 * These four faces — regular, bold, oblique, bold-oblique — are the only fonts
 * the pure-Kotlin writer emits in its first phase. Because every PDF viewer is
 * required to carry the Standard-14 metrics itself, nothing is embedded: the
 * font dictionary just names `/Helvetica` (etc.) with `/Encoding
 * /WinAnsiEncoding` and the viewer supplies the outlines. Matching the canonical
 * Adobe widths here is what keeps PdfKmp's layout (which measures through these
 * tables) aligned with what the viewer finally draws.
 *
 * The high-punctuation entries (smart quotes, dashes, euro, …) are filled at the
 * WinAnsi code where each glyph lives so a string containing them measures and
 * renders correctly even though the source AFM keyed them by glyph name.
 */
internal enum class HelveticaFace(
    /** PostScript base-font name written as `/BaseFont` in the font dictionary. */
    val baseFont: String,
    /** Advance widths in 1/1000 em, indexed by WinAnsi byte code. */
    val widths: IntArray,
) {
    REGULAR("Helvetica", HelveticaWidths.regular),
    BOLD("Helvetica-Bold", HelveticaWidths.bold),
    OBLIQUE("Helvetica-Oblique", HelveticaWidths.oblique),
    BOLD_OBLIQUE("Helvetica-BoldOblique", HelveticaWidths.boldOblique),
    ;

    /** Advance for [code] in 1/1000 em; falls back to the `?` width if absent. */
    fun widthOf(code: Int): Int {
        val w = if (code in widths.indices) widths[code] else 0
        return if (w > 0) w else widths[WinAnsiEncoding.REPLACEMENT]
    }

    companion object {
        /**
         * Picks the Helvetica face for a resolved [weight] / [style], mirroring
         * the bundled-font mapping used by the other backends: weight at or
         * above SemiBold (600) selects bold, [FontStyle.Italic] selects oblique.
         */
        fun forStyle(weight: FontWeight, style: FontStyle): HelveticaFace {
            val bold = weight.value >= FontWeight.SemiBold.value
            val italic = style == FontStyle.Italic
            return when {
                bold && italic -> BOLD_OBLIQUE
                bold -> BOLD
                italic -> OBLIQUE
                else -> REGULAR
            }
        }
    }
}

/**
 * Shared vertical metrics for the Helvetica family, in 1/1000 em. The whole
 * family shares one ascender/descender pair (the bold/oblique faces keep the
 * same vertical extents as regular), so a single set drives line height for all
 * four faces. A glyph point value is `metric / 1000 × size`.
 */
internal object HelveticaVerticalMetrics {
    /** Distance from baseline to the top of the tallest glyph (Adobe AFM `Ascender`). */
    const val ASCENDER: Int = 718

    /** Distance from baseline down to the lowest glyph; AFM `Descender` is -207. */
    const val DESCENDER: Int = 207
}

/**
 * The raw width tables. Split into a separate object so [HelveticaFace] reads as
 * a compact enum while the bulky data lives below. Each array is 256 long; only
 * the printable WinAnsi codes carry a non-zero width.
 */
private object HelveticaWidths {

    /**
     * Builds a 256-entry width array from `(winAnsiCode, width)` pairs. Codes
     * not listed default to 0 — [HelveticaFace.widthOf] resolves those to the
     * `?` width, so an unmapped glyph never measures as zero-advance.
     */
    private fun table(vararg pairs: Pair<Int, Int>): IntArray {
        val a = IntArray(256)
        for ((code, width) in pairs) a[code] = width
        return a
    }

    val regular: IntArray = table(
        32 to 278, 33 to 278, 34 to 355, 35 to 556, 36 to 556, 37 to 889, 38 to 667,
        39 to 191, 40 to 333, 41 to 333, 42 to 389, 43 to 584, 44 to 278, 45 to 333,
        46 to 278, 47 to 278, 48 to 556, 49 to 556, 50 to 556, 51 to 556, 52 to 556,
        53 to 556, 54 to 556, 55 to 556, 56 to 556, 57 to 556, 58 to 278, 59 to 278,
        60 to 584, 61 to 584, 62 to 584, 63 to 556, 64 to 1015, 65 to 667, 66 to 667,
        67 to 722, 68 to 722, 69 to 667, 70 to 611, 71 to 778, 72 to 722, 73 to 278,
        74 to 500, 75 to 667, 76 to 556, 77 to 833, 78 to 722, 79 to 778, 80 to 667,
        81 to 778, 82 to 722, 83 to 667, 84 to 611, 85 to 722, 86 to 667, 87 to 944,
        88 to 667, 89 to 667, 90 to 611, 91 to 278, 92 to 278, 93 to 278, 94 to 469,
        95 to 556, 96 to 333, 97 to 556, 98 to 556, 99 to 500, 100 to 556, 101 to 556,
        102 to 278, 103 to 556, 104 to 556, 105 to 222, 106 to 222, 107 to 500,
        108 to 222, 109 to 833, 110 to 556, 111 to 556, 112 to 556, 113 to 556,
        114 to 333, 115 to 500, 116 to 278, 117 to 556, 118 to 500, 119 to 722,
        120 to 500, 121 to 500, 122 to 500, 123 to 334, 124 to 260, 125 to 334,
        126 to 584,
        // High-punctuation block (WinAnsi 0x80..0x9F).
        128 to 556, 130 to 222, 131 to 556, 132 to 333, 133 to 1000, 134 to 556,
        135 to 556, 136 to 333, 137 to 1000, 138 to 667, 139 to 333, 140 to 1000,
        142 to 611, 145 to 222, 146 to 222, 147 to 333, 148 to 333, 149 to 350,
        150 to 556, 151 to 1000, 152 to 333, 153 to 1000, 154 to 500, 155 to 333,
        156 to 944, 158 to 500, 159 to 667,
        // Latin-1 supplement (WinAnsi 0xA0..0xFF).
        160 to 278, 161 to 333, 162 to 556, 163 to 556, 164 to 556, 165 to 556,
        166 to 260, 167 to 556, 168 to 333, 169 to 737, 170 to 370, 171 to 556,
        172 to 584, 173 to 333, 174 to 737, 175 to 333, 176 to 400, 177 to 584,
        178 to 333, 179 to 333, 180 to 333, 181 to 556, 182 to 537, 183 to 278,
        184 to 333, 185 to 333, 186 to 365, 187 to 556, 188 to 834, 189 to 834,
        190 to 834, 191 to 611, 192 to 667, 193 to 667, 194 to 667, 195 to 667,
        196 to 667, 197 to 667, 198 to 1000, 199 to 722, 200 to 667, 201 to 667,
        202 to 667, 203 to 667, 204 to 278, 205 to 278, 206 to 278, 207 to 278,
        208 to 722, 209 to 722, 210 to 778, 211 to 778, 212 to 778, 213 to 778,
        214 to 778, 215 to 584, 216 to 778, 217 to 722, 218 to 722, 219 to 722,
        220 to 722, 221 to 667, 222 to 667, 223 to 611, 224 to 556, 225 to 556,
        226 to 556, 227 to 556, 228 to 556, 229 to 556, 230 to 889, 231 to 500,
        232 to 556, 233 to 556, 234 to 556, 235 to 556, 236 to 278, 237 to 278,
        238 to 278, 239 to 278, 240 to 556, 241 to 556, 242 to 556, 243 to 556,
        244 to 556, 245 to 556, 246 to 556, 247 to 584, 248 to 611, 249 to 556,
        250 to 556, 251 to 556, 252 to 556, 253 to 500, 254 to 556, 255 to 500,
    )

    val bold: IntArray = table(
        32 to 278, 33 to 333, 34 to 474, 35 to 556, 36 to 556, 37 to 889, 38 to 722,
        39 to 238, 40 to 333, 41 to 333, 42 to 389, 43 to 584, 44 to 278, 45 to 333,
        46 to 278, 47 to 278, 48 to 556, 49 to 556, 50 to 556, 51 to 556, 52 to 556,
        53 to 556, 54 to 556, 55 to 556, 56 to 556, 57 to 556, 58 to 333, 59 to 333,
        60 to 584, 61 to 584, 62 to 584, 63 to 611, 64 to 975, 65 to 722, 66 to 722,
        67 to 722, 68 to 722, 69 to 667, 70 to 611, 71 to 778, 72 to 722, 73 to 278,
        74 to 556, 75 to 722, 76 to 611, 77 to 833, 78 to 722, 79 to 778, 80 to 667,
        81 to 778, 82 to 722, 83 to 667, 84 to 611, 85 to 722, 86 to 667, 87 to 944,
        88 to 667, 89 to 667, 90 to 611, 91 to 333, 92 to 278, 93 to 333, 94 to 584,
        95 to 556, 96 to 333, 97 to 556, 98 to 611, 99 to 556, 100 to 611, 101 to 556,
        102 to 333, 103 to 611, 104 to 611, 105 to 278, 106 to 278, 107 to 556,
        108 to 278, 109 to 889, 110 to 611, 111 to 611, 112 to 611, 113 to 611,
        114 to 389, 115 to 556, 116 to 333, 117 to 611, 118 to 556, 119 to 778,
        120 to 556, 121 to 556, 122 to 500, 123 to 389, 124 to 280, 125 to 389,
        126 to 584,
        128 to 556, 130 to 278, 131 to 556, 132 to 500, 133 to 1000, 134 to 556,
        135 to 556, 136 to 333, 137 to 1000, 138 to 667, 139 to 333, 140 to 1000,
        142 to 611, 145 to 278, 146 to 278, 147 to 500, 148 to 500, 149 to 350,
        150 to 556, 151 to 1000, 152 to 333, 153 to 1000, 154 to 556, 155 to 333,
        156 to 944, 158 to 500, 159 to 667,
        160 to 278, 161 to 333, 162 to 556, 163 to 556, 164 to 556, 165 to 556,
        166 to 280, 167 to 556, 168 to 333, 169 to 737, 170 to 370, 171 to 556,
        172 to 584, 173 to 333, 174 to 737, 175 to 333, 176 to 400, 177 to 584,
        178 to 333, 179 to 333, 180 to 333, 181 to 611, 182 to 556, 183 to 278,
        184 to 333, 185 to 333, 186 to 365, 187 to 556, 188 to 834, 189 to 834,
        190 to 834, 191 to 611, 192 to 722, 193 to 722, 194 to 722, 195 to 722,
        196 to 722, 197 to 722, 198 to 1000, 199 to 722, 200 to 667, 201 to 667,
        202 to 667, 203 to 667, 204 to 278, 205 to 278, 206 to 278, 207 to 278,
        208 to 722, 209 to 722, 210 to 778, 211 to 778, 212 to 778, 213 to 778,
        214 to 778, 215 to 584, 216 to 778, 217 to 722, 218 to 722, 219 to 722,
        220 to 722, 221 to 667, 222 to 667, 223 to 611, 224 to 556, 225 to 556,
        226 to 556, 227 to 556, 228 to 556, 229 to 556, 230 to 889, 231 to 556,
        232 to 556, 233 to 556, 234 to 556, 235 to 556, 236 to 278, 237 to 278,
        238 to 278, 239 to 278, 240 to 611, 241 to 611, 242 to 611, 243 to 611,
        244 to 611, 245 to 611, 246 to 611, 247 to 584, 248 to 611, 249 to 611,
        250 to 611, 251 to 611, 252 to 611, 253 to 556, 254 to 611, 255 to 556,
    )

    // Oblique shares Helvetica-Regular's advance widths (only the glyphs slant);
    // bold-oblique likewise shares Helvetica-Bold's. This matches the Adobe AFM
    // data, where the oblique faces are width-identical to their upright kin.
    val oblique: IntArray = regular
    val boldOblique: IntArray = bold
}
