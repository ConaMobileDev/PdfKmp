package com.conamobile.pdfkmp

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Exposes the rendered PDF bytes as an [NSData] for friction-free Swift
 * interop.
 *
 * Swift sees this as `pdfDocument.toNSData()` and can write the result
 * directly to disk via `Data.write(to:)` or feed it to PDFKit / share sheets
 * without touching `KotlinByteArray`.
 *
 * The returned [NSData] owns a fresh copy of the underlying bytes; mutating
 * the original document (which is impossible — [PdfDocument] is immutable)
 * would not affect previously returned data instances.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public fun PdfDocument.toNSData(): NSData {
    val bytes = toByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}
