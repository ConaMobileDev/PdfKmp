package com.conamobile.pdfkmp.style

/**
 * What to do with text that exceeds [TextStyle.maxLines].
 *
 * Only consulted when `maxLines` is set — unbounded paragraphs never
 * overflow vertically because the page-break machinery takes over instead.
 */
public enum class TextOverflow {
    /** Drop the overflowing lines without any visual marker. */
    Clip,

    /**
     * Drop the overflowing lines and replace the end of the last visible
     * line with a single ellipsis character (`…`), trimming characters
     * until the result fits the paragraph width.
     */
    Ellipsis,
}
