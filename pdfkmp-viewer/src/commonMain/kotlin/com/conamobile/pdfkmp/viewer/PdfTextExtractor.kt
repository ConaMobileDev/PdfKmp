package com.conamobile.pdfkmp.viewer

/**
 * Searches an **arbitrary** (non-PdfKmp) PDF for [query] and returns the
 * matched regions as [PdfSearchHighlight]s, so the viewer's search can
 * light up for documents that don't carry the captured
 * [com.conamobile.pdfkmp.text.PdfTextRun]s a `pdf { … }` build produces.
 *
 * This is the fallback path behind [PdfViewer]'s search. PdfKmp-authored
 * documents already ship their exact text positions
 * ([PdfSource.Document.textRuns]) and go through [searchPdfText] instead;
 * everything else (network downloads, file-picker results, bundled
 * third-party PDFs) goes through a platform text engine here.
 *
 * Returned coordinates **must match** the convention that [PdfViewer]
 * uses to paint highlights: PDF points with a **top-left origin and Y
 * growing downward**, sized against each page's media box — identical to
 * [searchPdfText]'s output. The viewer scales these points by the
 * on-screen page size the same way for both paths, so highlights line up
 * with the glyphs regardless of which engine produced them.
 *
 * Per-platform support:
 *
 * - **Desktop (JVM)** — PdfBox `PDFTextStripper` records per-word glyph
 *   rectangles; matching words are emitted as highlights. PdfBox's
 *   direction-adjusted geometry is already top-left / Y-down.
 * - **iOS** — `PDFDocument.findString(_:withOptions:)` returns a
 *   `PDFSelection` per hit; each selection's `boundsForPage` is flipped
 *   from PDFKit's bottom-left origin to the viewer's top-left
 *   convention.
 * - **Android** — `android.graphics.pdf.PdfRenderer` exposes **no text
 *   API**, so this returns an empty list and external-document search is
 *   unavailable there. PdfKmp-authored documents still search normally
 *   because they don't go through this path.
 *
 * Implementations run off the main thread and may be expensive on large
 * documents; callers invoke this once per (document, query) and cache
 * the parsed model where possible (see the search wiring in
 * [KmpPdfViewer]).
 *
 * Returns an empty list when [bytes] is empty, [query] is blank, the
 * platform decoder rejects the payload, or the platform has no text API.
 * Never throws for a malformed document — a failed search degrades to
 * "found nothing", not a crash.
 */
internal expect suspend fun searchPdfBytes(
    bytes: ByteArray,
    query: String,
): List<PdfSearchHighlight>

/**
 * `true` on platforms whose [searchPdfBytes] can find text inside an
 * arbitrary (non-PdfKmp) PDF — Desktop (PdfBox) and iOS (PDFKit) — and
 * `false` on Android, where [android.graphics.pdf.PdfRenderer] exposes
 * no text API.
 *
 * Used to decide whether to surface the search affordance for an
 * **external** document *before* any search has run: showing a search
 * button that can never find anything (Android external PDFs) would be a
 * dead end, so the chrome hides it there. Documents authored through the
 * PdfKmp DSL carry their own text and search regardless of this flag.
 */
internal expect val pdfViewerSupportsTextExtraction: Boolean
