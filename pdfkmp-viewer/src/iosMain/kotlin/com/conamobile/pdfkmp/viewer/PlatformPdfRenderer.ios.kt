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
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.setValue
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImage
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
 *
 * NOTE: the password-unlock path (`isLocked` / `unlockWithPassword`) targets
 * the Apple toolchain and cannot be compiled on a non-macOS host — it needs
 * verification on macOS / a simulator.
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

    actual suspend fun renderPage(index: Int, density: Float, invert: Boolean): ImageBitmap? = withContext(Dispatchers.Default) {
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
        // Dark-mode: run the UIImage through Core Image's CIColorInvert
        // before the PNG round-trip. Falls back to the original image if
        // the filter can't be built so a render never silently fails.
        val finalImage = if (invert) invertImage(image) ?: image else image
        val pngData = UIImagePNGRepresentation(finalImage) ?: return@withContext null
        SkiaImage.makeFromEncoded(pngData.toByteArray()).toComposeImageBitmap()
    }

    actual fun close() {
        // PDFDocument is reference-counted by the Kotlin/Native ARC
        // bridge; dropping the handle releases the underlying memory.
    }

    internal companion object {
        suspend fun open(bytes: ByteArray, password: String?): PdfOpenResult =
            withContext(Dispatchers.Default) {
                if (bytes.isEmpty()) return@withContext PdfOpenResult.CannotOpen
                val nsData: NSData = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                // Kotlin/Native exposes `PDFDocument(data:)` as non-null even
                // though the Objective-C initializer is failable. Guarding on
                // pageCount catches a "successfully" returned but malformed
                // document — a freshly-allocated empty document reports 0.
                val document = PDFDocument(data = nsData)
                // An encrypted document opens "locked": pageCount is reported but
                // rendering fails until unlocked. unlockWithPassword(_:) returns
                // false for the wrong password.
                if (document.isLocked) {
                    val unlocked = password != null && document.unlockWithPassword(password)
                    if (!unlocked) return@withContext PdfOpenResult.PasswordRequired
                }
                if (document.pageCount.toInt() == 0) {
                    PdfOpenResult.CannotOpen
                } else {
                    PdfOpenResult.Success(PdfPageRenderer(document))
                }
            }
    }
}

internal actual suspend fun openPdfRenderer(bytes: ByteArray, password: String?): PdfOpenResult =
    PdfPageRenderer.open(bytes, password)

/**
 * Returns a colour-inverted copy of [image] for the viewer's dark-mode
 * surface, using Core Image's `CIColorInvert` filter. Returns `null` if
 * the image has no backing `CGImage`, the filter can't be created, or
 * the filtered output can't be rendered back to a `CGImage` — callers
 * fall back to the un-inverted image so a render never fails outright.
 *
 * `CIColorInvert` maps each RGB channel to `1 − channel` and leaves
 * alpha intact, matching the Android (`ColorMatrix`) and Desktop
 * (`RescaleOp`) backends.
 *
 * NOTE: targets the Apple toolchain — cannot be compiled on a non-macOS
 * host; needs verification on macOS / a simulator.
 */
private fun invertImage(image: UIImage): UIImage? {
    val cgImage = image.CGImage ?: return null
    val input = CIImage.imageWithCGImage(cgImage)
    val filter = CIFilter.filterWithName("CIColorInvert") ?: return null
    filter.setValue(input, forKey = "inputImage")
    val output = filter.outputImage ?: return null
    val context = CIContext.context()
    val outputCg = context.createCGImage(output, fromRect = output.extent()) ?: return null
    // Preserve the original scale/orientation so the inverted bitmap
    // stays the same pixel size as the source thumbnail.
    return UIImage.imageWithCGImage(outputCg, scale = image.scale, orientation = image.imageOrientation)
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
