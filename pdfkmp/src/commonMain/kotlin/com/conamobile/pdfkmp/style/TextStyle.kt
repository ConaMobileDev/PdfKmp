package com.conamobile.pdfkmp.style

import com.conamobile.pdfkmp.unit.Sp
import com.conamobile.pdfkmp.unit.sp

/**
 * Visual style applied to a piece of text.
 *
 * Properties are merged top-down: a value set on a parent ([com.conamobile.pdfkmp.dsl.PageScope.textStyle])
 * cascades to children unless they override it. Inheritance happens in the
 * builders, not here — this is the resolved output of that cascade.
 */
public data class TextStyle(
    /** Font size in scale-independent pixels. */
    val fontSize: Sp = 12.sp,

    /** Weight on the 100..900 axis. */
    val fontWeight: FontWeight = FontWeight.Normal,

    /** Italic vs normal. */
    val fontStyle: FontStyle = FontStyle.Normal,

    /** Glyph color. */
    val color: PdfColor = PdfColor.Black,

    /** Resolvable font; defaults to the platform sans-serif. */
    val font: PdfFont = PdfFont.Default,

    /**
     * Extra horizontal spacing inserted between adjacent glyphs.
     * `0.sp` means use the font's natural advance widths.
     */
    val letterSpacing: Sp = Sp.Zero,

    /**
     * Vertical distance between baselines of consecutive lines. `0.sp` means
     * use the font's natural line height.
     */
    val lineHeight: Sp = Sp.Zero,

    /**
     * Whether to draw a continuous line just below the baseline. The line
     * inherits [color] and is one PDF point thick (scaled by font size for
     * larger text).
     */
    val underline: Boolean = false,

    /**
     * Whether to draw a continuous line through the vertical centre of the
     * glyphs. Inherits [color] like [underline].
     */
    val strikethrough: Boolean = false,

    /**
     * Horizontal alignment of wrapped lines inside the paragraph box. See
     * [TextAlign] for the four options. Defaults to [TextAlign.Start].
     */
    val align: TextAlign = TextAlign.Start,
) {
    public companion object {
        public val Default: TextStyle = TextStyle()
    }
}
