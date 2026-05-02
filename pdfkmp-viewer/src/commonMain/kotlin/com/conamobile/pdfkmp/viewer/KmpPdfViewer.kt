package com.conamobile.pdfkmp.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conamobile.pdfkmp.PdfDocument

/**
 * **Single entry point for `pdfkmp-viewer`.**
 *
 * `KmpPdfViewer.open(...)` drops a complete PDF screen into your
 * navigation graph: topbar (Minimal Mono on Android, Classic iOS
 * Native on iOS), inline search-bar morph, share & save actions,
 * hyperlink launcher, page indicator chip, gesture-driven zoom + pan.
 * Every piece of state — `searchOpen`, `searchQuery`,
 * `activeMatchIndex`, share / save bindings — is owned by the
 * library, not the host.
 *
 * The host configures *what* the viewer can do (via the visibility
 * toggles below) and *what happens on back* (via `onBack`). Nothing
 * else is required:
 *
 * ```kotlin
 * KmpPdfViewer.open(
 *     uri = "https://example.com/invoice.pdf",
 *     title = "Invoice",
 *     onBack = { navController.popBackStack() },
 * )
 * ```
 *
 * Four overloads cover every realistic input: a `PdfSource`, a
 * [PdfDocument] built through the PdfKmp DSL, a raw `ByteArray`, or a
 * URI string ([loadPdfBytesFromUri] resolves `content://`,
 * `file://`, `http(s)://`, asset / bundle paths, and bare filesystem
 * paths). The URI overload renders an inline progress indicator
 * while loading and an error message on failure — back navigation
 * still works in both states.
 *
 * Need finer control (custom topbar, custom share, FAB placement,
 * multi-action overlays)? Drop down to the lower-level public
 * composables: [PdfViewer], [PdfViewerTopBar], [PdfSearchBar],
 * [rememberPdfShareAction], [rememberPdfSaveAction],
 * [rememberPdfUrlLauncher], and [searchPdfText]. `KmpPdfViewer.open`
 * is the opinionated default; the building blocks remain available
 * for advanced layouts.
 */
public object KmpPdfViewer {

