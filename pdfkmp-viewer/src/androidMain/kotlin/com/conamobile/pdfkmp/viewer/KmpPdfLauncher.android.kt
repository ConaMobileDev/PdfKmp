package com.conamobile.pdfkmp.viewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder

/**
 * Android implementation of the imperative launcher. Each `open(...)`
 * builds an [Intent] targeting [KmpPdfViewerHostActivity], stuffs the
 * payload into either a primitive extra (URI / small byte arrays) or
 * a [KmpPdfLauncherRegistry] token, serialises the [KmpPdfLaunchOptions]
 * bundle into Intent extras alongside it, and starts the activity using
 * the application context the library captured at startup via
 * `ViewerContextInitializer`.
 *
 * `FLAG_ACTIVITY_NEW_TASK` is set unconditionally — the captured
 * context is the application context, not an activity, so a fresh
 * task is the only legal launch mode. Apps that want the viewer to
 * sit in their existing task should use the [KmpPdfViewer]
 * composable inside their own navigation graph instead.
 */
public actual object KmpPdfLauncher {

    /** Intent payloads kept under one extras key so we can switch between them. */
    internal const val EXTRA_URI: String = "kmp.pdf.uri"
    internal const val EXTRA_BYTES: String = "kmp.pdf.bytes"
    internal const val EXTRA_TOKEN: String = "kmp.pdf.token"

    /** Configuration flags forwarded to [KmpPdfViewer]. */
    internal const val EXTRA_TITLE: String = "kmp.pdf.title"
    internal const val EXTRA_FILE_NAME: String = "kmp.pdf.fileName"
    internal const val EXTRA_BACK_LABEL: String = "kmp.pdf.backLabel"
    internal const val EXTRA_SHOW_SEARCH: String = "kmp.pdf.showSearch"
    internal const val EXTRA_SHOW_SHARE: String = "kmp.pdf.showShare"
    internal const val EXTRA_SHOW_DOWNLOAD: String = "kmp.pdf.showDownload"
    internal const val EXTRA_SHOW_PAGE_INDICATOR: String = "kmp.pdf.showPageIndicator"
    internal const val EXTRA_ZOOM_ENABLED: String = "kmp.pdf.zoomEnabled"
    internal const val EXTRA_DOUBLE_TAP_TO_ZOOM: String = "kmp.pdf.doubleTapToZoom"
    internal const val EXTRA_TEXT_SELECTABLE: String = "kmp.pdf.textSelectable"
    internal const val EXTRA_HYPERLINKS_ENABLED: String = "kmp.pdf.hyperlinksEnabled"
    internal const val EXTRA_RENDER_DENSITY: String = "kmp.pdf.renderDensity"
    internal const val EXTRA_MAX_ZOOM: String = "kmp.pdf.maxZoom"

    /** Cap on inline byte payloads — Bundle parcelling fails near the 1 MiB mark. */
    private const val INLINE_BYTES_LIMIT: Int = 512 * 1024

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
        launch { putExtra(EXTRA_URI, uri).putOptions(options) }
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
        if (bytes.size < INLINE_BYTES_LIMIT) {
            launch { putExtra(EXTRA_BYTES, bytes).putOptions(options) }
        } else {
            val token = KmpPdfLauncherRegistry.put(PdfSource.Bytes(bytes))
            launch { putExtra(EXTRA_TOKEN, token).putOptions(options) }
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
        // Always go through the registry so we can carry the
        // captured text runs + hyperlinks — primitives can't hold
        // that metadata across the IPC boundary.
        val token = KmpPdfLauncherRegistry.put(PdfSource.of(document))
        launch { putExtra(EXTRA_TOKEN, token).putOptions(options) }
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

    private inline fun launch(crossinline configure: Intent.() -> Intent) {
        val context = ViewerContextHolder.get()
        val intent = Intent(context, KmpPdfViewerHostActivity::class.java)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .configure()
        context.startActivity(intent)
    }
}

/**
 * Writes every field of [options] into the receiving [Intent] as a
 * primitive extra so [KmpPdfViewerHostActivity] can rebuild the
 * configuration after the launch hop. Kept beside the launcher
 * because the extras keys live there too.
 */
private fun Intent.putOptions(options: KmpPdfLaunchOptions): Intent {
    putExtra(KmpPdfLauncher.EXTRA_TITLE, options.title)
    putExtra(KmpPdfLauncher.EXTRA_FILE_NAME, options.fileName)
    options.backLabel?.let { putExtra(KmpPdfLauncher.EXTRA_BACK_LABEL, it) }
    putExtra(KmpPdfLauncher.EXTRA_SHOW_SEARCH, options.showSearch)
    putExtra(KmpPdfLauncher.EXTRA_SHOW_SHARE, options.showShare)
    putExtra(KmpPdfLauncher.EXTRA_SHOW_DOWNLOAD, options.showDownload)
    putExtra(KmpPdfLauncher.EXTRA_SHOW_PAGE_INDICATOR, options.showPageIndicator)
    putExtra(KmpPdfLauncher.EXTRA_ZOOM_ENABLED, options.zoomEnabled)
    putExtra(KmpPdfLauncher.EXTRA_DOUBLE_TAP_TO_ZOOM, options.doubleTapToZoom)
    putExtra(KmpPdfLauncher.EXTRA_TEXT_SELECTABLE, options.textSelectable)
    putExtra(KmpPdfLauncher.EXTRA_HYPERLINKS_ENABLED, options.hyperlinksEnabled)
    putExtra(KmpPdfLauncher.EXTRA_RENDER_DENSITY, options.renderDensity)
    putExtra(KmpPdfLauncher.EXTRA_MAX_ZOOM, options.maxZoom)
    return this
}

/** Reverse of [putOptions]; defaults to [KmpPdfLaunchOptions]'s own defaults. */
private fun Intent.readOptions(): KmpPdfLaunchOptions {
    val defaults = KmpPdfLaunchOptions()
    return KmpPdfLaunchOptions(
        title = getStringExtra(KmpPdfLauncher.EXTRA_TITLE) ?: defaults.title,
        fileName = getStringExtra(KmpPdfLauncher.EXTRA_FILE_NAME) ?: defaults.fileName,
        backLabel = getStringExtra(KmpPdfLauncher.EXTRA_BACK_LABEL),
        showSearch = getBooleanExtra(KmpPdfLauncher.EXTRA_SHOW_SEARCH, defaults.showSearch),
        showShare = getBooleanExtra(KmpPdfLauncher.EXTRA_SHOW_SHARE, defaults.showShare),
        showDownload = getBooleanExtra(KmpPdfLauncher.EXTRA_SHOW_DOWNLOAD, defaults.showDownload),
        showPageIndicator = getBooleanExtra(KmpPdfLauncher.EXTRA_SHOW_PAGE_INDICATOR, defaults.showPageIndicator),
        zoomEnabled = getBooleanExtra(KmpPdfLauncher.EXTRA_ZOOM_ENABLED, defaults.zoomEnabled),
        doubleTapToZoom = getBooleanExtra(KmpPdfLauncher.EXTRA_DOUBLE_TAP_TO_ZOOM, defaults.doubleTapToZoom),
        textSelectable = getBooleanExtra(KmpPdfLauncher.EXTRA_TEXT_SELECTABLE, defaults.textSelectable),
        hyperlinksEnabled = getBooleanExtra(KmpPdfLauncher.EXTRA_HYPERLINKS_ENABLED, defaults.hyperlinksEnabled),
        renderDensity = getFloatExtra(KmpPdfLauncher.EXTRA_RENDER_DENSITY, defaults.renderDensity),
        maxZoom = getFloatExtra(KmpPdfLauncher.EXTRA_MAX_ZOOM, defaults.maxZoom),
    )
}

/**
 * Hosted activity that mounts [KmpPdfViewer] full-screen. Reads the
 * payload + options from the launching Intent, forwards every back tap
 * (chip, gesture, system) to [finish].
 *
 * Declared in `:pdfkmp-viewer`'s `AndroidManifest.xml` so consumers
 * don't have to register anything by hand.
 */
internal class KmpPdfViewerHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val options = intent.readOptions()
        val uri = intent.getStringExtra(KmpPdfLauncher.EXTRA_URI)
        val bytes = intent.getByteArrayExtra(KmpPdfLauncher.EXTRA_BYTES)
        val token = intent.getStringExtra(KmpPdfLauncher.EXTRA_TOKEN)

        setContent {
            MaterialTheme {
                when {
                    uri != null -> KmpPdfViewer(
                        uri = uri,
                        title = options.title,
                        fileName = options.fileName,
                        onBack = { finish() },
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

                    bytes != null -> KmpPdfViewer(
                        bytes = bytes,
                        title = options.title,
                        fileName = options.fileName,
                        onBack = { finish() },
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

                    token != null -> {
                        val source = remember(token) { KmpPdfLauncherRegistry.take(token) }
                        if (source != null) {
                            KmpPdfViewer(
                                source = source,
                                title = options.title,
                                fileName = options.fileName,
                                onBack = { finish() },
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
                        } else {
                            // Stale token (process restart, double launch).
                            // Dismiss immediately so the user isn't
                            // staring at a blank screen.
                            LaunchedEffect(Unit) { finish() }
                        }
                    }

                    else -> LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }
}
