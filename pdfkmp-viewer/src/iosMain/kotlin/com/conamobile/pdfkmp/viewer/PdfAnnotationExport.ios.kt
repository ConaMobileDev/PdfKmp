package com.conamobile.pdfkmp.viewer

/**
 * iOS annotation export is deferred. PDFKit *can* author highlight
 * annotations (`PDFAnnotation` with `PDFAnnotationSubtypeHighlight`) and
 * re-serialise via `PDFDocument.dataRepresentation`, but a correct,
 * verified implementation (coordinate flip, quad points, colour mapping)
 * needs on-device validation that can't be done from this host. Until that
 * lands, the actual returns `null` and iOS keeps the overlay-only behaviour.
 */
internal actual val pdfViewerSupportsAnnotationExport: Boolean = false

/**
 * No-op on iOS for now — see [pdfViewerSupportsAnnotationExport]. Returns
 * `null` so the viewer falls back to the original (un-annotated) bytes.
 *
 * NOTE: marked for macOS verification — a future PDFKit-based
 * implementation should build `PDFAnnotation`s here.
 */
internal actual suspend fun writeAnnotationsIntoPdf(
    pdf: ByteArray,
    annotations: List<PdfViewerAnnotation>,
): ByteArray? = null
