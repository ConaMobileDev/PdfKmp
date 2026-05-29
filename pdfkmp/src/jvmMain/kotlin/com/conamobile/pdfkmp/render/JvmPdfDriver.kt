package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream

/**
 * [PdfDriver] backed by Apache PdfBox.
 *
 * PdfBox builds the PDF entirely in memory: each [beginPage] adds a [PDPage]
 * and opens a [PDPageContentStream] that [JvmPdfCanvas] draws into, and
 * [finish] serialises the whole [PDDocument] to bytes. Fonts are embedded as
 * subset-enabled `Type0` fonts, so the only glyphs written are those actually
 * drawn — and every glyph and shape stays vector.
 *
 * Unlike the Android backend, PdfBox exposes the document info dictionary, so
 * the user-supplied [PdfMetadata] (title, author, subject, …) is written
 * verbatim.
 *
 * The driver is single-use and not thread-safe: pair every [beginPage] with
 * an [endPage] and call [finish] exactly once.
 */
internal class JvmPdfDriver(
    metadata: PdfMetadata,
    customFonts: List<PdfFont.Custom>,
) : PdfDriver {

    private val document = PDDocument()
    private val registry = JvmFontRegistry(document)
    private val metrics = JvmFontMetrics(registry)

    private var currentPage: PDPage? = null
    private var currentStream: PDPageContentStream? = null
    private var open = true

    init {
        applyMetadata(metadata)
        registry.preregister(customFonts)
    }

    override val fontMetrics: FontMetrics get() = metrics

    override fun beginPage(size: PageSize): PdfCanvas {
        check(open) { "Driver has been finished" }
        check(currentPage == null) { "endPage() must be called before beginPage()" }
        val width = size.width.value
        val height = size.height.value
        val page = PDPage(PDRectangle(width, height))
        document.addPage(page)
        val stream = PDPageContentStream(document, page)
        currentPage = page
        currentStream = stream
        return JvmPdfCanvas(document, page, stream, height, registry)
    }

    override fun endPage() {
        val stream = currentStream ?: error("endPage() called without a matching beginPage()")
        stream.close()
        currentStream = null
        currentPage = null
    }

    override fun finish(): ByteArray {
        check(open) { "Driver already finished" }
        check(currentStream == null) { "endPage() must be called before finish()" }
        return try {
            ByteArrayOutputStream().use { out ->
                // Font subsetting happens here, based on the glyphs drawn.
                document.save(out)
                out.toByteArray()
            }
        } finally {
            document.close()
            open = false
        }
    }

    /**
     * Releases the [PDDocument] (and any open content stream) without
     * producing output — used by the renderer when a draw call throws before
     * [finish]. Idempotent and safe to call after [finish].
     */
    override fun close() {
        if (!open) return
        open = false
        currentStream?.let { runCatching { it.close() } }
        currentStream = null
        currentPage = null
        runCatching { document.close() }
    }

    private fun applyMetadata(metadata: PdfMetadata) {
        val info = document.documentInformation
        metadata.title?.let { info.title = it }
        metadata.author?.let { info.author = it }
        metadata.subject?.let { info.subject = it }
        metadata.keywords?.let { info.keywords = it }
        metadata.creator?.let { info.creator = it }
        metadata.producer?.let { info.producer = it }
    }
}
