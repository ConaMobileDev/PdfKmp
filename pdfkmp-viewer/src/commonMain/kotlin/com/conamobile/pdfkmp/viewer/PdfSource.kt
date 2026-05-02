package com.conamobile.pdfkmp.viewer

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.text.PdfHyperlink
import com.conamobile.pdfkmp.text.PdfTextRun

/**
 * Supplies the bytes of an encoded PDF to a [PdfViewer].
 *
 * Two flavours coexist:
 *
 * - [Bytes] — opaque `%PDF-…` payload. Used when the source comes from
 *   the network, disk, an `ACTION_OPEN_DOCUMENT` picker, etc. The
 *   viewer rasterises the bytes through the platform's native PDF
 *   renderer and that's it — no text selection, because nothing in the
 *   stream identifies which pixels are text.
 *
 * - [Document] — bytes plus the laid-out text runs that produced them.
 *   Built automatically by [of] when the caller hands in a
 *   [PdfDocument]. The viewer overlays an invisible, selectable text
 *   layer on top of each rasterised page so users can long-press,
 *   drag, and copy text the same way they would in Apple Books or
 *   Samsung Notes — but only for documents authored through the
 *   PdfKmp DSL, which is the only place we have ground-truth text
 *   positions for free.
 */
public sealed interface PdfSource {

    /**
     * Opaque PDF payload — bytes only, no associated text-position
     * data. Yields a viewer with rasterised pages but no selectable
     * text layer.
     *
     * @property bytes raw `%PDF-…` bytes. The viewer keeps a reference
     *   for the lifetime of the composition; do not mutate the array
     *   after passing it in.
     */
    public class Bytes(public val bytes: ByteArray) : PdfSource

    /**
     * PDF payload paired with the laid-out text runs and hyperlink
     * annotations the renderer produced. Lets [PdfViewer] mount a
     * transparent, selectable text overlay on each page and attach a
     * clickable layer for hyperlink targets.
     *
     * @property bytes raw `%PDF-…` bytes — same shape as [Bytes].
     * @property textRuns every text run captured during rendering,
     *   indexed by page via [PdfTextRun.pageIndex].
     * @property hyperlinks every hyperlink annotation captured during
     *   rendering, indexed by page via [PdfHyperlink.pageIndex].
     */
    public class Document(
        public val bytes: ByteArray,
        public val textRuns: List<PdfTextRun>,
        public val hyperlinks: List<PdfHyperlink>,
    ) : PdfSource

    public companion object {

        /**
         * Convenience factory for a [PdfDocument] built through the
         * PdfKmp DSL. Returns the [Document] variant so the viewer can
         * surface the document's text runs as a selectable overlay
         * and its hyperlinks as a clickable layer.
         */
        public fun of(document: PdfDocument): PdfSource = Document(
            bytes = document.toByteArray(),
            textRuns = document.textRuns,
            hyperlinks = document.hyperlinks,
        )

        /** Convenience factory for raw bytes — same as `Bytes(bytes)`. */
        public fun of(bytes: ByteArray): PdfSource = Bytes(bytes)
    }
}

/** Returns the underlying byte array regardless of the variant. */
internal fun PdfSource.bytes(): ByteArray = when (this) {
    is PdfSource.Bytes -> bytes
    is PdfSource.Document -> bytes
}

/** Returns the text runs the source carries, or empty if none were captured. */
internal fun PdfSource.textRuns(): List<PdfTextRun> = when (this) {
    is PdfSource.Bytes -> emptyList()
    is PdfSource.Document -> textRuns
}

/** Returns the hyperlinks the source carries, or empty if none were captured. */
internal fun PdfSource.hyperlinks(): List<PdfHyperlink> = when (this) {
    is PdfSource.Bytes -> emptyList()
    is PdfSource.Document -> hyperlinks
}
