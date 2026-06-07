package com.conamobile.pdfkmp.viewer

/**
 * Android has no public text-extraction API: [android.graphics.pdf.PdfRenderer]
 * only rasterises pages and exposes no glyph / text-position data. There
 * is therefore no fallback search path for arbitrary external PDFs on
 * Android — this always returns an empty list.
 *
 * Documents authored through the PdfKmp DSL are unaffected: they carry
 * their own [com.conamobile.pdfkmp.text.PdfTextRun]s
 * ([PdfSource.Document.textRuns]), search through [searchPdfText], and
 * never reach this fallback — so search keeps working for them on Android
 * exactly as before.
 */
internal actual suspend fun searchPdfBytes(
    bytes: ByteArray,
    query: String,
): List<PdfSearchHighlight> = emptyList()

/** Android has no PDF text API, so external-document search is unsupported. */
internal actual val pdfViewerSupportsTextExtraction: Boolean = false
