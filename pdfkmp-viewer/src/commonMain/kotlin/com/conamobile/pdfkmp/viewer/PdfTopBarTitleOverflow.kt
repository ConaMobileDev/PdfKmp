package com.conamobile.pdfkmp.viewer

/**
 * How a [PdfViewerTopBar] title behaves when it is too long to fit the
 * space the bar can give it without pushing the action icons out of the
 * way.
 *
 * The bar always reserves room for the back affordance and the trailing
 * icons first, so those never shrink or disappear — the title yields,
 * and this enum picks *how* it yields.
 *
 * @see PdfViewerTopBar
 * @see PdfViewerTopBarClassicIos
 * @see PdfViewerTopBarMinimalMono
 */
public enum class PdfTopBarTitleOverflow {

    /**
     * Truncate the title to a single line and append an ellipsis
     * (`"Very long invoice n…"`). Matches the Android / Material
     * convention and is the default everywhere.
     */
    Ellipsis,

    /**
     * Keep the full title on a single line and scroll it horizontally
     * (a marquee) when — and only when — it overflows. Titles that
     * already fit stay static. Useful when truncating would hide a
     * meaningful tail of the filename.
     */
    Marquee,
}
