package com.conamobile.pdfkmp.viewer

/**
 * `true` on platforms that can burn in-viewer [PdfViewerAnnotation]s into
 * the encoded PDF bytes via [writeAnnotationsIntoPdf] — **Desktop (JVM)**
 * only, where PdfBox exposes a writable PDF model. Android and iOS return
 * `false`:
 *
 * - **Android** — `android.graphics.pdf.PdfRenderer` is read-only; there is
 *   no platform API to add an annotation to an existing PDF.
 * - **iOS** — PDFKit *can* write annotations, but a robust implementation
 *   (and the `PDFDocument.dataRepresentation` round-trip) is deferred; the
 *   actual currently returns `null` so the overlay-only behaviour is
 *   unchanged there.
 *
 * Used to decide whether the viewer's download / save action can offer the
 * annotated bytes instead of the originals (see [KmpPdfViewer]). When this
 * is `false`, highlights stay overlay-only and share / save / print export
 * the untouched original document.
 */
internal expect val pdfViewerSupportsAnnotationExport: Boolean

/**
 * Writes the in-viewer highlight [annotations] into [pdf] as real PDF
 * highlight (text-markup) annotations and returns the new bytes, or `null`
 * when the platform can't write annotations (see
 * [pdfViewerSupportsAnnotationExport]) or the write fails.
 *
 * Each [PdfViewerAnnotation] becomes a `Highlight` text-markup annotation on
 * its [PdfViewerAnnotation.pageIndex] page: the box is converted from the
 * viewer's coordinate space (PDF points, **top-left origin, Y down**) into
 * the PDF's native **bottom-left origin** space, the annotation colour is
 * mapped onto the annotation's `/C` colour, and a constant interior opacity
 * (`/CA`) keeps the highlight translucent so the underlying text stays
 * legible — matching the on-screen overlay look. The result opens with the
 * highlights visible in any standard PDF reader.
 *
 * The page's media-box height is needed to flip the Y axis, so the box is
 * clamped to the page bounds it actually lands on. Annotations whose
 * [PdfViewerAnnotation.pageIndex] is out of range are skipped.
 *
 * Runs off the main thread (the JVM backend uses [kotlinx.coroutines.Dispatchers.IO]).
 * Never throws for a malformed document — a failed write degrades to `null`
 * so the caller can fall back to the original bytes.
 *
 * @param pdf the original encoded `%PDF-…` bytes.
 * @param annotations the highlights to burn in; an empty list returns a copy
 *   of the original bytes unchanged (no-op write).
 */
internal expect suspend fun writeAnnotationsIntoPdf(
    pdf: ByteArray,
    annotations: List<PdfViewerAnnotation>,
): ByteArray?
