package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGPDFContextAuthor
import platform.CoreGraphics.kCGPDFContextCreator
import platform.CoreGraphics.kCGPDFContextKeywords
import platform.CoreGraphics.kCGPDFContextSubject
import platform.CoreGraphics.kCGPDFContextTitle
import platform.Foundation.NSMutableData
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetCurrentContext

/**
 * [PdfDriver] backed by `UIGraphicsPDFRenderer`-style APIs.
 *
 * `UIGraphicsBeginPDFContextToData` writes a vector PDF: every Core Graphics
 * call made through the underlying [PdfCanvas] is recorded as a vector
 * operation, so text and shapes stay sharp at any zoom level — exactly the
 * behaviour required by PdfKmp's design rules.
 *
 * Custom and bundled fonts are pre-registered through [IosFontRegistry] so
 * they're queryable by name from the moment the first canvas is requested.
 *
 * The driver is single-use: pair every [beginPage] with an [endPage] and
 * call [finish] exactly once.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPdfDriver(
    private val metadata: PdfMetadata,
    customFonts: List<PdfFont.Custom>,
) : PdfDriver {

    private val fonts = IosFontRegistry()
    private val backingData = NSMutableData()
    private val metricsImpl = IosFontMetrics(fonts)
    private var open: Boolean = true
    private var pageOpen: Boolean = false

    init {
        fonts.preregister(customFonts)
        UIGraphicsBeginPDFContextToData(
            data = backingData,
            bounds = CGRectMake(0.0, 0.0, 0.0, 0.0),
            documentInfo = buildDocumentInfo(metadata),
        )
    }

    override val fontMetrics: FontMetrics get() = metricsImpl

    override fun beginPage(size: PageSize): PdfCanvas {
        check(open) { "Driver has been finished" }
        check(!pageOpen) { "endPage() must be called before beginPage()" }
        UIGraphicsBeginPDFPageWithInfo(
            bounds = CGRectMake(0.0, 0.0, size.width.value.toDouble(), size.height.value.toDouble()),
            pageInfo = null,
        )
        pageOpen = true
        val ctx = UIGraphicsGetCurrentContext()
            ?: error("UIGraphicsGetCurrentContext returned null inside an open PDF page")
        return IosPdfCanvas(ctx, fonts)
    }

    override fun endPage() {
        check(pageOpen) { "endPage() called without a matching beginPage()" }
        // UIGraphicsBeginPDFPageWithInfo opens a new implicit page on the next
        // call; there's no explicit endPage in UIKit. The flag exists for
        // lifecycle assertions only.
        pageOpen = false
    }

    override fun finish(): ByteArray {
        check(open) { "Driver already finished" }
        check(!pageOpen) { "endPage() must be called before finish()" }
        UIGraphicsEndPDFContext()
        open = false
        try {
            return backingData.toByteArray()
        } finally {
            fonts.cleanup()
        }
    }

    private fun buildDocumentInfo(metadata: PdfMetadata): Map<Any?, Any>? {
        val attributes = mutableMapOf<Any?, Any>()
        metadata.title?.let { attributes[kCGPDFContextTitle] = it }
        metadata.author?.let { attributes[kCGPDFContextAuthor] = it }
        metadata.subject?.let { attributes[kCGPDFContextSubject] = it }
        metadata.keywords?.let { attributes[kCGPDFContextKeywords] = it }
        metadata.creator?.let { attributes[kCGPDFContextCreator] = it }
        return attributes.takeIf { it.isNotEmpty() }
    }
}

/**
 * Copies an [NSMutableData] into a Kotlin [ByteArray]. Returns an empty array
 * if the underlying buffer is empty.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSMutableData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val pointer = this.bytes ?: return ByteArray(0)
    return pointer.readBytes(length)
}
