package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Stateful handle around an opened PDF document. The viewer creates one
 * per [PdfSource] inside a [androidx.compose.runtime.DisposableEffect]
 * and routes per-page render requests through it so each
 * [androidx.compose.foundation.lazy.LazyColumn] item only allocates the
 * bitmap it actually needs to draw.
 *
 * Eager full-document rasterisation (the previous shape of this API)
 * works for two-page invoices but quickly turns into hundreds of MB of
 * heap pressure on real-world documents. Lazy per-page rendering is
 * what makes the viewer usable for catalogues, manuals, and books.
 *
 * The encoded `%PDF-…` bytes the share sheet hands out are unaffected
 * — rasterisation only ever happens to fill the on-screen preview.
 */
internal expect class PdfPageRenderer {

    /** Total number of pages in the document. */
    val pageCount: Int

    /**
     * Intrinsic dimensions for every page, in PDF points. Pre-computed
     * at open time so the viewer can reserve correctly-sized
     * placeholders before any bitmap exists — this keeps `LazyColumn`
     * scrolling smooth as pages stream in.
     */
    val pageSizes: List<PageSize>

    /**
     * Rasterises [index] into an [ImageBitmap] scaled by [density].
     * Returns `null` when the page cannot be rendered (corrupt input,
     * out-of-range index, etc.).
     *
     * Implementations must be safe to call concurrently from multiple
     * coroutines — the Android backend serialises through a [Mutex]
     * because [android.graphics.pdf.PdfRenderer] only allows one open
     * page at a time; the iOS PDFKit backend is naturally re-entrant.
     */
    suspend fun renderPage(index: Int, density: Float): ImageBitmap?

    /** Releases the underlying file descriptor / native handle. */
    fun close()
}

/**
 * Opens an in-memory PDF for lazy per-page rendering. Returns `null`
 * when [bytes] is empty or the platform decoder rejects the payload —
 * the viewer surfaces an error UI when this happens.
 */
internal expect suspend fun openPdfRenderer(bytes: ByteArray): PdfPageRenderer?