    /**
     * Opens a [PdfSource] in the all-in-one viewer screen.
     *
     * @param source PDF payload. `PdfSource.of(document)` keeps text
     *   selection + hyperlinks alive; `PdfSource.of(bytes)` is a
     *   plain bitmap experience.
     * @param modifier applied to the outer [Column].
     * @param title shown in the topbar's centered title (Classic iOS)
     *   / bold first line (Minimal Mono).
     * @param fileName user-visible filename surfaced to the share
     *   sheet and the "Saved to Downloads" entry. Must include `.pdf`.
     * @param onBack callback wired to the back chip / chevron. `null`
     *   removes the back affordance entirely.
     * @param backLabel iOS-only previous-screen label rendered next to
     *   the chevron (e.g. `"Files"`). Ignored on Android.
     * @param showBack hide / show the back affordance independently of
     *   [onBack]. Defaults to `true` when [onBack] is provided.
     * @param showSearch hide / show the search affordance. Auto-
     *   suppressed when the source carries no text runs.
     * @param showShare hide / show the share affordance.
     * @param showDownload hide / show the download affordance.
     * @param showPageIndicator hide / show the bottom-centre page chip.
     * @param zoomEnabled master switch for pinch + double-tap zoom.
     * @param doubleTapToZoom independent toggle for the double-tap
     *   shortcut.
     * @param textSelectable toggles the invisible selectable text
     *   overlay. Only effective on documents loaded from a
     *   [PdfDocument].
     * @param hyperlinksEnabled toggles the invisible clickable layer
     *   that opens hyperlink annotations in the system browser.
     *   Same `PdfDocument`-only caveat as [textSelectable].
     * @param backgroundColor colour painted behind the page bitmaps.
     *   Defaults to the active Material 3 surface tone.
     * @param pageBackgroundColor colour painted behind each individual
     *   page (visible while it's still rasterising).
     * @param contentPadding padding around the
     *   [androidx.compose.foundation.lazy.LazyColumn] content.
     *   Defaults to zero for a fully edge-to-edge layout.
     * @param pageSpacing vertical gap between page previews.
     * @param renderDensity baseline scaling factor applied during
     *   rasterisation.
     * @param maxZoom upper bound for the pinch gesture.
     */
    @Composable
    public fun open(
        source: PdfSource,
        modifier: Modifier = Modifier,
        title: String = "Document",
        fileName: String = "document.pdf",
        onBack: (() -> Unit)? = null,
        backLabel: String? = null,
        showBack: Boolean = onBack != null,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        pageBackgroundColor: Color = Color.White,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        pageSpacing: Dp = 4.dp,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
    ) {
        val bytes = remember(source) { source.bytes() }
        val textRuns = remember(source) { source.textRuns() }

        val shareAction = if (showShare) rememberPdfShareAction() else null
        val saveAction = if (showDownload) rememberPdfSaveAction() else null

        var searchOpen by remember(source) { mutableStateOf(false) }
        var searchQuery by remember(source) { mutableStateOf("") }
        var activeMatchIndex by remember(source) { mutableIntStateOf(0) }

        val highlights = remember(textRuns, searchQuery, searchOpen) {
            if (!searchOpen || searchQuery.isBlank()) emptyList()
            else searchPdfText(textRuns, searchQuery)
        }

        // Reset the active index whenever the result set changes so it
        // doesn't dangle past the new size.
        LaunchedEffect(highlights.size) {
            activeMatchIndex = if (highlights.isEmpty()) -1 else 0
        }

        val subtitle = remember(bytes.size) { "PDF · ${formatFileSize(bytes.size)}" }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor),
        ) {
            if (searchOpen) {
                PdfSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    matchCount = highlights.size,
                    activeIndex = activeMatchIndex,
                    onPrevious = {
                        if (highlights.isNotEmpty()) {
                            activeMatchIndex =
                                (activeMatchIndex - 1 + highlights.size) % highlights.size
                        }
                    },
                    onNext = {
                        if (highlights.isNotEmpty()) {
                            activeMatchIndex = (activeMatchIndex + 1) % highlights.size
                        }
                    },
                    onClose = {
                        searchOpen = false
                        searchQuery = ""
                        activeMatchIndex = -1
                    },
                )
            } else {
                PdfViewerTopBar(
                    title = title,
                    subtitle = subtitle,
                    backLabel = backLabel,
                    onBack = onBack ?: {},
                    onSearch = { searchOpen = true },
                    onShare = { shareAction?.invoke(bytes, fileName) },
                    onDownload = { saveAction?.invoke(bytes, fileName) },
                    showBack = showBack,
                    // Auto-suppress the search affordance when the
                    // source can't produce matches.
                    showSearch = showSearch && textRuns.isNotEmpty(),
                    showShare = showShare,
                    showDownload = showDownload,
                )
            }

            PdfViewer(
                source = source,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                showShareButton = false,
                backgroundColor = backgroundColor,
                pageBackgroundColor = pageBackgroundColor,
                contentPadding = contentPadding,
                pageSpacing = pageSpacing,
                renderDensity = renderDensity,
                maxZoom = maxZoom,
                zoomEnabled = zoomEnabled,
                doubleTapToZoom = doubleTapToZoom,
                textSelectable = textSelectable,
                hyperlinksEnabled = hyperlinksEnabled,
                showPageIndicator = showPageIndicator,
                searchHighlights = highlights,
                activeSearchHighlightIndex = activeMatchIndex,
            )
        }
    }

    /**
     * Opens a [PdfDocument] in the viewer. **Recommended entry point
     * when the PDF was authored through the PdfKmp DSL** — text
     * selection + hyperlinks light up automatically because the
     * document carries the necessary position metadata.
     */
    @Composable
    public fun open(
        document: PdfDocument,
        modifier: Modifier = Modifier,
        title: String = "Document",
        fileName: String = "document.pdf",
        onBack: (() -> Unit)? = null,
        backLabel: String? = null,
        showBack: Boolean = onBack != null,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        pageBackgroundColor: Color = Color.White,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        pageSpacing: Dp = 4.dp,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
    ) {
        open(
            source = remember(document) { PdfSource.of(document) },
            modifier = modifier,
            title = title,
            fileName = fileName,
            onBack = onBack,
            backLabel = backLabel,
            showBack = showBack,
            showSearch = showSearch,
            showShare = showShare,
            showDownload = showDownload,
            showPageIndicator = showPageIndicator,
            zoomEnabled = zoomEnabled,
            doubleTapToZoom = doubleTapToZoom,
            textSelectable = textSelectable,
            hyperlinksEnabled = hyperlinksEnabled,
            backgroundColor = backgroundColor,
            pageBackgroundColor = pageBackgroundColor,
            contentPadding = contentPadding,
            pageSpacing = pageSpacing,
            renderDensity = renderDensity,
            maxZoom = maxZoom,
        )
    }

    /**
     * Opens a raw `%PDF-…` payload — for bytes loaded from disk, the
     * network, an `ACTION_OPEN_DOCUMENT` picker, or any other source.
     * Text selection + hyperlink layers are inert because the bytes
     * carry no position metadata; everything else (zoom, share, save,
     * page indicator) works exactly the same.
     */
    @Composable
    public fun open(
        bytes: ByteArray,
        modifier: Modifier = Modifier,
        title: String = "Document",
        fileName: String = "document.pdf",
        onBack: (() -> Unit)? = null,
        backLabel: String? = null,
        showBack: Boolean = onBack != null,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        pageBackgroundColor: Color = Color.White,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        pageSpacing: Dp = 4.dp,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
    ) {
        open(
            source = remember(bytes) { PdfSource.Bytes(bytes) },
            modifier = modifier,
            title = title,
            fileName = fileName,
            onBack = onBack,
            backLabel = backLabel,
            showBack = showBack,
            showSearch = showSearch,
            showShare = showShare,
            showDownload = showDownload,
            showPageIndicator = showPageIndicator,
            zoomEnabled = zoomEnabled,
            doubleTapToZoom = doubleTapToZoom,
            textSelectable = textSelectable,
            hyperlinksEnabled = hyperlinksEnabled,
            backgroundColor = backgroundColor,
            pageBackgroundColor = pageBackgroundColor,
            contentPadding = contentPadding,
            pageSpacing = pageSpacing,
            renderDensity = renderDensity,
            maxZoom = maxZoom,
        )
    }

    /**
     * Opens the PDF behind [uri], loading the bytes asynchronously
     * via the platform's native resolution machinery (Android
     * `ContentResolver` for `content://`, `URL.openStream` for HTTPS,
     * etc. — see [loadPdfBytesFromUri] for the full list). While the
     * load is in flight the topbar renders with a
     * [CircularProgressIndicator] in place of the page area; on
     * failure the screen surfaces an inline error message so the host
     * can still navigate back via [onBack].
     */
    @Composable
    public fun open(
        uri: String,
        modifier: Modifier = Modifier,
        title: String = "Document",
        fileName: String = "document.pdf",
        onBack: (() -> Unit)? = null,
        backLabel: String? = null,
        showBack: Boolean = onBack != null,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        pageBackgroundColor: Color = Color.White,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        pageSpacing: Dp = 4.dp,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
    ) {
        var loaded by remember(uri) { mutableStateOf<ByteArray?>(null) }
        var error by remember(uri) { mutableStateOf<String?>(null) }

        LaunchedEffect(uri) {
            try {
                loaded = loadPdfBytesFromUri(uri)
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName ?: "Unknown error"
            }
        }

        val bytes = loaded
        when {
            bytes != null -> open(
                bytes = bytes,
                modifier = modifier,
                title = title,
                fileName = fileName,
                onBack = onBack,
                backLabel = backLabel,
                showBack = showBack,
                showSearch = showSearch,
                showShare = showShare,
                showDownload = showDownload,
                showPageIndicator = showPageIndicator,
                zoomEnabled = zoomEnabled,
                doubleTapToZoom = doubleTapToZoom,
                textSelectable = textSelectable,
                hyperlinksEnabled = hyperlinksEnabled,
                backgroundColor = backgroundColor,
                pageBackgroundColor = pageBackgroundColor,
                contentPadding = contentPadding,
                pageSpacing = pageSpacing,
                renderDensity = renderDensity,
                maxZoom = maxZoom,
            )

            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(backgroundColor),
            ) {
                // Show the topbar even while loading / on error so
                // the host can still navigate back.
                PdfViewerTopBar(
                    title = title,
                    backLabel = backLabel,
                    onBack = onBack ?: {},
                    showBack = showBack,
                    showSearch = false,
                    showShare = false,
                    showDownload = false,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val message = error
                    if (message != null) {
                        Text(
                            text = "Could not open PDF\n$message",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

/** Compact "PDF · 2.4 MB" string used as the Minimal Mono subtitle. */
private fun formatFileSize(bytes: Int): String = when {
    bytes >= 1_048_576 -> "${formatOneDecimal(bytes / 1_048_576f)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun formatOneDecimal(value: Float): String {
    val tenths = (value * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
