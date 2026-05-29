package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.style.TextStyle
import org.apache.pdfbox.pdmodel.font.PDFont

/**
 * [FontMetrics] backed by PdfBox's embedded-font metrics.
 *
 * Widths come from [PDFont.getStringWidth] — the font's own `hmtx` advance
 * widths scaled to the requested point size — so wrapping decisions made by
 * the layout engine line up exactly with the glyph advances PdfBox emits at
 * draw time. Ascent and descent read from the font descriptor.
 *
 * The same [JvmFontRegistry] (and therefore the same [PDFont] instances)
 * back both this class and [JvmPdfCanvas], guaranteeing measured and drawn
 * runs agree.
 */
internal class JvmFontMetrics(private val registry: JvmFontRegistry) : FontMetrics {

    override fun measure(text: String, style: TextStyle): TextMetrics {
        val font = registry.fontFor(style)
        val size = style.fontSize.value
        val encodable = registry.encodable(font, text)

        // getStringWidth returns 1/1000 glyph-space units; scale to points.
        val baseWidth = if (encodable.isEmpty()) 0f else font.getStringWidth(encodable) / 1000f * size

        // PDF character spacing (Tc) is added after every shown glyph,
        // including the last — mirror that here so wrapping matches drawing.
        val glyphCount = encodable.codePointCount(0, encodable.length)
        val letterSpacing = style.letterSpacing.value * glyphCount
        val width = baseWidth + letterSpacing

        val descriptor = font.fontDescriptor
        val descent = if (descriptor != null && descriptor.ascent != 0f) {
            -descriptor.descent / 1000f * size
        } else {
            0.2f * size
        }
        return TextMetrics(
            width = width,
            ascent = font.ascentPoints(size),
            descent = descent,
            lineGap = 0f,
        )
    }
}

/**
 * Distance from the baseline to the top of the tallest glyph, in points.
 *
 * Shared by [JvmFontMetrics] (so layout reserves the right line height) and
 * [JvmPdfCanvas] (so it places the baseline at the same offset the layout
 * assumed). Falls back to `0.8 × size` when the font omits a descriptor.
 */
internal fun PDFont.ascentPoints(size: Float): Float {
    val descriptor = fontDescriptor
    return if (descriptor != null && descriptor.ascent != 0f) {
        descriptor.ascent / 1000f * size
    } else {
        0.8f * size
    }
}
