@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController

/**
 * Returns a stateless [PdfPrintAction] that presents iOS's shared
 * [UIPrintInteractionController].
 *
 * The PDF bytes are wrapped in an [NSData] and handed to the controller
 * as its `printingItem`; `UIPrintInteractionController` knows how to
 * paginate a PDF payload natively, so no per-page rendering is needed.
 * A [UIPrintInfo] of [UIPrintInfoOutputType.UIPrintInfoOutputGeneral]
 * configures it for an ordinary document print.
 *
 * The print sheet is presented animated, mirroring how the share sheet
 * is surfaced ([rememberPdfShareAction]). On iPad UIKit presents the
 * print panel as a popover anchored to the screen automatically when
 * presented animated, so — unlike the share sheet — no explicit
 * `sourceView` anchoring is required here.
 *
 * NOTE: this file targets the Apple toolchain and cannot be compiled on
 * a non-macOS host; it needs verification on macOS / a simulator.
 */
@Composable
public actual fun rememberPdfPrintAction(): PdfPrintAction =
    remember { IosPrintAction() }

private class IosPrintAction : PdfPrintAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val controller = UIPrintInteractionController.sharedPrintController
        val safeName = fileName.takeIf { it.isNotBlank() } ?: "document.pdf"

        val info = UIPrintInfo.printInfo()
        info.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
        info.jobName = safeName
        controller.printInfo = info

        val data: NSData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        controller.printingItem = data

        // animated = true; no completion handler needed — the controller
        // owns its own presentation/dismissal lifecycle.
        controller.presentAnimated(true, completionHandler = null)
    }
}
