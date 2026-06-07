package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.layout.HyphenationPatterns
import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextDirection
import com.conamobile.pdfkmp.style.TextOverflow
import com.conamobile.pdfkmp.style.TextScript
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Sp

/**
 * Mutable receiver passed to the `text("...") { ... }` configuration block.
 *
 * Every property starts at the value of the inherited [TextStyle] from the
 * enclosing scope; assigning to a property overrides it for this text only.
 * The block returns a resolved [TextStyle] via [build].
 *
 * Two convenience flags — [bold] and [italic] — are provided for the common
 * case; underneath they delegate to [fontWeight] and [fontStyle].
 */
@PdfDsl
public class TextScope internal constructor(parent: TextStyle) {

    /** Font size in scale-independent pixels. Default: inherited from the parent scope. */
    public var fontSize: Sp = parent.fontSize

    /** Weight on the 100..900 axis. Default: inherited. */
    public var fontWeight: FontWeight = parent.fontWeight

    /** Italic vs normal. Default: inherited. */
    public var fontStyle: FontStyle = parent.fontStyle

    /** Glyph color. Default: inherited. */
    public var color: PdfColor = parent.color

    /** Resolvable font; defaults to the platform sans-serif unless overridden in a parent scope. */
    public var font: PdfFont = parent.font

    /** Extra horizontal space inserted between glyphs. Default: inherited. */
    public var letterSpacing: Sp = parent.letterSpacing

    /** Distance between baselines of consecutive lines. `0.sp` means use the font's natural value. */
    public var lineHeight: Sp = parent.lineHeight

    /** Whether the text is underlined. Defaults to inherited. */
    public var underline: Boolean = parent.underline

    /** Whether the text is struck through. Defaults to inherited. */
    public var strikethrough: Boolean = parent.strikethrough

    /** Horizontal alignment of wrapped lines within the paragraph box. */
    public var align: TextAlign = parent.align

    /**
     * Maximum number of wrapped lines; `null` keeps them all. Lines beyond
     * the limit are dropped according to [overflow].
     */
    public var maxLines: Int? = parent.maxLines

    /** What to do with lines dropped by [maxLines]. */
    public var overflow: TextOverflow = parent.overflow

    /**
     * Superscript / subscript positioning. Only meaningful on spans inside
     * `richText { }` — plain paragraphs ignore it.
     */
    public var script: TextScript = parent.script

    /** Base paragraph direction; `Auto` detects RTL scripts from content. */
    public var direction: TextDirection = parent.direction

    /**
     * Orphan control under the `Slice` strategy: minimum lines kept
     * together at the bottom of a page before a break. `1` allows any split.
     */
    public var minLinesBeforeBreak: Int = parent.minLinesBeforeBreak

    /**
     * Widow control under the `Slice` strategy: minimum lines continuing
     * on the next page after a break. `1` allows any split.
     */
    public var minLinesAfterBreak: Int = parent.minLinesAfterBreak

    /**
     * Automatic hyphenation dictionary; `null` disables it (the default).
     * See [com.conamobile.pdfkmp.layout.Hyphenators] for bundled languages.
     */
    public var hyphenation: HyphenationPatterns? = parent.hyphenation

    /**
     * Kashida (tatweel) justification for cursive RTL text. Only effective when
     * the line is justified and resolves to right-to-left. `false` by default.
     */
    public var kashidaJustify: Boolean = parent.kashidaJustify

    /**
     * Convenience flag that toggles between [FontWeight.Bold] and
     * [FontWeight.Normal]. Reading this returns `true` when [fontWeight] is
     * exactly [FontWeight.Bold].
     */
    public var bold: Boolean
        get() = fontWeight == FontWeight.Bold
        set(value) {
            fontWeight = if (value) FontWeight.Bold else FontWeight.Normal
        }

    /** Convenience flag that toggles between [FontStyle.Italic] and [FontStyle.Normal]. */
    public var italic: Boolean
        get() = fontStyle == FontStyle.Italic
        set(value) {
            fontStyle = if (value) FontStyle.Italic else FontStyle.Normal
        }

    internal fun build(): TextStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        color = color,
        font = font,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        underline = underline,
        strikethrough = strikethrough,
        align = align,
        maxLines = maxLines,
        overflow = overflow,
        script = script,
        direction = direction,
        minLinesBeforeBreak = minLinesBeforeBreak,
        minLinesAfterBreak = minLinesAfterBreak,
        hyphenation = hyphenation,
        kashidaJustify = kashidaJustify,
    )
}
