package com.conamobile.pdfkmp.font

import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Resolved font reference passed to a [com.conamobile.pdfkmp.render.PdfDriver].
 *
 * Layout and rendering go through [ResolvedFont] rather than [PdfFont] so that
 * platform backends never have to re-implement the cascade of "is the font
 * Default? if so, which weight/style is needed? which bundled bytes match?".
 *
 * @property name Stable identifier registered with the platform font manager.
 *   The same name is used for measurement and for drawing.
 * @property bytes TTF/OTF bytes to register on first use, or `null` for fonts
 *   already known to the platform (system-named fonts).
 */
public data class ResolvedFont(
    val name: String,
    val bytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is ResolvedFont && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String =
        "ResolvedFont(name='$name', hasBytes=${bytes != null})"
}

/**
 * Maps a [PdfFont] reference plus the desired weight and style to a concrete
 * [ResolvedFont] that the renderer can register and draw with.
 *
 * Resolution rules:
 *
 * - [PdfFont.Default] cascades into one of the four bundled Inter variants
 *   based on [weight] (>= 600 → bold) and [style] (italic / upright).
 * - [PdfFont.System] is used as-is — the renderer will look it up in the
 *   platform font registry and fall back to Inter Regular if missing.
 * - [PdfFont.Custom] returns a resolved entry pointing at the user's bytes.
 *
 * The function is pure; calling it many times for the same inputs returns
 * equivalent values without side effects.
 */
public fun resolveFont(
    font: PdfFont,
    weight: FontWeight,
    style: FontStyle,
): ResolvedFont = when (font) {
    is PdfFont.Default -> defaultBundledFor(weight, style)
    is PdfFont.System -> ResolvedFont(name = font.name, bytes = null)
    is PdfFont.Custom -> ResolvedFont(name = font.name, bytes = font.bytes)
}

private fun defaultBundledFor(weight: FontWeight, style: FontStyle): ResolvedFont {
    val bold = weight.value >= FontWeight.SemiBold.value
    val italic = style == FontStyle.Italic
    return when {
        bold && italic -> ResolvedFont("PdfKmp.Inter-BoldItalic", BundledFonts.interBoldItalic)
        bold -> ResolvedFont("PdfKmp.Inter-Bold", BundledFonts.interBold)
        italic -> ResolvedFont("PdfKmp.Inter-Italic", BundledFonts.interItalic)
        else -> ResolvedFont("PdfKmp.Inter-Regular", BundledFonts.interRegular)
    }
}
