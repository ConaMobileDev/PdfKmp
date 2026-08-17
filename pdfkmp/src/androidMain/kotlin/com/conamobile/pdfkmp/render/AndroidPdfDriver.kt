package com.conamobile.pdfkmp.render

import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.pdfwriter.PdfNavigation
import com.conamobile.pdfkmp.pdfwriter.PdfPatcher
import com.conamobile.pdfkmp.style.PdfFont
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * [PdfDriver] backed by [android.graphics.pdf.PdfDocument].
 *
 * The driver delegates page management to Android's system PDF generator,
 * which produces vector PDFs out of the box: every text glyph drawn through
 * [PdfCanvas.drawText] becomes a glyph reference in the resulting PDF
 * (sharp at any zoom), and every shape becomes a vector path.
 *
 * Page numbers are 1-based per Android API. Custom fonts are pre-registered
 * via [AndroidFontRegistry] before the first page is opened so measurement
 * and drawing both have access to the resolved [Typeface].
 *
 * The driver is single-use: call [beginPage] / [endPage] for each page, then
 * [finish] once. Calling any method afterwards is undefined.
 */
internal class AndroidPdfDriver(
    private val metadata: PdfMetadata,
    customFonts: List<PdfFont.Custom>,
    private val cacheDir: File,
) : PdfDriver {

    private val document = AndroidPdfDocument()
    private val registry = AndroidFontRegistry(cacheDir)
    private val metrics = AndroidFontMetrics(registry)

    // Links, named destinations, and outline entries the canvas records while
    // drawing — none of which Android's PdfDocument can emit. The patcher in
    // finish() turns these into real PDF annotations / outline / info dict.
    private val navigation = PdfNavigation()

    private var currentPage: AndroidPdfDocument.Page? = null
    private var pageNumber = 0

    init {
        registry.preregister(customFonts)
    }

    override val fontMetrics: FontMetrics get() = metrics

    override fun beginPage(size: PageSize): PdfCanvas {
        check(currentPage == null) { "endPage() must be called before beginPage()" }
        pageNumber += 1
        val info = AndroidPdfDocument.PageInfo
            .Builder(size.width.value.toInt(), size.height.value.toInt(), pageNumber)
            .create()
        val page = document.startPage(info)
        currentPage = page
        // pageNumber is 1-based (Android API); the patcher indexes pages 0-based.
        return AndroidPdfCanvas(page.canvas, metrics, pageNumber - 1, navigation)
    }

    override fun endPage() {
        val page = currentPage ?: error("endPage() called without a matching beginPage()")
        document.finishPage(page)
        currentPage = null
    }

    override fun finish(): ByteArray {
        check(currentPage == null) { "endPage() must be called before finish()" }
        val rawBytes = try {
            ByteArrayOutputStream().use { stream ->
                document.writeTo(stream)
                stream.toByteArray()
            }
        } finally {
            document.close()
            registry.cleanup()
        }
        // Android's PdfDocument has no API for the info dictionary, link
        // annotations, named destinations, or the outline. We post-process the
        // bytes it produced with an incremental update that adds exactly those
        // features. The patcher returns the input untouched on any parse
        // surprise, so the worst case is the prior silent no-op rather than a
        // corrupt file. See
        // https://developer.android.com/reference/android/graphics/pdf/PdfDocument
        // for the API limitation that makes this necessary.
        return PdfPatcher.apply(rawBytes, metadata, navigation)
    }

    override fun close() {
        // The renderer calls this on its abort path, where finish() never
        // ran — without it the registry's uniquely-named temp font files
        // (and the native document) would leak until the OS trims cacheDir.
        // Everything here is best-effort and idempotent, so a close() after
        // a successful finish() is a no-op.
        try {
            currentPage?.let { document.finishPage(it) }
        } catch (e: Exception) {
            // The page may be in an unusable state mid-abort; releasing the
            // document and the font files below is what actually matters.
        }
        currentPage = null
        try {
            document.close()
        } catch (e: Exception) {
            // Already closed by finish(), or never usable — nothing to free.
        }
        registry.cleanup()
    }
}
