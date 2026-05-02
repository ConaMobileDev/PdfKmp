@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL

/**
 * Returns a stateless [PdfSaveAction] backed by the app's
 * `<NSDocumentDirectory>` — the closest iOS equivalent of an Android
 * Downloads folder. Files written here surface in the system Files app
 * under "On My iPhone / <AppName>" and remain available across app
 * launches.
 *
 * Existing files with the same name are overwritten via
 * `NSData.writeToURL(atomically: true)` so the user sees a fresh copy
 * each time. Failures (disk full, sandboxed write denied) fall through
 * silently — the viewer's save action shouldn't be able to crash the
 * host app, and iOS doesn't have a Toast equivalent the library can
 * reach without UIKit ceremony.
 */
@Composable
public actual fun rememberPdfSaveAction(): PdfSaveAction =
    remember { IosSaveAction() }

private class IosSaveAction : PdfSaveAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val safeName = fileName.takeIf { it.isNotBlank() } ?: "document.pdf"
        val baseDir = NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: return
        val path = "${baseDir.trimEnd('/')}/$safeName"

        // Overwrite any existing copy so re-saving the same document
        // produces a clean file rather than failing or appending.
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)

        val data: NSData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val url = NSURL.fileURLWithPath(path)
        data.writeToURL(url, atomically = true)
    }
}
