@file:OptIn(ExperimentalForeignApi::class)

package com.conamobile.pdfkmp.viewer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCaseInsensitiveSearch
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.PDFKit.PDFSelection
import platform.PDFKit.kPDFDisplayBoxMediaBox
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/** iOS extracts text via PDFKit, so external-document search is supported. */
internal actual val pdfViewerSupportsTextExtraction: Boolean = true

/**
 * iOS external-PDF search, backed by PDFKit's selection geometry.
 *
 * PDFKit's [PDFDocument.findString] does case-insensitive matching for
 * us and returns one [PDFSelection] per hit. Each selection is split into
 * per-line sub-selections ([PDFSelection.selectionsByLine]) so a match
 * that wraps produces one highlight rectangle per visual line, and each
 * line's [PDFSelection.boundsForPage] (PDFKit's bottom-left origin, Y up)
 * is flipped into the viewer's top-left / Y-down point space — identical
 * to the coordinate convention [searchPdfText] produces for
 * PdfKmp-authored documents.
 *
 * Runs on [Dispatchers.Default]; never throws — a malformed document or a
 * blank query degrades to an empty list.
 *
 * NOTE: targets the Apple PDFKit toolchain — cannot be compiled on a
 * non-macOS host; needs verification on macOS / a simulator.
 */
@OptIn(BetaInteropApi::class)
internal actual suspend fun searchPdfBytes(
    bytes: ByteArray,
    query: String,
): List<PdfSearchHighlight> = withContext(Dispatchers.Default) {
    if (bytes.isEmpty() || query.isBlank()) return@withContext emptyList()

    val nsData: NSData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    val document = PDFDocument(data = nsData)
    if (document.pageCount.toInt() == 0) return@withContext emptyList()

    // Cache page heights keyed by index so the Y-flip doesn't re-query
    // the media box for every match on the same page.
    val pageHeights = HashMap<Int, Float>()
    fun pageHeight(page: PDFPage): Float {
        val index = document.indexForPage(page).toInt()
        return pageHeights.getOrPut(index) {
            page.boundsForBox(kPDFDisplayBoxMediaBox).useContents { size.height.toFloat() }
        }
    }

    val highlights = mutableListOf<PdfSearchHighlight>()

    // findString returns NSArray<PDFSelection> (or null); each element is
    // one match.
    val matches = document.findString(query, withOptions = NSCaseInsensitiveSearch)
        ?: return@withContext emptyList()
    for (matchAny in matches) {
        val match = matchAny as? PDFSelection ?: continue
        // Split into per-line selections so a wrapped match yields one
        // rectangle per visual line instead of a single bounding box that
        // would over-cover the gap between lines.
        val lines = match.selectionsByLine()
        val lineSelections: List<PDFSelection> =
            if (!lines.isNullOrEmpty()) {
                lines.mapNotNull { it as? PDFSelection }
            } else {
                listOf(match)
            }
        for (line in lineSelections) {
            for (pageAny in line.pages.orEmpty()) {
                val page = pageAny as? PDFPage ?: continue
                val pageIndex = document.indexForPage(page).toInt()
                val height = pageHeight(page)
                // boundsForPage returns CValue<CGRect>; read the struct
                // fields directly (origin.x/y, size.width/height) the same
                // way the renderer reads boundsForBox.
                val highlight = line.boundsForPage(page).useContents {
                    val rectX = origin.x.toFloat()
                    val rectY = origin.y.toFloat()
                    val rectW = size.width.toFloat()
                    val rectH = size.height.toFloat()
                    if (rectW <= 0f || rectH <= 0f) {
                        null
                    } else {
                        // PDFKit bounds are bottom-left origin (Y up); flip
                        // to the viewer's top-left origin (Y down).
                        PdfSearchHighlight(
                            pageIndex = pageIndex,
                            xPoints = rectX,
                            yPoints = height - (rectY + rectH),
                            widthPoints = rectW,
                            heightPoints = rectH,
                        )
                    }
                }
                if (highlight != null) highlights += highlight
            }
        }
    }

    // Document order (page → top-to-bottom) so prev/next navigation
    // matches the on-screen reading order, mirroring searchPdfText.
    highlights.sortedWith(compareBy({ it.pageIndex }, { it.yPoints }, { it.xPoints }))
}
