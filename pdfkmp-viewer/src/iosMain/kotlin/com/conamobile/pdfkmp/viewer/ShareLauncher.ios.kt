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
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * Captures the key window's root view controller once and returns a
 * [PdfShareAction] that writes the bytes into [NSTemporaryDirectory]
 * and presents a [UIActivityViewController] from the topmost presented
 * controller. The temp file is overwritten on every share so a
 * regenerated document with the same name correctly replaces the
 * previous payload.
 *
 * **iPad caveat** — UIKit insists on a `popoverPresentationController`
 * with a non-nil `sourceView` / `sourceRect` whenever a share sheet is
 * presented on iPad, otherwise it raises `NSGenericException`. Neither
 * `UIPopoverPresentationController` nor the KVC accessors needed to
 * reach it from outside the typed binding are exposed in Kotlin/Native's
 * UIKit klib at the moment, so this default action skips the
 * presentation on iPad. Apps targeting iPad should disable the built-in
 * button via `showShareButton = false` and present the share sheet from
 * Swift / Objective-C where the popover anchor can be set directly.
 */
@Composable
public actual fun rememberPdfShareAction(): PdfShareAction =
    remember { IosShareAction() }

private class IosShareAction : PdfShareAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            // Refuse to crash on iPad — see the class KDoc for the
            // recommended workaround.
            return
        }

        val safeName = fileName.takeIf { it.isNotBlank() } ?: "document.pdf"
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
        presenter.presentViewController(activity, animated = true, completion = null)
    }
}

/** Returns the deepest currently-presented view controller, or `null`. */
private fun topMostViewController(): UIViewController? {
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
