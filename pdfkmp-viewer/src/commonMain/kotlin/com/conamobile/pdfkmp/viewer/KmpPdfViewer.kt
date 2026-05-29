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
 * **All-in-one PDF viewer screen — recommended composable entry.**
 *
 * `KmpPdfViewer(...)` drops a complete PDF screen into your
 * Compose hierarchy: topbar (Minimal Mono on Android, Classic iOS
 * Native on iOS), inline search-bar morph, share & save actions,
 * hyperlink launcher, page indicator chip, gesture-driven zoom + pan.
 * Every piece of state — `searchOpen`, `searchQuery`,
 * `activeMatchIndex`, share / save bindings — is owned by the
 * library, not the host.
 *
 * The host configures *what* the viewer can do (via the visibility
 * toggles below) and *what happens on back* (via [onBack]). Nothing
 * else is required:
 *
 * ```kotlin
 * KmpPdfViewer(
 *     source = PdfSource.Remote("https://example.com/invoice.pdf"),
 *     title = "Invoice",
 *     onBack = { navController.popBackStack() },
 * )
 * ```
 *
 * Three overloads cover every realistic input: a [PdfSource]
 * (recommended — covers remote URLs, local files, content URIs,
 * bundled assets, raw bytes and PdfKmp documents through one sealed
 * type), a [PdfDocument] built through the PdfKmp DSL, or a raw
 * `ByteArray`. Async sources render an inline progress indicator
 * while loading and an error message on failure — back navigation
 * still works in both states.
 *
 * **Need to launch a viewer from outside a `@Composable` scope** —
 * say from a click handler or `LaunchedEffect`? Use the imperative
 * counterpart [KmpPdfLauncher.open] instead. It hosts this same
 * composable inside a platform-native shell (Activity on Android,
 * `UIViewController` on iOS).
 *
 * **Need finer control** (custom topbar, custom share, FAB
 * placement, multi-action overlays)? Drop down to the lower-level
 * public composables: [PdfViewer], [PdfViewerTopBar], [PdfSearchBar],
 * [rememberPdfShareAction], [rememberPdfSaveAction],
 * [rememberPdfUrlLauncher], and [searchPdfText]. `KmpPdfViewer` is
 * the opinionated default; the building blocks remain available for
 * advanced layouts.
 *
 * @param source PDF payload. `PdfSource.of(document)` keeps text
 *   selection + hyperlinks alive; async variants stream bytes from
 *   the platform's native loader.
 * @param modifier applied to the outer [Column].
 * @param title shown in the topbar's centered title (Classic iOS)
 *   / bold first line (Minimal Mono).
 * @param fileName user-visible filename surfaced to the share
 *   sheet and the "Saved to Downloads" entry. Must include `.pdf`.
 * @param onBack callback wired to the back chip / chevron. `null`
 *   removes the back affordance entirely.
 * @param backLabel iOS-only previous-screen label rendered next to
 *   the chevron (e.g. `"Files"`). Ignored on Android.
 * @param showTopBar master switch for the entire chrome (topbar +
 *   search bar). `false` hides both unconditionally — yields a "poor
 *   viewer" surface that is just pages, indicator, and gestures.
 *   When `false`, [showBack], [showSearch], [showShare],
 *   [showDownload] all become no-ops because there is no bar to
 *   surface them on. Host apps that hide the topbar typically wire
 *   their own back navigation and share / download affordances via
 *   the platform's system UI.
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
 * @param cacheStrategy controls how far ahead and behind the
 *   visible page the renderer keeps rasterised bitmaps. See
 *   [PdfPageCacheStrategy] for the trade-offs; defaults to
 *   [PdfPageCacheStrategy.Auto] which picks a window based on
 *   available RAM and never crashes.
 */
