package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.render.TextMetrics
import com.conamobile.pdfkmp.style.TextStyle

/**
 * [FontMetrics] backed by the bundled Helvetica AFM advance-width tables.
 *
 * The layout engine consults this *before* any drawing happens to decide line
 * wrapping and page breaking, so the widths it returns must match what
 * [KmpPdfCanvas] later draws against the same Standard-14 font — otherwise wrap
 * points drift from the rendered glyph advances. Both sides read the identical
 * [HelveticaFace] table keyed by WinAnsi code, which keeps them in lock-step.
 *
 * Letter spacing is folded in exactly the way the PDF `Tc` operator applies it:
 * the extra advance is added after *every* shown glyph, including the last, so
 * the measured width equals `Σ glyphAdvance + glyphCount × letterSpacing`. This
 * mirrors the JVM/PdfBox backend so a document laid out for one renders the same
 * on the other.
 */
internal class KmpFontMetrics(
    /** Encodes text and warns once per document about substitutions; shared with the canvas. */
    private val textEncoder: WinAnsiTextEncoder,
    /** Decides the per-run rendering path and supplies embedded advances; shared with the canvas. */
    private val fontRegistry: KmpFontRegistry,
) : FontMetrics {

    override fun measure(text: String, style: TextStyle): TextMetrics {
        textEncoder.noteFont(style.font)
        val size = style.fontSize.value

        // Measure through whichever font the canvas will draw with, so wrap
        // points line up exactly with the rendered advances.
        return when (val plan = fontRegistry.planRun(text, style)) {
            is KmpFontRegistry.RunPlan.Helvetica -> measureHelvetica(text, style, plan.face, size)
            is KmpFontRegistry.RunPlan.Embedded -> measureEmbedded(text, style, plan.embedded, size)
        }
    }

    private fun measureHelvetica(text: String, style: TextStyle, face: HelveticaFace, size: Float): TextMetrics {
        val codes = textEncoder.encodeToWinAnsi(text)

        // Sum the per-glyph advances from the AFM table (1/1000 em → points).
        var advanceThousandths = 0
        for (code in codes) advanceThousandths += face.widthOf(code)
        val baseWidth = advanceThousandths / 1000f * size

        // Tc adds after every glyph including the last — count glyphs, not chars,
        // but WinAnsi is single-byte so one code == one glyph here.
        val letterSpacing = style.letterSpacing.value * codes.size
        val width = baseWidth + letterSpacing

        val ascent = HelveticaVerticalMetrics.ASCENDER / 1000f * size
        val descent = HelveticaVerticalMetrics.DESCENDER / 1000f * size
        return TextMetrics(
            width = width,
            ascent = ascent,
            descent = descent,
            // The Standard-14 fonts declare a zero typographic line gap; the
            // layout engine's own line-height policy supplies the inter-line
            // breathing room, matching the proportions of the other backends.
            lineGap = 0f,
        )
    }

    /**
     * Measures an embedded-font run against the source font's own hmtx advances
     * (per code point via the cmap), the same widths the PDF `W` array reports and
     * the canvas draws with — so layout and rendering stay in lock-step exactly as
     * the Helvetica path does against the AFM tables.
     */
    private fun measureEmbedded(text: String, style: TextStyle, embedded: KmpEmbeddedFont, size: Float): TextMetrics {
        var advanceThousandths = 0
        var glyphCount = 0
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            i += if (cp > 0xFFFF) 2 else 1
            val gid = embedded.glyphFor(cp)
            advanceThousandths += embedded.advanceThousandths(gid)
            glyphCount++
        }
        val baseWidth = advanceThousandths / 1000f * size
        val letterSpacing = style.letterSpacing.value * glyphCount
        val width = baseWidth + letterSpacing

        val ascent = embedded.ascentThousandths / 1000f * size
        val descent = embedded.descentThousandths / 1000f * size
        return TextMetrics(width = width, ascent = ascent, descent = descent, lineGap = 0f)
    }

    /** Decodes a (possibly astral) code point at [index] with wasm-safe char math. */
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

/** Ascent in points for [size], shared by the metrics and the canvas baseline math. */
internal fun ascentPoints(size: Float): Float = HelveticaVerticalMetrics.ASCENDER / 1000f * size
