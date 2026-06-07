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
     * When [invert] is `true` the produced bitmap is colour-inverted
     * per RGB channel (white → near-black, black → white) while alpha
     * is preserved — the dark-mode reading surface exposed via
     * [KmpPdfViewer]'s `invertColors` flag. The inversion is applied to
     * the rasterised preview only; the encoded PDF bytes are never
     * touched.
     *
     * Implementations must be safe to call concurrently from multiple
     * coroutines — the Android backend serialises through a [Mutex]
     * because [android.graphics.pdf.PdfRenderer] only allows one open
     * page at a time; the iOS PDFKit backend is naturally re-entrant.
     */
    suspend fun renderPage(index: Int, density: Float, invert: Boolean): ImageBitmap?

    /** Releases the underlying file descriptor / native handle. */
    fun close()
}

/**
 * Outcome of an [openPdfRenderer] attempt — either a usable renderer or a
 * typed reason it couldn't open, so the viewer can show "wrong / missing
 * password" distinctly from a generic decode failure.
 */
internal sealed interface PdfOpenResult {

    /** The document opened; [renderer] is ready for per-page rendering. */
    class Success(val renderer: PdfPageRenderer) : PdfOpenResult

    /**
     * The document is encrypted and the supplied [password] (possibly
     * `null`) didn't unlock it — on Desktop the right password reopens it;
     * on Android encrypted files can't be opened at all.
     */
    object PasswordRequired : PdfOpenResult

    /** The bytes are empty, truncated, or otherwise un-decodable. */
    object CannotOpen : PdfOpenResult
}

/**
 * Opens an in-memory PDF for lazy per-page rendering.
 *
 * [password] unlocks an encrypted document — pass the user/owner password
 * when one is known. A `null` password is fine for unencrypted documents;
 * an encrypted document with a missing / wrong password yields
 * [PdfOpenResult.PasswordRequired] instead of a generic failure so the
 * viewer can surface a password message. Android can never open encrypted
 * documents (its `PdfRenderer` has no password API), so it always reports
 * [PdfOpenResult.PasswordRequired] for an encrypted payload regardless of
 * [password].
 */
internal expect suspend fun openPdfRenderer(
    bytes: ByteArray,
    password: String?,
): PdfOpenResult
