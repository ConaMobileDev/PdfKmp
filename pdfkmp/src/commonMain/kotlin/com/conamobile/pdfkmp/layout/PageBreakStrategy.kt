package com.conamobile.pdfkmp.layout

/**
 * Strategy used when a child element does not fit in the remaining vertical
 * space of the current page.
 *
 * Configured per page via
 * [com.conamobile.pdfkmp.dsl.PageScope.pageBreakStrategy], with the document
 * default supplied by
 * [com.conamobile.pdfkmp.dsl.DocumentScope.defaultPageBreakStrategy].
 */
public enum class PageBreakStrategy {

    /**
     * Render whatever portion of the element fits on the current page, then
     * continue the rest at the top of a new page.
     *
     * For text the split happens at line boundaries — never in the middle of
     * a word. For images the visible bottom of the image is drawn on the
     * current page and the remainder on the next page (TODO: image slicing
     * is not implemented yet; see [com.conamobile.pdfkmp.render.PdfCanvas.drawImage]).
     *
     * Use this when partial display is acceptable, e.g. long body text or
     * tall photographs in a magazine layout.
     */
    Slice,

    /**
     * Move the entire element to a new page if it would otherwise be split.
     *
     * The current page ends with whatever fit before the element, leaving
     * blank space at the bottom; the next page starts with the element in
     * full.
     *
     * Use this for figures, tables, headers, or anything where partial
     * display would be misleading. This is the safer default and matches the
     * `page-break-inside: avoid` behaviour familiar from CSS.
     */
    MoveToNextPage,
}
