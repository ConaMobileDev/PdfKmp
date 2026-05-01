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
 * iOS implementation that walks the PDF with PDFKit. Each page is
 * rasterised through [platform.PDFKit.PDFPage.thumbnailOfSize] —
 * PDFKit takes care of the coordinate flip and DPI scaling internally
 * and returns a ready-to-use [platform.UIKit.UIImage]. The image is
 * re-encoded to PNG and decoded by Skia so the resulting [ImageBitmap]
 * is the same type the Android backend produces.
 *
 * The PNG round-trip is the cheapest way to get a Compose-compatible
 * [ImageBitmap] out of a UIImage in Kotlin/Native — Compose
 * Multiplatform's iOS image pipeline goes through Skia for decode, so
 * any other path would still end up encoding to a known format first.
 *
 * The vector quality of the source document is preserved — PDFKit
 * consumes the PDF's path geometry and produces pixels at the requested
 * density, so retina-sharpness on a 3x device is one `density = 3f`
 * argument away. The encoded `%PDF-…` bytes that flow into the share
 * sheet are byte-for-byte identical to what came in.
 */
internal actual suspend fun renderPdfPages(
    bytes: ByteArray,
    density: Float,
): List<ImageBitmap> = withContext(Dispatchers.Default) {
    if (bytes.isEmpty()) return@withContext emptyList()
    val safeDensity = max(density, 0.5f).toDouble()

    val nsData: NSData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }

    // Kotlin/Native's PDFKit binding currently exposes PDFDocument(data:)
    // as a non-null initializer even though the Objective-C side returns
    // nil for malformed payloads. The cheapest portable safety net is to
    // guard `pageCount` — a freshly-allocated empty document still
    // reports zero pages, so we exit early instead of risking a render.
    val document = PDFDocument(data = nsData)
    val pageCount = document.pageCount.toInt()
    if (pageCount == 0) return@withContext emptyList()
    val pages = ArrayList<ImageBitmap>(pageCount)

    for (i in 0 until pageCount) {
        val page = document.pageAtIndex(i.toULong()) ?: continue
        val (pointWidth, pointHeight) = page.boundsForBox(kPDFDisplayBoxMediaBox).useContents {
            size.width to size.height
        }
        val pixelSize = CGSizeMake(
            width = pointWidth * safeDensity,
            height = pointHeight * safeDensity,
        )
        val image = page.thumbnailOfSize(pixelSize, forBox = kPDFDisplayBoxMediaBox)
        val pngData = UIImagePNGRepresentation(image) ?: continue
        val pngBytes = pngData.toByteArray()
        pages += SkiaImage.makeFromEncoded(pngBytes).toComposeImageBitmap()
    }
    pages
}

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
