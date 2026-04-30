package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont

/**
 * Per-document handle returned by a [PdfDriverFactory].
 *
 * A driver is **stateful** and **single-use**: produce pages one at a time
 * with [beginPage] / [endPage], then call [finish] exactly once to obtain the
 * encoded PDF bytes. Calling any method after [finish] is undefined.
 *
 * The expected lifecycle is:
 * ```
 * val driver = factory.create(metadata, customFonts)
 * val canvas1 = driver.beginPage(PageSize.A4)
 * // ... draw on canvas1 ...
 * driver.endPage()
 * val canvas2 = driver.beginPage(PageSize.A4)
 * // ... draw on canvas2 ...
 * driver.endPage()
 * val bytes = driver.finish()
 * ```
 *
 * Drivers are not thread-safe and must be used from a single coroutine /
 * thread.
 */
public interface PdfDriver {

    /**
     * Font measurement service backed by the same renderer the canvas uses.
     *
     * The layout engine reads from this to decide line wrapping and page
     * breaking before any draw call happens — so the metrics must already be
     * accurate after [PdfDriverFactory.create] returns, even before the first
     * page is opened.
     */
    public val fontMetrics: FontMetrics

    /**
     * Starts a new page and returns a fresh [PdfCanvas] for it. The previous
     * canvas (if any) becomes invalid; do not retain a reference to it.
     *
     * @param size physical dimensions of the new page in PDF points.
     */
    public fun beginPage(size: PageSize): PdfCanvas

    /**
     * Closes the page previously opened with [beginPage]. Must be called
     * before either another [beginPage] or [finish].
     */
    public fun endPage()

    /**
     * Finalises the document and returns its encoded bytes.
     *
     * After this call the driver releases any underlying native resources
     * and must not be used again.
     */
    public fun finish(): ByteArray
}

/**
 * Factory for [PdfDriver] instances.
 *
 * One factory exists per platform (Android, iOS, eventually Desktop / Web).
 * The factory is the only place where platform-specific initialisation
 * happens — registering custom fonts with the OS, configuring the underlying
 * PDF context, attaching metadata.
 *
 * Library users normally don't touch a factory directly; the top-level
 * `pdf { ... }` entry resolves the platform default through
 * [com.conamobile.pdfkmp.defaultPdfDriverFactory]. Inject a custom factory
 * only for advanced use cases such as test fakes or alternate PDF backends.
 */
public interface PdfDriverFactory {

    /**
     * Creates a driver primed for a brand-new document.
     *
     * @param metadata fields written into the PDF info dictionary.
     * @param customFonts every [PdfFont.Custom] referenced anywhere in the
     *   document. The factory is responsible for registering these with the
     *   underlying platform font manager so subsequent [PdfCanvas.drawText]
     *   calls can resolve them by name.
     */
    public fun create(
        metadata: PdfMetadata,
        customFonts: List<PdfFont.Custom>,
    ): PdfDriver
}
