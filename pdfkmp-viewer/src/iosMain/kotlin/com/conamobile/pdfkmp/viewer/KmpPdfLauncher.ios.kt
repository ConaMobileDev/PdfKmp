@file:OptIn(ExperimentalForeignApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.material3.MaterialTheme
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

    public actual fun open(
        uri: String,
        title: String,
        fileName: String,
        backLabel: String?,
        showSearch: Boolean,
        showShare: Boolean,
        showDownload: Boolean,
        showPageIndicator: Boolean,
        zoomEnabled: Boolean,
        doubleTapToZoom: Boolean,
        textSelectable: Boolean,
        hyperlinksEnabled: Boolean,
        renderDensity: Float,
        maxZoom: Float,
    ) {
        val options = buildOptions(
            title, fileName, backLabel,
            showSearch, showShare, showDownload, showPageIndicator,
            zoomEnabled, doubleTapToZoom, textSelectable, hyperlinksEnabled,
            renderDensity, maxZoom,
        )
        present { dismiss ->
            KmpPdfViewer(
                uri = uri,
                title = options.title,
                fileName = options.fileName,
                onBack = dismiss,
                backLabel = options.backLabel,
                showSearch = options.showSearch,
                showShare = options.showShare,
                showDownload = options.showDownload,
                showPageIndicator = options.showPageIndicator,
                zoomEnabled = options.zoomEnabled,
                doubleTapToZoom = options.doubleTapToZoom,
                textSelectable = options.textSelectable,
                hyperlinksEnabled = options.hyperlinksEnabled,
                renderDensity = options.renderDensity,
                maxZoom = options.maxZoom,
            )
        }
    }

    public actual fun open(
        bytes: ByteArray,
        title: String,
        fileName: String,
        backLabel: String?,
        showSearch: Boolean,
        showShare: Boolean,
        showDownload: Boolean,
        showPageIndicator: Boolean,
        zoomEnabled: Boolean,
        doubleTapToZoom: Boolean,
        textSelectable: Boolean,
        hyperlinksEnabled: Boolean,
        renderDensity: Float,
        maxZoom: Float,
    ) {
        val options = buildOptions(
            title, fileName, backLabel,
            showSearch, showShare, showDownload, showPageIndicator,
            zoomEnabled, doubleTapToZoom, textSelectable, hyperlinksEnabled,
            renderDensity, maxZoom,
        )
        present { dismiss ->
            KmpPdfViewer(
                bytes = bytes,
                title = options.title,
                fileName = options.fileName,
                onBack = dismiss,
                backLabel = options.backLabel,
                showSearch = options.showSearch,
                showShare = options.showShare,
                showDownload = options.showDownload,
                showPageIndicator = options.showPageIndicator,
                zoomEnabled = options.zoomEnabled,
                doubleTapToZoom = options.doubleTapToZoom,
                textSelectable = options.textSelectable,
                hyperlinksEnabled = options.hyperlinksEnabled,
                renderDensity = options.renderDensity,
                maxZoom = options.maxZoom,
            )
        }
    }

    public actual fun open(
        document: PdfDocument,
        title: String,
        fileName: String,
        backLabel: String?,
        showSearch: Boolean,
        showShare: Boolean,
        showDownload: Boolean,
        showPageIndicator: Boolean,
        zoomEnabled: Boolean,
        doubleTapToZoom: Boolean,
        textSelectable: Boolean,
        hyperlinksEnabled: Boolean,
        renderDensity: Float,
        maxZoom: Float,
    ) {
        val options = buildOptions(
            title, fileName, backLabel,
            showSearch, showShare, showDownload, showPageIndicator,
            zoomEnabled, doubleTapToZoom, textSelectable, hyperlinksEnabled,
            renderDensity, maxZoom,
        )
        // Snapshot the source on the calling thread so the
        // composition reads the same captured text runs / hyperlinks
        // even if the caller mutates the document afterwards.
        val source = PdfSource.of(document)
        present { dismiss ->
            KmpPdfViewer(
                source = source,
                title = options.title,
                fileName = options.fileName,
                onBack = dismiss,
                backLabel = options.backLabel,
                showSearch = options.showSearch,
                showShare = options.showShare,
                showDownload = options.showDownload,
                showPageIndicator = options.showPageIndicator,
                zoomEnabled = options.zoomEnabled,
                doubleTapToZoom = options.doubleTapToZoom,
                textSelectable = options.textSelectable,
                hyperlinksEnabled = options.hyperlinksEnabled,
                renderDensity = options.renderDensity,
                maxZoom = options.maxZoom,
            )
        }
    }

    private fun buildOptions(
        title: String,
        fileName: String,
        backLabel: String?,
        showSearch: Boolean,
        showShare: Boolean,
        showDownload: Boolean,
        showPageIndicator: Boolean,
        zoomEnabled: Boolean,
        doubleTapToZoom: Boolean,
        textSelectable: Boolean,
        hyperlinksEnabled: Boolean,
        renderDensity: Float,
        maxZoom: Float,
    ): KmpPdfLaunchOptions = KmpPdfLaunchOptions(
        title = title,
        fileName = fileName,
        backLabel = backLabel,
        showSearch = showSearch,
        showShare = showShare,
        showDownload = showDownload,
        showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled,
        doubleTapToZoom = doubleTapToZoom,
        textSelectable = textSelectable,
        hyperlinksEnabled = hyperlinksEnabled,
        renderDensity = renderDensity,
        maxZoom = maxZoom,
    )

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