@Composable
public fun KmpPdfViewer(
    source: PdfSource,
    modifier: Modifier = Modifier,
    title: String = "Document",
    fileName: String = "document.pdf",
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    showTopBar: Boolean = true,
    showBack: Boolean = onBack != null,
    showSearch: Boolean = true,
    showShare: Boolean = true,
    showDownload: Boolean = true,
    showPageIndicator: Boolean = true,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    textSelectable: Boolean = true,
    hyperlinksEnabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = 5f,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    // Resolve async sources here once so the topbar's share + save
    // bindings (which need the bytes in hand) and the embedded
    // PdfViewer don't both kick off duplicate platform loads.
    val initialBytes = remember(source) { source.inMemoryBytesOrNull() }
    var bytes by remember(source) { mutableStateOf(initialBytes) }
    var loadError by remember(source) { mutableStateOf<String?>(null) }

    LaunchedEffect(source) {
        if (bytes != null || loadError != null) return@LaunchedEffect
        try {
            bytes = source.loadBytes()
        } catch (t: Throwable) {
            loadError = t.message ?: t::class.simpleName ?: "Unknown error"
        }
    }

    val resolvedBytes = bytes
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

    val subtitle = remember(resolvedBytes?.size) {
        resolvedBytes?.let { "PDF · ${formatFileSize(it.size)}" } ?: "PDF · loading"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (showTopBar) {
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
                    onShare = {
                        val ready = resolvedBytes
                        if (ready != null) shareAction?.invoke(ready, fileName)
                    },
                    onDownload = {
                        val ready = resolvedBytes
                        if (ready != null) saveAction?.invoke(ready, fileName)
                    },
                    showBack = showBack,
                    // Auto-suppress the search affordance when the
                    // source can't produce matches.
                    showSearch = showSearch && textRuns.isNotEmpty(),
                    showShare = showShare,
                    showDownload = showDownload,
                )
            }
        }

        when {
            loadError != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Could not open PDF\n$loadError",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            resolvedBytes == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> {
                // Hand a Bytes-wrapped source down so PdfViewer's
                // internal LaunchedEffect short-circuits the (already
                // completed) async load. For Document sources we
                // pass the original through to preserve the text
                // runs / hyperlinks the document captured.
                val downstream = when (source) {
                    is PdfSource.Document -> source
                    else -> PdfSource.Bytes(resolvedBytes)
                }
                PdfViewer(
                    source = downstream,
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
                    showZoomControls = showZoomControls,
                    textSelectable = textSelectable,
                    hyperlinksEnabled = hyperlinksEnabled,
                    showPageIndicator = showPageIndicator,
                    cacheStrategy = cacheStrategy,
                    searchHighlights = highlights,
                    activeSearchHighlightIndex = activeMatchIndex,
                )
            }
        }
    }
}

/**
 * [PdfDocument]-flavoured overload — wraps the document in a
 * `PdfSource.Document` and forwards. Recommended entry point when
 * the PDF was authored through the PdfKmp DSL because text selection
 * and hyperlinks light up automatically.
 */
@Composable
public fun KmpPdfViewer(
    document: PdfDocument,
    modifier: Modifier = Modifier,
    title: String = "Document",
    fileName: String = "document.pdf",
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    showTopBar: Boolean = true,
    showBack: Boolean = onBack != null,
    showSearch: Boolean = true,
    showShare: Boolean = true,
    showDownload: Boolean = true,
    showPageIndicator: Boolean = true,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    textSelectable: Boolean = true,
    hyperlinksEnabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = 5f,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    KmpPdfViewer(
        source = remember(document) { PdfSource.of(document) },
        modifier = modifier,
        title = title,
        fileName = fileName,
        onBack = onBack,
        backLabel = backLabel,
        showTopBar = showTopBar,
        showBack = showBack,
        showSearch = showSearch,
        showShare = showShare,
        showDownload = showDownload,
        showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled,
        doubleTapToZoom = doubleTapToZoom,
        showZoomControls = showZoomControls,
        textSelectable = textSelectable,
        hyperlinksEnabled = hyperlinksEnabled,
        backgroundColor = backgroundColor,
        pageBackgroundColor = pageBackgroundColor,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        renderDensity = renderDensity,
        maxZoom = maxZoom,
        cacheStrategy = cacheStrategy,
    )
}

/**
 * Raw-bytes overload — for payloads that came from disk, the
 * network, an `ACTION_OPEN_DOCUMENT` picker, or any other source.
 * Text selection + hyperlink layers are inert because the bytes
 * carry no position metadata; everything else (zoom, share, save,
 * page indicator) works exactly the same.
 */
