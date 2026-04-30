package com.conamobile.pdfkmp.style

/**
 * Horizontal alignment of wrapped lines inside a [TextStyle]'s paragraph
 * box.
 *
 * Distinct from
 * [com.conamobile.pdfkmp.layout.HorizontalAlignment], which positions a
 * whole element inside its container's cross axis. `TextAlign` instead
 * controls where each *line* sits inside the text block's own width.
 */
public enum class TextAlign {
    /** Left-aligned (in left-to-right scripts). The default. */
    Start,

    /** Each line centred in the available width. */
    Center,

    /** Right-aligned (in left-to-right scripts). */
    End,

    /**
     * Word-spaced to fill the available width. The last line of every hard
     * paragraph stays naturally aligned to the start so it doesn't gap out
     * awkwardly — matching the convention every word processor follows.
     */
    Justify,
}
