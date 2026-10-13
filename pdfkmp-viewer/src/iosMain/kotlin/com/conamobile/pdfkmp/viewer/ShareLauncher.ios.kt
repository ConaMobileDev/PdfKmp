@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController

/**
 * Captures the key window's root view controller once and returns a
 * [PdfShareAction] that writes the bytes into [NSTemporaryDirectory]
 * and presents a [UIActivityViewController] from the topmost presented
 * controller. The temp file is overwritten on every share so a
 * regenerated document with the same name correctly replaces the
 * previous payload.
 *
 * **iPad** — UIKit presents the share sheet as a popover there and
 * raises `NSGenericException` when its `popoverPresentationController`
 * has no anchor. The sheet is anchored to the centre of the presenting
 * view with no arrow, since the action has no handle on whichever
 * button triggered it.
 */
@Composable
public actual fun rememberPdfShareAction(): PdfShareAction =
    remember { IosShareAction() }

private class IosShareAction : PdfShareAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val safeName = sanitizePdfFileName(fileName)
        val path = joinPath(NSTemporaryDirectory(), safeName)
        val url = NSURL.fileURLWithPath(path)

        val data: NSData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        data.writeToURL(url, atomically = true)

        val activity = UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null,
        )

        val presenter = topMostViewController() ?: return
        // Non-null only where the sheet is shown as a popover (iPad).
        activity.popoverPresentationController?.let { popover ->
            val anchor = presenter.view
            popover.sourceView = anchor
            popover.sourceRect = anchor.bounds.useContents {
                CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 0.0, 0.0)
            }
            popover.permittedArrowDirections = 0u
        }
        presenter.presentViewController(activity, animated = true, completion = null)
    }
}

/** Returns the deepest currently-presented view controller, or `null`. */
internal fun topMostViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.keyWindow
        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
    var top = window?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}

/** Joins [base] and [name] with a single `/`, regardless of trailing slash. */
private fun joinPath(base: String, name: String): String {
    val sanitized = base.trimEnd('/')
    return "$sanitized/$name"
}
