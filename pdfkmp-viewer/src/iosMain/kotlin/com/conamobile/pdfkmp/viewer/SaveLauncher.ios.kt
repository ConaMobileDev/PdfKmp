@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerViewController

/**
 * Returns a stateless [PdfSaveAction] that hands the PDF to the system
 * "Save to Files" picker ([UIDocumentPickerViewController] in export
 * mode), so the user chooses the destination — On My iPhone / iPad,
 * iCloud Drive, or any installed file provider.
 *
 * iOS has no shared Downloads folder, and a file written straight into
 * the app's `<NSDocumentDirectory>` only shows up in the Files app when
 * the host sets `UIFileSharingEnabled` and
 * `LSSupportsOpeningDocumentsInPlace` in its Info.plist — without them
 * the save is invisible. The picker needs no host configuration and
 * doubles as the confirmation the user expects from tapping "Save".
 *
 * The bytes are staged in [NSTemporaryDirectory] and exported as a copy.
 * Failures (disk full, no presenter) fall through silently — the
 * viewer's save action shouldn't be able to crash the host app.
 */
@Composable
public actual fun rememberPdfSaveAction(): PdfSaveAction =
    remember { IosSaveAction() }

private class IosSaveAction : PdfSaveAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val safeName = sanitizePdfFileName(fileName)
        val path = "${NSTemporaryDirectory().trimEnd('/')}/$safeName"
        val url = NSURL.fileURLWithPath(path)

        val data: NSData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        if (!data.writeToURL(url, atomically = true)) return

        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        val presenter = topMostViewController() ?: return
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}
