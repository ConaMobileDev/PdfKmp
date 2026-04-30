package com.conamobile.pdfkmp.font

import com.conamobile.pdfkmp.font.bundled.InterBoldBytes
import com.conamobile.pdfkmp.font.bundled.InterBoldItalicBytes
import com.conamobile.pdfkmp.font.bundled.InterItalicBytes
import com.conamobile.pdfkmp.font.bundled.InterRegularBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Raw bytes of the fonts shipped inside the library.
 *
 * PdfKmp bundles **Inter** (SIL Open Font License 1.1) so that text rendering
 * is consistent across every supported platform: both Android and iOS register
 * exactly the same byte sequence with their native font managers, and the
 * resulting PDFs embed the same glyph outlines.
 *
 * The bytes are decoded from the generated `font.bundled` constants on first
 * access and cached for the lifetime of the process. Each property allocates
 * roughly 400 KB of memory once.
 *
 * Bundled fonts are licensed separately from PdfKmp — see
 * `pdfkmp/fonts/Inter-LICENSE.txt` for the SIL OFL 1.1 terms.
 */
public object BundledFonts {

    /** Inter Regular (weight 400, upright). The library default. */
    public val interRegular: ByteArray by lazy { decode(InterRegularBytes.chunks) }

    /** Inter Bold (weight 700, upright). */
    public val interBold: ByteArray by lazy { decode(InterBoldBytes.chunks) }

    /** Inter Italic (weight 400, italic). */
    public val interItalic: ByteArray by lazy { decode(InterItalicBytes.chunks) }

    /** Inter Bold Italic (weight 700, italic). */
    public val interBoldItalic: ByteArray by lazy { decode(InterBoldItalicBytes.chunks) }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decode(chunks: Array<String>): ByteArray {
        val combined = StringBuilder(chunks.sumOf { it.length })
        for (chunk in chunks) combined.append(chunk)
        return Base64.Default.decode(combined.toString())
    }
}
