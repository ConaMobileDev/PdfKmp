package com.conamobile.pdfkmp.text

/**
 * One hyperlink annotation captured during rendering of a PDF page.
 *
 * The library emits a [PdfHyperlink] for every call into
 * [com.conamobile.pdfkmp.render.PdfCanvas.linkAnnotation] — i.e. every
 * `link(url) { … }` block in the DSL or every `text { hyperlink = … }`
 * — so consumers (most notably `:pdfkmp-viewer`) can re-attach a
 * clickable affordance on top of the rasterised preview without
 * re-parsing the encoded PDF for `/Annot` entries.
 *
 * **Coordinates** are in PDF points (1 pt = 1/72 in) with a top-left
 * origin and Y growing downward — same convention as
 * [com.conamobile.pdfkmp.render.PdfCanvas]. Bounding box is
 * `(xPoints, yPoints, widthPoints, heightPoints)`.
 *
 * **Page numbering** is zero-based and matches the order in which the
 * driver produced pages, so it lines up with [PdfTextRun.pageIndex].
 *
 * @property pageIndex zero-based page the link belongs to.
 * @property xPoints left edge of the clickable region.
 * @property yPoints top edge of the clickable region.
 * @property widthPoints width of the clickable region.
 * @property heightPoints height of the clickable region.
 * @property url destination URL passed to `linkAnnotation`.
 */
public data class PdfHyperlink(
    val pageIndex: Int,
    val xPoints: Float,
    val yPoints: Float,
    val widthPoints: Float,
    val heightPoints: Float,
    val url: String,
)
