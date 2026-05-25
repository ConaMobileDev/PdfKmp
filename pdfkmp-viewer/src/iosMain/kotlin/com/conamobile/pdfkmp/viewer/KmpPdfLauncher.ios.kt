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
 * by value (resolved to a [PdfSource.auto]), raw bytes wrap to
 * [PdfSource.Bytes], and `PdfDocument`s wrap to [PdfSource.of] so
 * captured text runs / hyperlinks survive the Compose-controller
 * boundary intact.
 */
public actual object KmpPdfLauncher {

    public actual fun open(
        uri: String,
        title: String,
        fileName: String,
        backLabel: String?,
        showTopBar: Boolean,
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
        cacheStrategy: PdfPageCacheStrategy,
    ) {
        val options = buildOptions(
            title, fileName, backLabel,
            showTopBar, showSearch, showShare, showDownload, showPageIndicator,
            zoomEnabled, doubleTapToZoom, textSelectable, hyperlinksEnabled,
            renderDensity, maxZoom, cacheStrategy,
        )
        val source = PdfSource.auto(uri)
        present { dismiss -> KmpPdfViewerWithOptions(source, options, dismiss) }
    }

    public actual fun open(
        bytes: ByteArray,
        title: String,
        fileName: String,
        backLabel: String?,
        showTopBar: Boolean,
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
        cacheStrategy: PdfPageCacheStrategy,
    ) {
        val options = buildOptions(
            title, fileName, backLabel,
            showTopBar, showSearch, showShare, showDownload, showPageIndicator,
            zoomEnabled, doubleTapToZoom, textSelectable, hyperlinksEnabled,
            renderDensity, maxZoom, cacheStrategy,
        )
        val source = PdfSource.Bytes(bytes)
        present { dismiss -> KmpPdfViewerWithOptions(source, options, dismiss) }
    }

    public actual fun open(
        document: PdfDocument,
        title: String,
        fileName: String,
        backLabel: String?,
        showTopBar: Boolean,
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
        cacheStrategy: PdfPageCacheStrategy,
    ) {
        val options = buildOptions(
            title, fileName, backLabel,
            showTopBar, showSearch, showShare, showDownload, showPageIndicator,
            zoomEnabled, doubleTapToZoom, textSelectable, hyperlinksEnabled,
            renderDensity, maxZoom, cacheStrategy,
        )
        // Snapshot the source on the calling thread so the
        // composition reads the same captured text runs / hyperlinks
        // even if the caller mutates the document afterwards.
        val source = PdfSource.of(document)
        present { dismiss -> KmpPdfViewerWithOptions(source, options, dismiss) }
    }

    private fun buildOptions(
        title: String,
        fileName: String,
        backLabel: String?,
        showTopBar: Boolean,
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
        cacheStrategy: PdfPageCacheStrategy,
    ): KmpPdfLaunchOptions = KmpPdfLaunchOptions(
        title = title,
        fileName = fileName,
        backLabel = backLabel,
        showTopBar = showTopBar,
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
        cacheStrategy = cacheStrategy,
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

@androidx.compose.runtime.Composable
private fun KmpPdfViewerWithOptions(
    source: PdfSource,
    options: KmpPdfLaunchOptions,
    dismiss: () -> Unit,
) {
    KmpPdfViewer(
        source = source,
        title = options.title,
        fileName = options.fileName,
        onBack = dismiss,
        backLabel = options.backLabel,
        showTopBar = options.showTopBar,
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
        cacheStrategy = options.cacheStrategy,
    )
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
