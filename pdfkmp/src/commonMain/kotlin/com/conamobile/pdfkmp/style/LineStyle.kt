package com.conamobile.pdfkmp.style

/**
 * Stroke pattern applied to lines, dividers, and shape outlines.
 *
 * Reused by [com.conamobile.pdfkmp.dsl.divider] and (eventually) by
 * `drawLine`, `strokeRect`, `drawPath` so any vector outline can be
 * dashed or dotted instead of solid.
 */
public enum class LineStyle {
    /** Continuous line, no gaps. */
    Solid,

    /** Long dashes separated by gaps roughly equal to the dash length. */
    Dashed,

    /** Round dots — short on/off pattern that reads as a discrete sequence. */
    Dotted,
}
