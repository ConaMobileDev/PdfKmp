package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.style.TextStyle
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSKernAttributeName
import platform.UIKit.sizeWithAttributes

/**
 * [FontMetrics] backed by `NSString.sizeWithAttributes`.
 *
 * The same attribute dictionary is used for measurement and drawing in
 * [IosPdfCanvas], which keeps layout output and rendered glyph positions
 * pixel-aligned.
 *
 * Ascent and descent come straight off the resolved [platform.UIKit.UIFont].
 * `NSString.sizeWithAttributes` returns `CGSize` in points, which matches
 * PdfKmp's native unit; no conversion is needed.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosFontMetrics(private val registry: IosFontRegistry) : FontMetrics {

    override fun measure(text: String, style: TextStyle): TextMetrics {
        val font = registry.fontFor(style)
        val attributes = mutableMapOf<Any?, Any?>(NSFontAttributeName to font)
        if (style.letterSpacing.value != 0f) {
            attributes[NSKernAttributeName] = style.letterSpacing.value.toDouble()
        }
        @Suppress("CAST_NEVER_SUCCEEDS") // Kotlin/Native String is bridged with NSString at runtime.
        val nsString = text as NSString
        val size = nsString.sizeWithAttributes(attributes)
        return TextMetrics(
            width = size.useContents { width.toFloat() },
            ascent = font.ascender.toFloat(),
            descent = -font.descender.toFloat(),
            lineGap = font.leading.toFloat(),
        )
    }
}
