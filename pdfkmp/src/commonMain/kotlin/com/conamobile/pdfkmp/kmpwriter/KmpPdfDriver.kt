package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.render.FontMetrics
import com.conamobile.pdfkmp.render.PdfCanvas
import com.conamobile.pdfkmp.render.PdfDriver
import com.conamobile.pdfkmp.style.PdfFont

/**
 * [PdfDriver] that writes a complete PDF 1.7 document from scratch in pure
 * Kotlin, with no platform PDF API — the backend the upcoming wasmJs (browser)
 * target builds on, validated on the JVM against PdfBox first.
 *
 * Each [beginPage] opens a fresh [KmpPage] whose [KmpPdfCanvas] appends
 * content-stream operators and collects per-page resources; nothing is
 * serialised until [finish], where every page, content stream, font,
 * graphics-state, shading, image XObject, link annotation, named destination,
 * outline entry, and the info dictionary are turned into numbered indirect
 * objects with byte-exact cross-reference offsets.
 *
 * Supported: Standard-14 Helvetica text (WinAnsi, no embedding); embedded
 * TrueType text via subsetted CIDFontType2 / Type0 (Identity-H) for
 * [PdfFont.Custom] fonts and for the bundled Inter fallback that covers
 * non-WinAnsi code points (e.g. Cyrillic), with a ToUnicode CMap for text
 * extraction; Flate-compressed content streams; vector shapes / paths / clips,
 * dashed & dotted lines, constant-alpha transparency and transparency groups,
 * axial & radial gradients, PNG (8-bit truecolor/grayscale, non-interlaced) and
 * JPEG image embedding, URI & internal-destination links, named destinations,
 * outline bookmarks, and the document info dictionary.
 *
 * Not supported (each warns once and is skipped): CFF/OpenType-PS custom fonts
 * (only TrueType outlines subset; falls back to Helvetica), code points with no
 * glyph in any available font (rendered as a missing-glyph box or `?`), synthetic
 * bold/italic for a single-face custom font, AcroForm fields (static fallback
 * kept), and [PdfMetadata.encryption] / [PdfMetadata.attachments] (ignored). The
 * driver is single-use and not thread-safe.
 */
internal class KmpPdfDriver(
    private val metadata: PdfMetadata,
    customFonts: List<PdfFont.Custom>,
    /** Forwarded to the assembler: Flate-compress content streams when `true`. */
    private val compressStreams: Boolean = true,
) : PdfDriver {

    private val textEncoder = WinAnsiTextEncoder()
    private val fontRegistry = KmpFontRegistry(customFonts)
    private val metrics = KmpFontMetrics(textEncoder, fontRegistry)
    private val navigation = KmpNavigation()
    private val pages = ArrayList<KmpPage>()

    private var currentPage: KmpPage? = null
    private var open = true

    init {
        if (metadata.encryption != null) {
            PdfLog.warn("Encryption is not supported by the pure-Kotlin PDF backend; the document is written unencrypted.")
        }
        if (metadata.attachments.isNotEmpty()) {
            PdfLog.warn("Embedded attachments are not supported by the pure-Kotlin PDF backend; they are omitted.")
        }
    }

    override val fontMetrics: FontMetrics get() = metrics

    override fun beginPage(size: PageSize): PdfCanvas {
        check(open) { "Driver has been finished" }
        check(currentPage == null) { "endPage() must be called before beginPage()" }
        val page = KmpPage(width = size.width.value, height = size.height.value)
        pages.add(page)
        currentPage = page
        return KmpPdfCanvas(page, pages.size - 1, navigation, textEncoder, fontRegistry)
    }

    override fun endPage() {
        checkNotNull(currentPage) { "endPage() called without a matching beginPage()" }
        currentPage = null
    }

    override fun finish(): ByteArray {
        check(open) { "Driver already finished" }
        check(currentPage == null) { "endPage() must be called before finish()" }
        open = false
        return KmpDocumentAssembler(metadata, pages, navigation, compressStreams).assemble()
    }
}
