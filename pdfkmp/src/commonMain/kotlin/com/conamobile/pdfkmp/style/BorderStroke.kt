package com.conamobile.pdfkmp.style

import com.conamobile.pdfkmp.unit.Dp

/**
 * Outline drawn around a container.
 *
 * Reused by [com.conamobile.pdfkmp.dsl.column], [com.conamobile.pdfkmp.dsl.row],
 * [com.conamobile.pdfkmp.dsl.box], and [com.conamobile.pdfkmp.dsl.card] to
 * give them a coloured outline. Pass `null` instead of a [BorderStroke] to
 * skip the outline; `BorderStroke(0.dp, ...)` does the same.
 *
 * @property width stroke width in PDF points.
 * @property color solid stroke colour.
 * @property style solid / dashed / dotted pattern. Dashed and dotted
 *   borders are drawn edge-by-edge, so they apply to sharp-cornered
 *   rectangles; a container with a non-zero corner radius falls back to a
 *   solid outline because the dash phase cannot follow the arc cleanly.
 */
public data class BorderStroke(
    val width: Dp,
    val color: PdfColor,
    val style: LineStyle = LineStyle.Solid,
) {
    public companion object {
        /** Convenience for callers that read better with `BorderStroke.none`. */
        public val None: BorderStroke = BorderStroke(Dp.Zero, PdfColor.Transparent)
    }
}
