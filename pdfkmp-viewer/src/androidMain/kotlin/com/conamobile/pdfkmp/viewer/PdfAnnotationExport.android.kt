package com.conamobile.pdfkmp.viewer

/**
 * Android has no writable-PDF API. `android.graphics.pdf.PdfRenderer` is
 * read-only and `PdfDocument` only authors brand-new documents (it can't
 * edit an existing one), so highlights can't be burned into the bytes here.
 */
internal actual val pdfViewerSupportsAnnotationExport: Boolean = false

/**
 * No-op on Android — see [pdfViewerSupportsAnnotationExport]. Always returns
 * `null` so the viewer falls back to the original (un-annotated) bytes.
 */
internal actual suspend fun writeAnnotationsIntoPdf(
    pdf: ByteArray,
    annotations: List<PdfViewerAnnotation>,
): ByteArray? = null
