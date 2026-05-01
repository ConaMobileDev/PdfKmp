package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Vector-rasterises every page of an encoded PDF to an [ImageBitmap]
 * using the host platform's PDF rendering pipeline.
 *
 * The library never asks the platform to embed a pre-rasterised bitmap —
 * the rasterisation that happens here is purely a *display* step driven
 * by the fact that Compose draws bitmaps, not vector PDF. The encoded
 * PDF stays vector and stays sharp at any zoom level — the bytes that
 * reach the share sheet are exactly what the document author produced.
 *
 * **Android** — `android.graphics.pdf.PdfRenderer` opens the file
 * descriptor and renders each page into an `ARGB_8888` bitmap with the
 * `RENDER_MODE_FOR_DISPLAY` mode. Pages are scaled by [density] so a
 * screen-sized preview retains crisp edges on retina displays.
 *
 * **iOS** — `CoreGraphics.CGPDFDocument` opens the document; each page
 * is drawn into a `CGBitmapContext` via `CGContextDrawPDFPage`, then
 * wrapped as an `ImageBitmap` via `Skia.Image.makeFromEncoded(...)`
 * (after re-encoding to PNG for cross-cutting compatibility with
 * Compose Multiplatform's iOS image bridge).
 *
 * @param bytes encoded PDF bytes (the `%PDF-…` payload).
 * @param density multiplicative scaling factor applied to each page's
 *   intrinsic point size before rasterisation. `2f` doubles the pixel
 *   density (good default for retina screens); pass `3f` for extra
 *   sharpness when the viewer fills a large surface.
 * @return one [ImageBitmap] per page, in document order. Empty when
 *   [bytes] is not a parseable PDF.
 */
internal expect suspend fun renderPdfPages(
    bytes: ByteArray,
    density: Float,
): List<ImageBitmap>
