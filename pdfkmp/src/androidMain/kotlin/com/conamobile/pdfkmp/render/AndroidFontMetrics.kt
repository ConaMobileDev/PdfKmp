package com.conamobile.pdfkmp.render

import android.graphics.Paint
import com.conamobile.pdfkmp.style.TextStyle

/**
 * [FontMetrics] backed by Android's [Paint].
 *
 * The implementation creates a single reusable [Paint] and reconfigures it on
 * each [measure] call to match the requested [TextStyle]. The same [Paint]
 * configuration is used by [AndroidPdfCanvas] for drawing, which keeps
 * measurements and rendered output in agreement.
 *
 * Letter spacing follows the Android convention — values supplied in PDF
 * points are converted to em-relative units expected by
 * [Paint.setLetterSpacing] using the current font size.
 */
internal class AndroidFontMetrics(private val registry: AndroidFontRegistry) : FontMetrics {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun measure(text: String, style: TextStyle): TextMetrics {
        applyStyle(paint, style)
        val width = paint.measureText(text)
        val fontMetrics = paint.fontMetrics
        return TextMetrics(
            width = width,
            ascent = -fontMetrics.ascent,
            descent = fontMetrics.descent,
            lineGap = fontMetrics.leading,
        )
    }

    /**
     * Mutates [paint] to reflect [style]. Exposed at package scope so the
     * canvas can apply the exact same configuration when drawing.
     */
    fun applyStyle(paint: Paint, style: TextStyle) {
        paint.typeface = registry.typefaceFor(style)
        paint.textSize = style.fontSize.value
        paint.color = style.color.toArgb()
        paint.letterSpacing = if (style.fontSize.value > 0f) {
            style.letterSpacing.value / style.fontSize.value
        } else {
            0f
        }
        paint.isAntiAlias = true
    }
}
