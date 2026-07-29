package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.font.BundledFonts
import com.conamobile.pdfkmp.font.ResolvedFont
import com.conamobile.pdfkmp.font.resolveFont
import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TextStyle
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayInputStream

/**
 * Per-document cache mapping a [ResolvedFont] onto the [PDFont] embedded in
 * the PdfBox [PDDocument].
 *
 * Every bundled or custom font is embedded as a subset-enabled
 * [PDType0Font], which supports the full Unicode range and lets PdfBox emit
 * vector glyph outlines (sharp at any zoom) while keeping the file small by
 * subsetting to only the glyphs actually drawn.
 *
 * Desktop platforms have no system-wide "register a font by name" facility
 * comparable to Android's `Typeface` or iOS's `UIFont`. A [PdfFont.System]
 * reference therefore falls back to the bundled Inter face (matching the
 * documented "drops back to Default" behaviour on
 * [com.conamobile.pdfkmp.style.PdfFont.System]); supply a
 * [PdfFont.Custom] font with the right script coverage to render
 * non-Latin text on Desktop.
 *
 * The same [PDFont] instance backs both measurement ([JvmFontMetrics]) and
 * drawing ([JvmPdfCanvas]), so subsetting accumulates correctly and laid-out
 * positions match rendered glyphs exactly.
 */
internal class JvmFontRegistry(private val document: PDDocument) {

    private val fonts = mutableMapOf<String, PDFont>()

    /**
     * One shared fallback face per document. Loading Inter through
     * [PDType0Font.load] embeds a separate font program each time, so a
     * per-failure load would duplicate the subset in the output PDF for
     * every unparseable custom font.
     */
    private val fallbackFont: PDFont by lazy {
        try {
            loadType0(BundledFonts.interRegular)
        } catch (e: Exception) {
            throw IllegalStateException("Bundled Inter font failed to load into PdfBox", e)
        }
    }

    /** Returns the [PDFont] for [style], embedding its bytes on first use. */
    fun fontFor(style: TextStyle): PDFont {
        val resolved = resolveFont(style.font, style.fontWeight, style.fontStyle)
        return cached(resolved)
    }

    /** Eagerly embeds every custom font referenced by the document. */
    fun preregister(customFonts: List<PdfFont.Custom>) {
        for (font in customFonts) {
            cached(resolveFont(font, FontWeight.Normal, FontStyle.Normal))
        }
    }

    private fun cached(resolved: ResolvedFont): PDFont {
        fonts[resolved.name]?.let { return it }
        val font = resolveWithFallback(resolved)
        fonts[resolved.name] = font
        return font
    }

    private fun resolveWithFallback(resolved: ResolvedFont): PDFont {
        // System fonts have no bytes on Desktop — fall back to bundled Inter.
        val bytes = resolved.bytes ?: return fallbackFont
        return try {
            loadType0(bytes)
        } catch (e: Exception) {
            // Exception only: an Error (OOM, linkage) must propagate rather
            // than be misreported as a font-parse failure.
            PdfLog.warn(
                "Custom font '${resolved.name}' could not be parsed; falling back to the bundled Inter face (JVM backend)",
            )
            fallbackFont
        }
    }

    private fun loadType0(bytes: ByteArray): PDFont =
        ByteArrayInputStream(bytes).use { stream ->
            PDType0Font.load(document, stream, true)
        }

    /**
     * Returns the subsequence of [text] whose characters [font] can encode.
     *
     * PdfBox throws [IllegalArgumentException] when asked to measure or show
     * a character the embedded font has no glyph for (e.g. a CJK ideograph
     * against the Latin-only Inter face). The whole string is tried first —
     * the common all-Latin path costs a single call — and only on failure
     * is the string filtered code point by code point. Measurement and
     * drawing share this filter so their results never diverge.
     */
    fun encodable(font: PDFont, text: String): String {
        if (text.isEmpty()) return text
        if (canEncode(font, text)) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val piece = text.substring(i, i + charCount)
            if (canEncode(font, piece)) sb.append(piece)
            i += charCount
        }
        return sb.toString()
    }

    private fun canEncode(font: PDFont, piece: String): Boolean = try {
        font.getStringWidth(piece)
        true
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: Exception) {
        false
    }
}