@Composable
public fun KmpPdfViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    title: String = "Document",
    fileName: String = "document.pdf",
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    showTopBar: Boolean = true,
    showBack: Boolean = onBack != null,
    showSearch: Boolean = true,
    showShare: Boolean = true,
    showDownload: Boolean = true,
    showPageIndicator: Boolean = true,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    textSelectable: Boolean = true,
    hyperlinksEnabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = 5f,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    KmpPdfViewer(
        source = remember(bytes) { PdfSource.Bytes(bytes) },
        modifier = modifier,
        title = title,
        fileName = fileName,
        onBack = onBack,
        backLabel = backLabel,
        showTopBar = showTopBar,
        showBack = showBack,
        showSearch = showSearch,
        showShare = showShare,
        showDownload = showDownload,
        showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled,
        doubleTapToZoom = doubleTapToZoom,
        showZoomControls = showZoomControls,
        textSelectable = textSelectable,
        hyperlinksEnabled = hyperlinksEnabled,
        backgroundColor = backgroundColor,
        pageBackgroundColor = pageBackgroundColor,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        renderDensity = renderDensity,
        maxZoom = maxZoom,
        cacheStrategy = cacheStrategy,
    )
}

/**
 * String-URI overload, **deprecated in favour of [PdfSource.auto]**.
 *
 * The string form hid which transport was being used and offered no
 * place to attach per-shape configuration (HTTP headers, timeouts,
 * etc.). Migrate to an explicit [PdfSource] variant — call
 * [PdfSource.auto] if you genuinely don't know the scheme at call
 * site, or pick a constructor directly when you do.
 */
@Deprecated(
    message = "Use KmpPdfViewer(source = PdfSource.auto(uri), …) — strings hide " +
        "what transport is being used and can't carry headers / timeouts.",
    replaceWith = ReplaceWith(
        "KmpPdfViewer(source = PdfSource.auto(uri), modifier = modifier, " +
            "title = title, fileName = fileName, onBack = onBack, backLabel = backLabel, " +
            "showTopBar = showTopBar, showBack = showBack, showSearch = showSearch, " +
            "showShare = showShare, showDownload = showDownload, " +
            "showPageIndicator = showPageIndicator, zoomEnabled = zoomEnabled, " +
            "doubleTapToZoom = doubleTapToZoom, textSelectable = textSelectable, " +
            "hyperlinksEnabled = hyperlinksEnabled, backgroundColor = backgroundColor, " +
            "pageBackgroundColor = pageBackgroundColor, contentPadding = contentPadding, " +
            "pageSpacing = pageSpacing, renderDensity = renderDensity, maxZoom = maxZoom, " +
            "cacheStrategy = cacheStrategy)",
        "com.conamobile.pdfkmp.viewer.PdfSource",
    ),
)
@Composable
public fun KmpPdfViewer(
    uri: String,
    modifier: Modifier = Modifier,
    title: String = "Document",
    fileName: String = "document.pdf",
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    showTopBar: Boolean = true,
    showBack: Boolean = onBack != null,
    showSearch: Boolean = true,
    showShare: Boolean = true,
    showDownload: Boolean = true,
    showPageIndicator: Boolean = true,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    textSelectable: Boolean = true,
    hyperlinksEnabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = 5f,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    KmpPdfViewer(
        source = remember(uri) { PdfSource.auto(uri) },
        modifier = modifier,
        title = title,
        fileName = fileName,
        onBack = onBack,
        backLabel = backLabel,
        showTopBar = showTopBar,
        showBack = showBack,
        showSearch = showSearch,
        showShare = showShare,
        showDownload = showDownload,
        showPageIndicator = showPageIndicator,
        zoomEnabled = zoomEnabled,
        doubleTapToZoom = doubleTapToZoom,
        showZoomControls = showZoomControls,
        textSelectable = textSelectable,
        hyperlinksEnabled = hyperlinksEnabled,
        backgroundColor = backgroundColor,
        pageBackgroundColor = pageBackgroundColor,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        renderDensity = renderDensity,
        maxZoom = maxZoom,
        cacheStrategy = cacheStrategy,
    )
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
