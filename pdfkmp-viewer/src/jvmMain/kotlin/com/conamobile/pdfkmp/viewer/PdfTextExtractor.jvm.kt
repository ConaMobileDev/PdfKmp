package com.conamobile.pdfkmp.viewer

import com.conamobile.pdfkmp.text.PdfTextRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.util.Collections
import java.util.WeakHashMap

/** Desktop extracts text via PdfBox, so external-document search is supported. */
internal actual val pdfViewerSupportsTextExtraction: Boolean = true

/**
 * Parsed-once cache of extracted runs, keyed on the **identity** of the
 * source byte array. Extraction walks every glyph in the document, which
 * is far too expensive to repeat on each keystroke; the viewer hands the
 * same array in for every query of a given document, so the cache lets
 * repeated searches reuse the single parse.
 *
 * A [WeakHashMap] keys on identity for arrays (`ByteArray` uses
 * reference `equals` / `hashCode`) **and** lets entries be collected once
 * the viewer drops the document's bytes — so opening many documents in
 * one session doesn't leak their parsed text. Wrapped in a synchronised
 * view because searches can fire from several coroutines.
 */
private val parsedRunsCache: MutableMap<ByteArray, List<PdfTextRun>> =
    Collections.synchronizedMap(WeakHashMap())

/**
 * Desktop / JVM external-PDF search, backed by PdfBox's
 * [PDFTextStripper]. The document is parsed once into per-word
 * [PdfTextRun]s (cached by byte-array identity) and subsequent queries
 * reuse the shared, query-agnostic [searchPdfText] matcher — so the
 * highlight geometry and slicing behaviour are byte-for-byte identical
 * to the PdfKmp-authored search path.
 *
 * Runs on [Dispatchers.IO] because parsing + glyph walking is heap- and
 * CPU-heavy. Never throws: a malformed document degrades to an empty
 * list (search finds nothing) rather than crashing the viewer.
 */
internal actual suspend fun searchPdfBytes(
    bytes: ByteArray,
    query: String,
): List<PdfSearchHighlight> {
    if (bytes.isEmpty() || query.isBlank()) return emptyList()
    val runs = withContext(Dispatchers.IO) { extractRuns(bytes) }
    return searchPdfText(runs, query)
}

/** Returns the cached runs for [bytes], parsing on the first request. */
private fun extractRuns(bytes: ByteArray): List<PdfTextRun> =
    parsedRunsCache.getOrPut(bytes) {
        runCatching {
            Loader.loadPDF(bytes).use { document ->
                val stripper = WordPositionStripper()
                // Drive the stripper for its position side-effects; the
                // returned String is discarded — we only want the runs.
                stripper.getText(document)
                stripper.runs.toList()
            }
        }.getOrElse { emptyList() }
    }

/**
 * [PDFTextStripper] subclass that records per-word glyph rectangles
 * instead of (only) emitting a flat string.
 *
 * PdfBox hands [writeString] the laid-out [TextPosition]s for a chunk of
 * text. We read the **direction-adjusted** geometry (`getXDirAdj()` /
 * `getYDirAdj()` / `getWidthDirAdj()` / `getHeightDir()`): these are
 * already expressed with a **top-left origin, Y growing downward** — the
 * viewer's exact convention — and they fold in page rotation, so a
 * rotated page's highlights still land on the glyphs. `getYDirAdj()` is
 * the glyph's **top** edge (unlike `getY()`, which is the baseline), so
 * no baseline correction is needed. We accumulate positions into words
 * (splitting on whitespace) and union each word's glyph boxes into one
 * [PdfTextRun].
 *
 * Page numbering: [getCurrentPageNo] is **1-based**, so we subtract one
 * to match [PdfTextRun.pageIndex]'s zero-based convention.
 */
private class WordPositionStripper : PDFTextStripper() {

    /** Accumulated runs, in document order. */
    val runs: MutableList<PdfTextRun> = mutableListOf()

    init {
        // Keep the engine's natural reading order; we map positions
        // directly so we don't need sorted output.
        sortByPosition = false
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        // zero-based to match PdfTextRun.pageIndex.
        val pageIndex = currentPageNo - 1
        var word = WordAccumulator(pageIndex)
        for (position in textPositions) {
            // A space (or any blank glyph) terminates the current word.
            if (position.unicode.isBlank()) {
                word.toRunOrNull()?.let(runs::add)
                word = WordAccumulator(pageIndex)
                continue
            }
            word.add(position)
        }
        word.toRunOrNull()?.let(runs::add)
    }

    // The stripper writes nothing to a real Writer — positions are
    // captured in writeString. No-op the separators / document hooks so
    // the default implementation doesn't push markup into the (unused)
    // output buffer.
    override fun writeLineSeparator() { /* positions captured in writeString */ }
    override fun writeWordSeparator() { /* positions captured in writeString */ }
    override fun writePageStart() { /* no-op */ }
    override fun writePageEnd() { /* no-op */ }
    override fun startDocument(document: PDDocument) { /* no-op */ }
    override fun endDocument(document: PDDocument) { /* no-op */ }
}

/**
 * Builds one [PdfTextRun] from a sequence of [TextPosition]s belonging
 * to a single word. Tracks the unioned bounding box (top-left origin)
 * and the dominant font size so the run mirrors what a PdfKmp build
 * would have recorded.
 */
private class WordAccumulator(private val pageIndex: Int) {
    private val builder = StringBuilder()
    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var maxY = Float.MIN_VALUE
    private var fontSize = 0f

    fun add(position: TextPosition) {
        builder.append(position.unicode)
        // Direction-adjusted geometry: top-left origin, Y down, rotation
        // already folded in. getYDirAdj() is the glyph TOP edge.
        val left = position.xDirAdj
        val top = position.yDirAdj
        val right = left + position.widthDirAdj
        val bottom = top + position.heightDir
        if (left < minX) minX = left
        if (top < minY) minY = top
        if (right > maxX) maxX = right
        if (bottom > maxY) maxY = bottom
        // Last glyph's size wins — good enough for an overlay; words
        // rarely mix sizes mid-token.
        fontSize = position.fontSizeInPt
    }

    /**
     * Materialises the accumulated glyphs into a [PdfTextRun], or `null`
     * when the word is empty / degenerate (zero width) — those would
     * never produce a usable highlight and only bloat the search index.
     */
    fun toRunOrNull(): PdfTextRun? {
        if (builder.isEmpty()) return null
        val width = maxX - minX
        val height = maxY - minY
        if (width <= 0f || height <= 0f) return null
        return PdfTextRun(
            pageIndex = pageIndex,
            text = builder.toString(),
            xPoints = minX,
            yPoints = minY,
            widthPoints = width,
            heightPoints = height,
            fontSizePoints = if (fontSize > 0f) fontSize else height,
        )
    }
}
