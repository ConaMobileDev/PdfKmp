@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.max

/**
 * iOS implementation of [PdfPageRenderer]. PDFKit's `PDFDocument`
 * holds the parsed page tree and is naturally thread-safe for
 * read-only access, so [renderPage] does not need its own mutex.
 *
 * Each render goes `PDFKit.thumbnailOfSize → UIImage → PNG → Skia`,
 * the same path used by the previous one-shot renderer — Compose
 * Multiplatform's iOS bridge decodes PNG straight into the
 * [ImageBitmap] type used by Android.
 *
 * `close()` is a no-op because Kotlin/Native ARC releases
 * `PDFDocument` automatically when the handle drops out of scope.
 */
internal actual class PdfPageRenderer private constructor(
    private val document: PDFDocument,
) {

    actual val pageCount: Int = document.pageCount.toInt()

    actual val pageSizes: List<PageSize> = (0 until pageCount).mapNotNull { i ->
        document.pageAtIndex(i.toULong())?.let { page ->
            page.boundsForBox(kPDFDisplayBoxMediaBox).useContents {
                PageSize(size.width.toFloat(), size.height.toFloat())
            }
        }
    }

    actual suspend fun renderPage(index: Int, density: Float): ImageBitmap? = withContext(Dispatchers.Default) {
        if (index !in 0 until pageCount) return@withContext null
        val page = document.pageAtIndex(index.toULong()) ?: return@withContext null
        val safeDensity = max(density, 0.5f).toDouble()
        val (pointWidth, pointHeight) = page.boundsForBox(kPDFDisplayBoxMediaBox).useContents {
            size.width to size.height
        }
        val pixelSize = CGSizeMake(
            width = pointWidth * safeDensity,
            height = pointHeight * safeDensity,
        )
        val image = page.thumbnailOfSize(pixelSize, forBox = kPDFDisplayBoxMediaBox)
        val pngData = UIImagePNGRepresentation(image) ?: return@withContext null
        SkiaImage.makeFromEncoded(pngData.toByteArray()).toComposeImageBitmap()
    }

    actual fun close() {
        // PDFDocument is reference-counted by the Kotlin/Native ARC
        // bridge; dropping the handle releases the underlying memory.
    }

    internal companion object {
        suspend fun open(bytes: ByteArray): PdfPageRenderer? = withContext(Dispatchers.Default) {
            if (bytes.isEmpty()) return@withContext null
            val nsData: NSData = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            // Kotlin/Native exposes `PDFDocument(data:)` as non-null even
            // though the Objective-C initializer is failable. Guarding on
            // pageCount catches a "successfully" returned but malformed
            // document — a freshly-allocated empty document reports 0.
            val document = PDFDocument(data = nsData)
            if (document.pageCount.toInt() == 0) null else PdfPageRenderer(document)
        }
    }
}

internal actual suspend fun openPdfRenderer(bytes: ByteArray): PdfPageRenderer? =
    PdfPageRenderer.open(bytes)

/** Copies an [NSData] payload into a Kotlin [ByteArray]. */
private fun NSData.toByteArray(): ByteArray {
    val length = length.toInt()
    if (length == 0) return ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length.toULong())
    }
    return out
}
