package com.conamobile.pdfkmp.style

/**
 * Base direction of a paragraph.
 *
 * Direction affects which edge `TextAlign.Start` / `End` anchor to and
 * which side justification leaves ragged. Glyph-level bidi reordering and
 * Arabic shaping happen in the platform backends (Android and iOS shape
 * natively; the JVM backend runs its own bidi + shaping pass), so the
 * common layout layer only needs the paragraph's base direction.
 */
public enum class TextDirection {
    /**
     * Detect from the first strong directional character — Hebrew or
     * Arabic content flips the paragraph to RTL automatically. The
     * default, and the right choice for user-supplied content.
     */
    Auto,

    /** Force left-to-right regardless of content. */
    Ltr,

    /** Force right-to-left regardless of content. */
    Rtl,
}

/**
 * Resolves [TextDirection.Auto] against actual [text]: the first strong
 * directional character wins, mirroring the UAX#9 paragraph-level rule.
 * Strings with no strong characters (digits, punctuation) read as LTR.
 */
public fun TextDirection.resolve(text: String): TextDirection {
    if (this != TextDirection.Auto) return this
    for (ch in text) {
        when (ch.code) {
            // Hebrew, Arabic (+ supplements), and their presentation forms.
            in 0x0590..0x08FF, in 0xFB1D..0xFDFF, in 0xFE70..0xFEFF -> return TextDirection.Rtl
            // Latin, Greek, Cyrillic, Armenian.
            in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F,
            in 0x0370..0x03FF, in 0x0400..0x052F, in 0x0530..0x058F,
            // CJK, kana, Hangul — strong LTR for paragraph purposes.
            in 0x3040..0x30FF, in 0x4E00..0x9FFF, in 0xAC00..0xD7AF,
            -> return TextDirection.Ltr
        }
    }
    return TextDirection.Ltr
}
