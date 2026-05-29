package com.conamobile.pdfkmp.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.conamobile.pdfkmp.PdfDocument
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * JVM / Desktop implementation of the imperative launcher.
 *
 * Where Android opens an `Activity` and iOS presents a `UIViewController`,
 * Desktop opens a Swing [JFrame] hosting a Compose [ComposePanel]. A panel
 * (rather than `application { Window { … } }`) is used deliberately: it can
 * be shown imperatively from any call site without owning the process event
 * loop, so a single click handler can open the viewer the same way it does
 * on mobile. The window disposes itself when the user taps back or closes it.
 *
 * Payload routing mirrors the other platforms: URI strings resolve through
 * [PdfSource.auto], raw bytes wrap to [PdfSource.Bytes], and `PdfDocument`s
 * wrap to [PdfSource.of] so captured text runs and hyperlinks survive.
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
    ): Unit = present(
        source = PdfSource.auto(uri),
        title = title, fileName = fileName, backLabel = backLabel,
        showTopBar = showTopBar, showSearch = showSearch, showShare = showShare,
        showDownload = showDownload, showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled, doubleTapToZoom = doubleTapToZoom,
        textSelectable = textSelectable, hyperlinksEnabled = hyperlinksEnabled,
        renderDensity = renderDensity, maxZoom = maxZoom, cacheStrategy = cacheStrategy,
    )

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
    ): Unit = present(
        source = PdfSource.Bytes(bytes),
        title = title, fileName = fileName, backLabel = backLabel,
        showTopBar = showTopBar, showSearch = showSearch, showShare = showShare,
        showDownload = showDownload, showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled, doubleTapToZoom = doubleTapToZoom,
        textSelectable = textSelectable, hyperlinksEnabled = hyperlinksEnabled,
        renderDensity = renderDensity, maxZoom = maxZoom, cacheStrategy = cacheStrategy,
    )

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
    ): Unit = present(
        // Snapshot on the calling thread so the composition reads the same
        // captured text runs / hyperlinks even if the caller mutates later.
        source = PdfSource.of(document),
        title = title, fileName = fileName, backLabel = backLabel,
        showTopBar = showTopBar, showSearch = showSearch, showShare = showShare,
        showDownload = showDownload, showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled, doubleTapToZoom = doubleTapToZoom,
        textSelectable = textSelectable, hyperlinksEnabled = hyperlinksEnabled,
        renderDensity = renderDensity, maxZoom = maxZoom, cacheStrategy = cacheStrategy,
    )

    private fun present(
        source: PdfSource,
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
        SwingUtilities.invokeLater {
            val frame = JFrame(title)
            frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            frame.size = Dimension(900, 1100)
            frame.setLocationRelativeTo(null)

            val dismiss: () -> Unit = { frame.dispose() }
            val panel = ComposePanel()
            panel.setContent {
                HostedViewer(
                    source = source,
                    title = title, fileName = fileName, onBack = dismiss, backLabel = backLabel,
                    showTopBar = showTopBar, showSearch = showSearch, showShare = showShare,
                    showDownload = showDownload, showPageIndicator = showPageIndicator,
                    zoomEnabled = zoomEnabled, doubleTapToZoom = doubleTapToZoom,
                    textSelectable = textSelectable, hyperlinksEnabled = hyperlinksEnabled,
                    renderDensity = renderDensity, maxZoom = maxZoom, cacheStrategy = cacheStrategy,
                )
            }
            frame.contentPane.add(panel)
            frame.isVisible = true
        }
    }
}

@Composable
private fun HostedViewer(
    source: PdfSource,
    title: String,
    fileName: String,
    onBack: () -> Unit,
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
    MaterialTheme {
        KmpPdfViewer(
            source = source,
            title = title,
            fileName = fileName,
            onBack = onBack,
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
    }
}
