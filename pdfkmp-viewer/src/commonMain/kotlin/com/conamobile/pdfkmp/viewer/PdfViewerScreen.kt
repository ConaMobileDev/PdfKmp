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
 * **Plug-and-play PDF viewer screen.** Drop one of these into your
 * navigation graph, hand it a [PdfSource] / [PdfDocument] / raw
 * `ByteArray` / URI, and you get the entire experience for free —
 * topbar, search bar morph, share & save actions, hyperlink &
 * selection overlays, page indicator, the lot.
 *
 * The composable owns every piece of state internally
 * (`searchOpen`, `searchQuery`, `activeMatchIndex`, share / save
 * actions). The host only configures *what* the screen can do — via
 * the visibility toggles below — and *what* should happen on back —
 * via [onBack]. Nothing else is required.
 *
 * Need finer control (custom topbar, custom share affordance, FAB
 * placement, multi-action overlays)? Drop down to the lower-level
 * [PdfViewer] / [PdfViewerTopBar] / [PdfSearchBar] composables; this
 * screen is intentionally an opinionated default rather than a
 * lowest-common-denominator API.
 *
 * Topbar variant follows the platform convention: Minimal Mono on
 * Android, Classic iOS Native on iOS — both honour the design
 * handoff in `design_handoff_pdf_topbar/`.
 *
 * @param source PDF payload. `PdfSource.of(document)` keeps text
 *   selection + hyperlinks alive; `PdfSource.of(bytes)` is a plain
 *   bitmap experience.
 * @param modifier applied to the outer [Column].
 * @param title shown in the topbar's centered title (Classic iOS) /
 *   bold first line (Minimal Mono).
 * @param fileName user-visible filename surfaced to the share sheet
 *   and the "Saved to Downloads" file system entry. Must include
 *   `.pdf`.
 * @param onBack callback wired to the back chip / chevron. `null`
 *   removes the back affordance entirely.
 * @param backLabel iOS-only previous-screen label rendered next to
 *   the chevron (e.g. `"Files"`). Ignored on Android.
 * @param showBack hide / show the back affordance independently of
 *   [onBack]. Defaults to `true` when [onBack] is provided.
 * @param showSearch hide / show the search affordance. The button
 *   is also auto-suppressed when the [source] carries no text
 *   runs, since search would have nothing to scan.
 * @param showShare hide / show the share affordance.
 * @param showDownload hide / show the download affordance.
 * @param showPageIndicator hide / show the bottom-centre page chip.
 * @param zoomEnabled master switch for pinch + double-tap zoom.
 * @param doubleTapToZoom independent toggle for the double-tap
 *   shortcut.
 * @param textSelectable toggles the invisible selectable text
 *   overlay. Only effective on documents loaded from a [PdfDocument].
 * @param hyperlinksEnabled toggles the invisible clickable layer
 *   that opens hyperlink annotations in the system browser. Same
 *   `PdfDocument`-only caveat as [textSelectable].
 * @param backgroundColor colour painted behind the page bitmaps.
 *   Defaults to the active Material 3 surface tone.
 * @param pageBackgroundColor colour painted behind each individual
 *   page (visible while it's still rasterising).
 * @param contentPadding padding around the [androidx.compose.foundation.lazy.LazyColumn]
 *   content. Defaults to zero for a fully edge-to-edge layout.
 * @param pageSpacing vertical gap between page previews.
 * @param renderDensity baseline scaling factor applied during
 *   rasterisation.
 * @param maxZoom upper bound for the pinch gesture.
 */
@Composable
public fun PdfViewerScreen(
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
                // Auto-suppress the search affordance when the source
                // can't produce matches — saves consumers from
                // duplicating the `textRuns.isNotEmpty()` check.
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
            // Built-in share FAB suppressed — the topbar already
            // hosts every action this screen needs.
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
 * [PdfDocument]-flavoured overload — defers to the [PdfSource]
 * version after wrapping the document in a `PdfSource.Document`. This
 * is the recommended entry point when the PDF was authored through
 * the PdfKmp DSL because text selection and hyperlinks light up
 * automatically.
 */
@Composable
public fun PdfViewerScreen(
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
    PdfViewerScreen(
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
 * Raw-bytes overload — for payloads that came from disk, the
 * network, an `ACTION_OPEN_DOCUMENT` picker, etc. Text selection
 * and hyperlink layers are inert because the bytes carry no
 * position metadata; everything else (zoom, share, save, page
 * indicator) works exactly the same.
 */
@Composable
public fun PdfViewerScreen(
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
    PdfViewerScreen(
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
 * URI overload — loads the bytes asynchronously via the platform's
 * native resolution machinery (Android `ContentResolver` for
 * `content://`, `URL.openStream` for HTTPS, etc. — see
 * [loadPdfBytesFromUri] for the full list). While the load is in
 * flight the topbar renders with a [CircularProgressIndicator] in
 * place of the page area; on failure the screen surfaces an inline
 * error message so the host can still navigate back via [onBack].
 */
@Composable
public fun PdfViewerScreen(
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
        bytes != null -> PdfViewerScreen(
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
            // Show the topbar even while loading / on error so the
            // user can still navigate back.
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
