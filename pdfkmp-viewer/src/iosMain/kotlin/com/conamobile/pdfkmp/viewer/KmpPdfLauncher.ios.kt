@file:OptIn(ExperimentalForeignApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.conamobile.pdfkmp.PdfDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * iOS implementation of the imperative launcher.
 *
 * Each `open(...)` builds a `ComposeUIViewController` around
 * [KmpPdfViewer], walks the topmost presented view-controller from
 * the current key window, and fires `presentViewController(animated:
 * true)` against it. The hosted view-controller dismisses itself when
 * the user taps back (`onBack` calls `dismissViewControllerAnimated:`).
 *
 * Payload routing mirrors the Android launcher: URI strings travel
 * by value, raw bytes / `PdfDocument`s bigger than a primitive go
 * through [KmpPdfLauncherRegistry] so the content survives the
 * Compose-controller boundary intact.
 */
public actual object KmpPdfLauncher {

    public actual fun open(uri: String, title: String, fileName: String) {
        present { dismiss ->
            KmpPdfViewer(
                uri = uri,
                title = title,
                fileName = fileName,
                onBack = dismiss,
            )
        }
    }

    public actual fun open(bytes: ByteArray, title: String, fileName: String) {
        present { dismiss ->
            KmpPdfViewer(
                bytes = bytes,
                title = title,
                fileName = fileName,
                onBack = dismiss,
            )
        }
    }

    public actual fun open(document: PdfDocument, title: String, fileName: String) {
        // Snapshot the source on the calling thread so the
        // composition reads the same captured text runs / hyperlinks
        // even if the caller mutates the document afterwards.
        val source = PdfSource.of(document)
        present { dismiss ->
            KmpPdfViewer(
                source = source,
                title = title,
                fileName = fileName,
                onBack = dismiss,
            )
        }
    }

    private inline fun present(
        crossinline content: @androidx.compose.runtime.Composable (dismiss: () -> Unit) -> Unit,
    ) {
        var hosted: UIViewController? = null
        val dismiss: () -> Unit = {
            // Capture-by-reference so the closure dismisses *this*
            // launch's view-controller, even if multiple launches
            // are in flight (registry guarantees uniqueness).
            hosted?.dismissViewControllerAnimated(true, null)
            Unit
        }
        val viewController = ComposeUIViewController {
            MaterialTheme {
                content(dismiss)
            }
        }
        hosted = viewController
        viewController.modalPresentationStyle = UIModalPresentationFullScreen

        val presenter = topMostViewController() ?: return
        presenter.presentViewController(viewController, animated = true, completion = null)
    }
}

/** Walks from the key window's root through any presented view-controllers. */
private fun topMostViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.keyWindow
        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
    var top = window?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}
