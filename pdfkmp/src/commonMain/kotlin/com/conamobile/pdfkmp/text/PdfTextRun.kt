package com.conamobile.pdfkmp.text

/**
 * One laid-out piece of text on a rendered PDF page.
 *
 * The library emits a [PdfTextRun] for every wrapped line / styled
 * segment that reaches the canvas during rendering, capturing exactly
 * what was drawn and where. Consumers — most notably `:pdfkmp-viewer`
 * — use these runs to overlay an invisible, selectable text layer on
 * top of the rasterised preview, mirroring how Adobe Reader / Apple
 * Books / Samsung Notes implement copy-paste over a vector PDF.
 *
 * **Coordinates** are in PDF points (1 pt = 1/72 in), with a top-left
 * origin and Y growing downward — matching the rest of the public
 * surface ([com.conamobile.pdfkmp.render.PdfCanvas]). The bounding box
 * is `(xPoints, yPoints, widthPoints, heightPoints)`. `widthPoints` is
 * the advance width of the run's glyphs and `heightPoints` is
 * `ascent + descent` of the active style's font, so the rectangle
 * approximates the visible glyph footprint rather than a typographic
 * line slot.
 *
 * **Page numbering** is zero-based and follows the order in which the
 * driver produced pages. Slicing breaks and explicit
 * `MoveToNextPage` calls each advance the index, matching the page
 * indices a downstream PDF reader sees.
 *
 * @property pageIndex zero-based page the run belongs to.
 * @property text final wrapped string drawn on the page (no embedded
 *   newlines — each wrapped line is its own run).
 * @property xPoints left edge of the bounding box in PDF points.
 * @property yPoints top edge of the bounding box in PDF points.
 * @property widthPoints horizontal advance width of the run.
 * @property heightPoints sum of the active font's ascent + descent —
 *   the visible glyph height, not the typographic line height.
 * @property fontSizePoints font size of the active text style; useful
 *   for sizing an overlay glyph layer.
 */
public data class PdfTextRun(
    val pageIndex: Int,
    val text: String,
    val xPoints: Float,
    val yPoints: Float,
    val widthPoints: Float,
    val heightPoints: Float,
    val fontSizePoints: Float,
)
