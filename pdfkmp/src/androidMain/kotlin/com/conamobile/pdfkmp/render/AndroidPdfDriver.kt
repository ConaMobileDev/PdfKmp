package com.conamobile.pdfkmp.render

import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
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
        return AndroidPdfCanvas(page.canvas, metrics)
    }

    override fun endPage() {
        val page = currentPage ?: error("endPage() called without a matching beginPage()")
        document.finishPage(page)
        currentPage = null
    }

    override fun finish(): ByteArray {
        check(currentPage == null) { "endPage() must be called before finish()" }
        return try {
            ByteArrayOutputStream().use { stream ->
                // Android's PdfDocument has no API for setting metadata in the
                // info dictionary; the title/author the user supplied is
                // intentionally not written until we either switch to a
                // lower-level encoder or post-process the bytes. The metadata
                // value is kept reachable so we don't drop user data — see
                // https://developer.android.com/reference/android/graphics/pdf/PdfDocument
                // for the API limitation.
                @Suppress("UNUSED_VARIABLE") val pendingMetadata = metadata
                document.writeTo(stream)
                stream.toByteArray()
            }
        } finally {
            document.close()
            registry.cleanup()
        }
    }
}
