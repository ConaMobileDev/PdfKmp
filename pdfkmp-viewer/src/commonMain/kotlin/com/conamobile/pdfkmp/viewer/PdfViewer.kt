package com.conamobile.pdfkmp.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.text.PdfHyperlink
import com.conamobile.pdfkmp.text.PdfTextRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Hard-coded ceiling for pinch zoom. 5× matches iOS PDFKit's default. */
private const val DEFAULT_MAX_ZOOM: Float = 5f

/** Step zoom triggered by a double tap, between `1×` and [DEFAULT_MAX_ZOOM]. */
private const val DOUBLE_TAP_ZOOM: Float = 2.5f

/**
 * Multiplicative zoom step applied per mouse-wheel notch while Ctrl (or ⌘
 * on macOS) is held — the Desktop mouse zoom gesture. A bare macOS trackpad
 * pinch goes through the separate magnify path ([installTrackpadPinchZoom]),
 * not this scroll handler. Touch platforms never deliver a Ctrl-modified
 * scroll, so this is inert there and pinch-to-zoom stays the mobile path.
 */
private const val DESKTOP_SCROLL_ZOOM_STEP: Float = 1.12f

/** Multiplicative zoom step per click of the Desktop on-screen ＋ / － buttons. */
private const val DESKTOP_BUTTON_ZOOM_STEP: Float = 1.4f

/**
 * Cap on the multiplier applied to [PdfViewer]'s `renderDensity` while
 * zoomed in, on **touch** platforms. Mobile RAM is tight, so a 2× boost on
 * a 2× base (4× pixels — retina-crisp) is the ceiling; going higher risks
 * OOM on a low-end device for little visible gain.
 *
 * Desktop has no such constraint, so it tracks the full zoom factor (up to
 * `maxZoom`) — see the density `LaunchedEffect` — so text keeps getting
 * sharper the more you zoom in. The result is still bounded by
 * [MAX_EFFECTIVE_DENSITY].
 */
private const val MAX_DENSITY_BOOST: Float = 2f

/**
 * Absolute ceiling on the effective render density (`renderDensity ×
 * zoom`), in the `points → pixels` sense. 10 ≈ 720 DPI — past the point of
 * perceivable sharpening even on a hi-DPI display — and caps the worst-case
 * per-page bitmap size so an extreme `maxZoom` can't blow up the heap.
 */
private const val MAX_EFFECTIVE_DENSITY: Float = 10f

/**
 * Debounce window before a stable zoom value triggers a higher-density
 * re-render. Keeps the renderer idle during an active pinch gesture and
 * snaps the bitmap to its sharper version once the user has settled.
 */
private const val DENSITY_REFRESH_DELAY_MS: Long = 250L

/**
 * Compose Multiplatform viewer for PDFs produced by `:pdfkmp` (or any
 * other source that hands you raw `%PDF-…` bytes).
 *
 * Pages stream through the host platform's native PDF renderer
 * (Android `PdfRenderer`, iOS `PDFKit.PDFDocument`) one at a time,
 * driven by a [LazyColumn] — even hundred-page documents only keep the
 * visible bitmaps in memory. The encoded PDF retains its vector
 * geometry, so what users see on screen and what reaches the share
 * sheet are identical.
 *
 * Gestures (document-wide):
 *
 * - **Pinch** anywhere to zoom the whole document between `1×` and
 *   [maxZoom]. The pinch focal point stays under the user's fingers on
 *   the horizontal axis.
 * - **Single-finger drag** scrolls vertically through the document.
 *   Once the user has zoomed in, drags also pan the horizontally
 *   scrolling viewport.
 * - **Double tap** (double click on Desktop) toggles between `1×` and a
 *   comfortable reading zoom (`2.5×`).
 * - **Desktop:** a **macOS trackpad pinch** zooms anchored under the
 *   cursor; **Ctrl/⌘ + mouse wheel** zooms with a mouse; a plain wheel
 *   scrolls through pages. Windows/Linux trackpads (which don't deliver a
 *   pinch gesture to the toolkit) use the on-screen ＋ / − controls
 *   ([showZoomControls]).
 *
 * Sharpness on zoom is handled automatically — once the gesture settles,
 * the viewer re-rasterises each visible page at `renderDensity × zoom`, so
 * the deeper you zoom the sharper the text gets. On Desktop this tracks the
 * full zoom range (up to `maxZoom`); touch platforms cap the boost at `2×`
 * to bound memory. The re-render is debounced, so the gesture itself stays
 * smooth (the existing bitmap is scaled until the sharper one is ready).
 *
 * @param source encoded PDF wrapped in a [PdfSource]. Use
 *   [PdfSource.of] to convert from a [PdfDocument] or raw bytes.
 * @param modifier applied to the outer [Box].
 * @param showShareButton toggles the built-in share FAB. `true` by
 *   default; set `false` to suppress it (e.g. when the host screen
 *   already has its own share affordance).
 * @param shareFileName user-visible filename presented to the share
 *   sheet (must include the `.pdf` extension).
 * @param backgroundColor colour painted behind the page bitmaps. The
 *   default tracks the active Material 3 theme.
 * @param pageBackgroundColor colour painted behind each individual page
 *   (visible while the page is still rasterising or through transparent
 *   margins inside the PDF itself).
 * @param contentPadding padding applied around the [LazyColumn] content.
 *   Defaults to zero for a fully edge-to-edge layout.
 * @param pageSpacing vertical gap between page previews. Defaults to a
 *   tight `4.dp` so adjacent pages read as a continuous document.
 * @param renderDensity baseline rasterisation density at `1×` zoom — the
 *   sharpness before the user zooms at all. Defaults per platform via
 *   [pdfViewerDefaultRenderDensity]: **3× on Desktop** (large / hi-DPI
 *   screens, ample RAM), **2× on Android / iOS** (retina-crisp, memory-
 *   bounded). Each unit is `72 DPI`. Bump higher (`4f`+) for very fine
 *   print or large technical drawings, at proportionally more memory per
 *   page. Zoom multiplies on top of this — see the sharpness note above.
 * @param maxZoom upper bound for the pinch gesture. Defaults to `5f`
 *   to match iOS PDFKit.
 * @param zoomEnabled master switch for both pinch-to-zoom and
 *   double-tap-to-zoom. `false` keeps the document permanently at `1×`
 *   and disables the auxiliary horizontal pan logic — useful for
 *   read-only one-page receipts where zoom would only get in the way.
 * @param doubleTapToZoom toggles the double-tap shortcut between `1×`
 *   and a comfortable reading zoom. Independent of [zoomEnabled];
 *   leaving pinch on while suppressing double-tap is a common
 *   accessibility preference. Has no effect when [zoomEnabled] is
 *   `false`.
 * @param showZoomControls shows an on-screen ＋ / − zoom pill **on
 *   Desktop only** (touch platforms have native pinch, so it never
 *   appears there). `true` by default; set `false` to hide it — e.g.
 *   when the host provides its own zoom UI, or to keep a clean page
 *   view and rely on double-click + Ctrl-scroll (mouse). No effect
 *   when [zoomEnabled] is `false`.
 * @param textSelectable toggles the invisible selectable text overlay.
 *   `true` by default. Only effective on documents loaded via
 *   [PdfSource.of] from a PdfKmp [PdfDocument] — opaque external PDFs
 *   carry no text-position data so the overlay has nothing to render
 *   regardless of this flag.
 * @param hyperlinksEnabled toggles the invisible clickable overlay
 *   that opens hyperlink annotations in the system browser. Same
 *   "needs PdfKmp-built document" caveat as [textSelectable].
 * @param showPageIndicator toggles the bottom-centre `n / total`
 *   chip. `true` by default.
 * @param shareButtonAlignment positions the built-in share FAB inside
 *   the outer [Box]. Defaults to [Alignment.BottomEnd] to match
 *   Material 3 guidance.
 * @param shareButtonPadding padding between the built-in share FAB
 *   and the nearest [Box] edge.
 * @param searchHighlights translucent yellow rectangles painted over
 *   page bitmaps to mark in-document search matches. Pass the result
 *   of [searchPdfText] (or any custom logic that produces
 *   [PdfSearchHighlight]s) — empty list disables the layer.
 * @param activeSearchHighlightIndex index into [searchHighlights]
 *   identifying the "current" match. Rendered with a stronger fill
 *   and auto-scrolled into view whenever it changes. `-1` means no
 *   active match (all highlights painted with the resting fill).
 */
@Composable
public fun PdfViewer(
    source: PdfSource,
    modifier: Modifier = Modifier,
    showShareButton: Boolean = true,
    shareFileName: String = "document.pdf",
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = pdfViewerDefaultRenderDensity,
    maxZoom: Float = DEFAULT_MAX_ZOOM,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    textSelectable: Boolean = true,
    hyperlinksEnabled: Boolean = true,
    showPageIndicator: Boolean = true,
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
    searchHighlights: List<PdfSearchHighlight> = emptyList(),
    activeSearchHighlightIndex: Int = -1,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    // Stage 1 — bytes. In-memory variants resolve synchronously via
    // `inMemoryBytesOrNull()`; async variants stay null until the
    // platform loader returns (LaunchedEffect below).
    var bytes by remember(source) { mutableStateOf(source.inMemoryBytesOrNull()) }
    var loadError by remember(source) { mutableStateOf<String?>(null) }

    LaunchedEffect(source) {
        if (bytes != null || loadError != null) return@LaunchedEffect
        try {
            bytes = source.loadBytes()
        } catch (t: Throwable) {
            loadError = t.message ?: t::class.simpleName ?: "Unknown error"
        }
    }

    val textRunsByPage = remember(source, textSelectable) {
        if (textSelectable) source.textRuns().groupBy { it.pageIndex } else emptyMap()
    }
    val hyperlinksByPage = remember(source, hyperlinksEnabled) {
        if (hyperlinksEnabled) source.hyperlinks().groupBy { it.pageIndex } else emptyMap()
    }
    val urlLauncher = if (hyperlinksEnabled) rememberPdfUrlLauncher() else null

    // Stage 2 — renderer. Re-opens whenever the resolved bytes change.
    val currentBytes = bytes
    var renderer by remember(currentBytes) { mutableStateOf<PdfPageRenderer?>(null) }
    var loading by remember(currentBytes) { mutableStateOf(currentBytes != null) }
    var error by remember(currentBytes) { mutableStateOf(false) }

    // Bitmap cache survives LazyColumn item recomposition, which is
    // the whole point: scroll-back to a previously viewed page is a
    // memory hit instead of a fresh rasterisation. The cache is keyed
    // on `currentBytes` so a new document gets a fresh cache.
    val cache = remember(currentBytes) {
        PdfBitmapCache(budgetBytes = bitmapCacheBudgetBytes())
    }

    val scope = rememberCoroutineScope()

    DisposableEffect(currentBytes) {
        if (currentBytes == null) return@DisposableEffect onDispose { }
        var openedRenderer: PdfPageRenderer? = null
        val job = scope.launch {
            val opened = openPdfRenderer(currentBytes)
            openedRenderer = opened
            renderer = opened
            error = opened == null || opened.pageCount == 0
            loading = false
        }
        onDispose {
            job.cancel()
            openedRenderer?.close()
            renderer = null
            // Best-effort cache flush — `clear` is suspending, so we
            // can't await it from `onDispose`. Launching on the
            // (already-disposed) scope is safe because cache state
            // is independent of the renderer; in the worst case the
            // bitmaps stay reachable for one extra GC cycle.
            scope.launch { cache.clear() }
        }
    }

    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val zoom = remember { Animatable(1f) }
    var stableEffectiveDensity by remember(renderDensity) {
        mutableFloatStateOf(renderDensity)
    }

    LaunchedEffect(zoom.value, renderDensity, maxZoom) {
        delay(DENSITY_REFRESH_DELAY_MS)
        // Desktop tracks the full zoom factor so text keeps sharpening the
        // more you zoom in (re-rasterised at zoom×renderDensity once the
        // gesture settles); touch platforms keep the tighter mobile cap for
        // memory. Both are bounded by the absolute MAX_EFFECTIVE_DENSITY.
        val boostCap = if (pdfViewerIsDesktop) maxZoom else MAX_DENSITY_BOOST
        val multiplier = zoom.value.coerceIn(1f, boostCap)
        stableEffectiveDensity = (renderDensity * multiplier).coerceAtMost(MAX_EFFECTIVE_DENSITY)
    }

    // Snap back to 1× whenever the host disables zooming so the
    // document doesn't get stuck mid-zoom after a runtime toggle.
    LaunchedEffect(zoomEnabled) {
        if (!zoomEnabled && zoom.value != 1f) {
            zoom.snapTo(1f)
            horizontalScrollState.scrollTo(0)
        }
    }

    // Group search highlights by page index for cheap per-page lookup.
    // Resolve the active highlight (if any) so the page item that
    // owns it can paint the stronger fill.
    val searchHighlightsByPage = remember(searchHighlights) {
        searchHighlights.groupBy { it.pageIndex }
    }
    val activeSearchHighlight = searchHighlights.getOrNull(activeSearchHighlightIndex)

    // When the active match changes, scroll the LazyColumn so the page
    // hosting it is visible. Page-level scroll is good enough for v1
    // — within-page scrolling would also need the zoomed-in horizontal
    // position, which can land in a follow-up.
    LaunchedEffect(activeSearchHighlightIndex, activeSearchHighlight?.pageIndex) {
        val target = activeSearchHighlight?.pageIndex ?: return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    val shareAction = if (showShareButton) rememberPdfShareAction() else null
    val current = renderer

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        when {
            loadError != null || error || (currentBytes != null && current == null && !loading) ->
                PdfViewerErrorState(
                    modifier = Modifier.align(Alignment.Center),
                )

            currentBytes == null || loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            else -> {
                PdfPagesContent(
                    renderer = current!!,
                    cache = cache,
                    cacheStrategy = cacheStrategy,
                    listState = listState,
                    horizontalScrollState = horizontalScrollState,
                    zoom = zoom,
                    maxZoom = maxZoom,
                    zoomEnabled = zoomEnabled,
                    doubleTapToZoom = doubleTapToZoom,
                    showZoomControls = showZoomControls,
                    effectiveDensity = stableEffectiveDensity,
                    pageBackgroundColor = pageBackgroundColor,
                    contentPadding = contentPadding,
                    pageSpacing = pageSpacing,
                    textRunsByPage = textRunsByPage,
                    hyperlinksByPage = hyperlinksByPage,
                    urlLauncher = urlLauncher,
                    searchHighlightsByPage = searchHighlightsByPage,
                    activeSearchHighlight = activeSearchHighlight,
                    scope = scope,
                )
                if (showPageIndicator) {
                    PdfPageIndicator(
                        listState = listState,
                        pageCount = current.pageCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp),
                    )
                }
            }
        }

        if (shareAction != null && current != null && currentBytes != null && !error) {
            FloatingActionButton(
                onClick = { shareAction(currentBytes, shareFileName) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(),
                modifier = Modifier
                    .align(shareButtonAlignment)
                    .padding(shareButtonPadding),
            ) {
                Icon(imageVector = PdfShareIcon, contentDescription = "Share PDF")
            }
        }

    }
}

/**
 * Convenience overload that accepts a [PdfDocument] directly.
 *
 * Equivalent to `PdfViewer(PdfSource.of(document), …)` — saves the
 * caller from building a [PdfSource] when the document is already in
 * hand from `pdf { }` / `pdfAsync { }`.
 */
@Composable
public fun PdfViewer(
    document: PdfDocument,
    modifier: Modifier = Modifier,
    showShareButton: Boolean = true,
    shareFileName: String = "document.pdf",
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = pdfViewerDefaultRenderDensity,
    maxZoom: Float = DEFAULT_MAX_ZOOM,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    textSelectable: Boolean = true,
    hyperlinksEnabled: Boolean = true,
    showPageIndicator: Boolean = true,
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
    searchHighlights: List<PdfSearchHighlight> = emptyList(),
    activeSearchHighlightIndex: Int = -1,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    PdfViewer(
        source = remember(document) { PdfSource.of(document) },
        modifier = modifier,
        showShareButton = showShareButton,
        shareFileName = shareFileName,
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
        shareButtonAlignment = shareButtonAlignment,
        shareButtonPadding = shareButtonPadding,
        searchHighlights = searchHighlights,
        activeSearchHighlightIndex = activeSearchHighlightIndex,
        cacheStrategy = cacheStrategy,
    )
}

/**
 * Convenience overload for callers that already have raw PDF bytes
 * (downloaded from the network, picked from `ACTION_OPEN_DOCUMENT`,
 * etc.) and don't need to construct a [PdfSource] explicitly.
 */
@Composable
public fun PdfViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    showShareButton: Boolean = true,
    shareFileName: String = "document.pdf",
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 4.dp,
    renderDensity: Float = pdfViewerDefaultRenderDensity,
    maxZoom: Float = DEFAULT_MAX_ZOOM,
    zoomEnabled: Boolean = true,
    doubleTapToZoom: Boolean = true,
    showZoomControls: Boolean = true,
    showPageIndicator: Boolean = true,
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
    searchHighlights: List<PdfSearchHighlight> = emptyList(),
    activeSearchHighlightIndex: Int = -1,
    cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
) {
    PdfViewer(
        source = remember(bytes) { PdfSource.Bytes(bytes) },
        modifier = modifier,
        showShareButton = showShareButton,
        shareFileName = shareFileName,
        backgroundColor = backgroundColor,
        pageBackgroundColor = pageBackgroundColor,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        renderDensity = renderDensity,
        maxZoom = maxZoom,
        zoomEnabled = zoomEnabled,
        doubleTapToZoom = doubleTapToZoom,
        showZoomControls = showZoomControls,
        showPageIndicator = showPageIndicator,
        shareButtonAlignment = shareButtonAlignment,
        shareButtonPadding = shareButtonPadding,
        searchHighlights = searchHighlights,
        activeSearchHighlightIndex = activeSearchHighlightIndex,
        cacheStrategy = cacheStrategy,
    )
}

/**
 * Inner composable that owns the document-wide gesture detector and the
 * scrollable container hierarchy. Split out from [PdfViewer] so the
 * latter stays focused on lifecycle / chrome and its parameter list
 * remains the public surface area.
 *
 * The hierarchy is intentionally:
 *
 * ```
 * BoxWithConstraints              // captures pinch + double tap
 *   Box(horizontalScroll = ...)   // pans X when contentWidth > viewport
 *     LazyColumn(width = ...)     // virtualises pages, scrolls Y
 *       PdfPageItem               // re-rasterises at effectiveDensity
 * ```
 *
 * — `LazyColumn` provides vertical virtualisation, the parent
 * `horizontalScroll` provides horizontal panning when the document is
 * zoomed in, and the gestures are read from the outer container so they
 * compose with both scrollables instead of fighting them.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PdfPagesContent(
    renderer: PdfPageRenderer,
    cache: PdfBitmapCache,
    cacheStrategy: PdfPageCacheStrategy,
    listState: LazyListState,
    horizontalScrollState: ScrollState,
    zoom: Animatable<Float, *>,
    maxZoom: Float,
    zoomEnabled: Boolean,
    doubleTapToZoom: Boolean,
    showZoomControls: Boolean,
    effectiveDensity: Float,
    pageBackgroundColor: Color,
    contentPadding: PaddingValues,
    pageSpacing: Dp,
    textRunsByPage: Map<Int, List<PdfTextRun>>,
    hyperlinksByPage: Map<Int, List<PdfHyperlink>>,
    urlLauncher: PdfUrlLauncher?,
    searchHighlightsByPage: Map<Int, List<PdfSearchHighlight>>,
    activeSearchHighlight: PdfSearchHighlight?,
    scope: CoroutineScope,
) {
    // Prefetch loop — keeps the requested window around the currently
    // visible page warm so the user never sees a fresh-rasterisation
    // delay when scrolling within the window. `collectLatest` cancels
    // the prior pass on every scroll, so a fast flick doesn't queue
    // up renders for pages the user is already past.
    LaunchedEffect(renderer, cache, cacheStrategy, effectiveDensity) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { visible ->
                val (before, after) = cacheStrategy.window(renderer.pageCount)
                val start = (visible - before).coerceAtLeast(0)
                val end = (visible + after).coerceAtMost(renderer.pageCount - 1)
                // Visible page first so it always wins the render
                // mutex, then expanding outward — neighbours that
                // miss the window simply queue behind.
                val order = buildPrefetchOrder(visible, start, end)
                for (index in order) {
                    renderAndCache(renderer, cache, index, effectiveDensity)
                }
            }
    }
    // Desktop modifier-key state. Scroll PointerEvents don't reliably carry
    // keyboardModifiers across platforms, so we also consult the window-level
    // snapshot when deciding whether a scroll is a zoom request.
    val windowInfo = LocalWindowInfo.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val contentWidth = viewportWidth * zoom.value

        // Anchored zoom shared by the Ctrl+scroll handler (focus = cursor)
        // and the Desktop on-screen ＋ / － buttons (focus = viewport centre).
        // The horizontal scroll + LazyColumn offset are nudged so the focal
        // point stays put as the content grows/shrinks.
        val viewportCenterX = constraints.maxWidth / 2f
        val viewportCenterY = constraints.maxHeight / 2f
        fun applyZoom(targetZoom: Float, focusX: Float, focusY: Float) {
            val previous = zoom.value
            val target = targetZoom.coerceIn(1f, maxZoom)
            if (target == previous) return
            val factor = target / previous
            val anchoredScrollX = (focusX + horizontalScrollState.value) * factor - focusX
            val targetScrollX = anchoredScrollX.toInt().coerceAtLeast(0)
            val firstOffsetBefore = listState.firstVisibleItemScrollOffset.toFloat()
            val deltaY = (firstOffsetBefore + focusY) * (factor - 1f)
            scope.launch {
                zoom.snapTo(target)
                listState.scrollBy(deltaY)
            }
            scope.launch {
                horizontalScrollState.scrollTo(targetScrollX)
            }
        }

        // Latest cursor position (Desktop), so a trackpad pinch zooms toward
        // the pointer rather than the viewport centre.
        var lastCursor by remember { mutableStateOf<Offset?>(null) }

        // macOS trackpad pinch: a bare pinch is an NSEvent magnify gesture, not
        // a scroll/wheel event, so it never reaches the pointer handlers above.
        // We bridge Apple's gesture channel here and feed the accumulated
        // magnification into the same anchored-zoom path. No-op off macOS.
        if (zoomEnabled) {
            DisposableEffect(zoomEnabled, maxZoom) {
                val dispose = installTrackpadPinchZoom { delta ->
                    val focus = lastCursor ?: Offset(viewportCenterX, viewportCenterY)
                    applyZoom(zoom.value * (1f + delta), focus.x, focus.y)
                }
                onDispose { dispose() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Passive cursor tracker — never consumes, just records the
                    // pointer position to anchor trackpad-pinch zoom.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.lastOrNull()?.let { lastCursor = it.position }
                        }
                    }
                }
                .pointerInput(maxZoom, zoomEnabled) {
                    if (!zoomEnabled) return@pointerInput
                    // Initial-pass handler so we can take precedence
                    // over the inner LazyColumn / horizontalScroll
                    // scrollables when the user is pinching or panning
                    // a zoomed-in document. At zoom == 1 we leave
                    // single-finger drags unconsumed so the LazyColumn
                    // continues to drive the page-to-page scroll with
                    // its native fling and overscroll.
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break

                            val zoomChange = event.calculateZoom()
                            val pan = event.calculatePan()

                            if (pressed >= 2 &&
                                (zoomChange != 1f || pan != Offset.Zero)
                            ) {
                                val previous = zoom.value
                                val target = (previous * zoomChange)
                                    .coerceIn(1f, maxZoom)
                                val factor = if (previous == 0f) 1f else target / previous
                                val centroid = event.calculateCentroid()

                                // X axis: horizontalScroll exposes an
                                // absolute pixel value, so we compute
                                // the anchored scroll directly. The
                                // pan component shifts the centroid,
                                // which in turn shifts the anchor.
                                val anchoredScrollX = ((centroid.x +
                                    horizontalScrollState.value) * factor - centroid.x)
                                val targetScrollX = (anchoredScrollX - pan.x)
                                    .toInt()
                                    .coerceAtLeast(0)

                                // Y axis: LazyColumn re-measures with
                                // new (taller) items but preserves
                                // firstVisibleItemScrollOffset
                                // verbatim — content grows downward
                                // from that anchor. The corrective
                                // delta keeps the focal Y point under
                                // the user's fingers, plus the pan
                                // delta translates the centroid.
                                val firstOffsetBefore =
                                    listState.firstVisibleItemScrollOffset.toFloat()
                                val deltaY =
                                    (firstOffsetBefore + centroid.y) * (factor - 1f) - pan.y

                                scope.launch {
                                    zoom.snapTo(target)
                                    listState.scrollBy(deltaY)
                                }
                                scope.launch {
                                    horizontalScrollState.scrollTo(targetScrollX)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (
                                pressed == 1 &&
                                zoom.value > 1.01f &&
                                pan != Offset.Zero
                            ) {
                                // Free 2D pan once the document is
                                // zoomed in. We dispatch deltas to
                                // both scroll states ourselves so a
                                // diagonal drag isn't axis-locked by
                                // the inner scrollables.
                                scope.launch { listState.scrollBy(-pan.y) }
                                scope.launch { horizontalScrollState.scrollBy(-pan.x) }
                                event.changes.forEach { it.consume() }
                            }
                            // pressed == 1 && zoom == 1 → leave the
                            // event unconsumed; LazyColumn picks up
                            // the drag in the Main pass and scrolls
                            // vertically between pages.
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(zoomEnabled, doubleTapToZoom) {
                    if (!zoomEnabled || !doubleTapToZoom) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            val zoomingIn = zoom.value <= 1.01f
                            val target = if (zoomingIn) DOUBLE_TAP_ZOOM else 1f
                            scope.launch { zoom.animateTo(target) }
                            if (!zoomingIn) {
                                scope.launch {
                                    horizontalScrollState.animateScrollTo(0)
                                }
                            }
                        },
                    )
                }
                .pointerInput(zoomEnabled, maxZoom) {
                    if (!zoomEnabled) return@pointerInput
                    // Mouse zoom: Ctrl/⌘ + scroll wheel zooms, anchored under
                    // the cursor. Handled in the Initial pass and consumed so
                    // the LazyColumn doesn't ALSO scroll; a plain (unmodified)
                    // scroll is left untouched so the wheel still drives
                    // page-to-page navigation. macOS trackpad PINCH goes
                    // through the magnify path, not here. Pure-touch platforms
                    // never send a Ctrl-modified scroll (so this is inert and
                    // pinch stays their path); an Android device with a
                    // physical mouse + keyboard can still Ctrl+wheel to zoom,
                    // which is the intended behaviour there too.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue

                            val eventMods = event.keyboardModifiers
                            val winMods = windowInfo.keyboardModifiers
                            val zoomModifier =
                                eventMods.isCtrlPressed || eventMods.isMetaPressed ||
                                    winMods.isCtrlPressed || winMods.isMetaPressed
                            if (!zoomModifier) continue // plain scroll → scrollables handle it

                            val scrollY = change.scrollDelta.y
                            // Only a vertical delta is a zoom; leave a pure
                            // horizontal Ctrl-scroll for the scrollables (don't
                            // consume it, or horizontal panning would be blocked).
                            if (scrollY == 0f) continue
                            change.consume()
                            // Wheel up reports a negative delta → zoom in.
                            val step = if (scrollY < 0f) {
                                DESKTOP_SCROLL_ZOOM_STEP
                            } else {
                                1f / DESKTOP_SCROLL_ZOOM_STEP
                            }
                            applyZoom(zoom.value * step, change.position.x, change.position.y)
                        }
                    }
                },
        ) {
            Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight(),
                    contentPadding = contentPadding,
                    // Scale the inter-page gap with the zoom factor so the
                    // WHOLE content column (pages + gaps) scales uniformly.
                    // If the gap stayed fixed while pages grew, the cursor-
                    // anchored zoom math would drift on lower pages (the gap
                    // contribution to the scroll offset wouldn't scale),
                    // making pinch zoom toward the wrong point.
                    verticalArrangement = Arrangement.spacedBy(pageSpacing * zoom.value),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(
                        count = renderer.pageCount,
                        key = { it },
                    ) { index ->
                        PdfPageItem(
                            renderer = renderer,
                            cache = cache,
                            index = index,
                            pageSize = renderer.pageSizes.getOrNull(index)
                                ?: PageSize(widthPoints = 1f, heightPoints = 1f),
                            pageBackgroundColor = pageBackgroundColor,
                            renderDensity = effectiveDensity,
                            textRuns = textRunsByPage[index].orEmpty(),
                            hyperlinks = hyperlinksByPage[index].orEmpty(),
                            urlLauncher = urlLauncher,
                            searchHighlights = searchHighlightsByPage[index].orEmpty(),
                            activeSearchHighlight = activeSearchHighlight
                                ?.takeIf { it.pageIndex == index },
                        )
                    }
                }
            }
        }

        // Desktop zoom controls — trackpads don't deliver pinch (and macOS
        // sends Ctrl-modified scroll without the modifier flag), so an
        // explicit ＋ / − / reset pill is the reliable zoom affordance. The
        // cursor-anchored Ctrl+scroll path above still works for mice that
        // do report modifiers. Hidden on touch platforms (pinch is native).
        if (showZoomControls && zoomEnabled && pdfViewerIsDesktop) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(50),
                color = Color(0xC7141416),
                contentColor = Color.White,
                shadowElevation = 4.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ZoomControlButton("+") {
                        applyZoom(zoom.value * DESKTOP_BUTTON_ZOOM_STEP, viewportCenterX, viewportCenterY)
                    }
                    ZoomControlButton("−") { // U+2212 minus sign
                        applyZoom(zoom.value / DESKTOP_BUTTON_ZOOM_STEP, viewportCenterX, viewportCenterY)
                    }
                }
            }
        }
    }
}

/** One round cell in the Desktop zoom-control pill. */
@Composable
private fun ZoomControlButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PdfPageItem(
    renderer: PdfPageRenderer,
    cache: PdfBitmapCache,
    index: Int,
    pageSize: PageSize,
    pageBackgroundColor: Color,
    renderDensity: Float,
    textRuns: List<PdfTextRun>,
    hyperlinks: List<PdfHyperlink>,
    urlLauncher: PdfUrlLauncher?,
    searchHighlights: List<PdfSearchHighlight>,
    activeSearchHighlight: PdfSearchHighlight?,
) {
    // The bitmap state intentionally outlives `renderDensity` changes
    // so the previous low-resolution frame stays on screen while the
    // higher-density re-render is in flight — no zoom-induced flicker.
    var bitmap by remember(renderer, index) {
        mutableStateOf<ImageBitmap?>(null)
    }

    // Cache lookup is the fast path — when the user has already
    // visited this page (within the current document) the bitmap is
    // resident in `cache` and `renderAndCache` returns synchronously
    // after a single mutex round-trip. Only the first visit (or a
    // visit after the page has been evicted) actually rasterises.
    LaunchedEffect(renderer, cache, index, renderDensity) {
        val rendered = renderAndCache(renderer, cache, index, renderDensity)
        if (rendered != null) {
            bitmap = rendered
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .background(pageBackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            if (searchHighlights.isNotEmpty()) {
                // Painted ABOVE the bitmap but BELOW the text-selection
                // overlay so long-press selection still wins on the
                // same region — yellow rectangles only need to be
                // visible, not tappable.
                PdfSearchOverlay(
                    highlights = searchHighlights,
                    activeHighlight = activeSearchHighlight,
                    pageSize = pageSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (textRuns.isNotEmpty()) {
                PdfTextSelectionOverlay(
                    textRuns = textRuns,
                    pageSize = pageSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (hyperlinks.isNotEmpty() && urlLauncher != null) {
                // Drawn AFTER the text overlay so a clickable Box sits
                // on top — Compose's hit testing routes the tap to the
                // topmost node, and the SelectionContainer below
                // handles long-press separately so both gestures
                // coexist on the same region.
                PdfHyperlinkOverlay(
                    hyperlinks = hyperlinks,
                    pageSize = pageSize,
                    onUrlClicked = urlLauncher::invoke,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Renders translucent yellow rectangles over the rasterised page for
 * every in-document search match. The active match (if it lives on
 * this page) gets a stronger fill so the user can spot the current
 * highlight after tapping next/previous.
 *
 * No interaction is wired — the rectangles are pure paint, scrolled
 * into view by [PdfViewer] via `LazyListState.animateScrollToItem`.
 */
@Composable
private fun PdfSearchOverlay(
    highlights: List<PdfSearchHighlight>,
    activeHighlight: PdfSearchHighlight?,
    pageSize: PageSize,
    modifier: Modifier = Modifier,
) {
    val widthPoints = pageSize.widthPoints.takeIf { it > 0f } ?: return
    val heightPoints = pageSize.heightPoints.takeIf { it > 0f } ?: return

    BoxWithConstraints(modifier = modifier) {
        val scaleX = maxWidth.value / widthPoints
        val scaleY = maxHeight.value / heightPoints
        highlights.forEach { match ->
            val isActive = match === activeHighlight
            Box(
                modifier = Modifier
                    .offset(
                        x = (match.xPoints * scaleX).dp,
                        y = (match.yPoints * scaleY).dp,
                    )
                    .size(
                        width = (match.widthPoints * scaleX).dp,
                        height = (match.heightPoints * scaleY).dp,
                    )
                    .background(
                        color = if (isActive) ActiveMatchFill else IdleMatchFill,
                    ),
            )
        }
    }
}

private val IdleMatchFill = Color(0x66FFE57F)    // ~40% amber 200
private val ActiveMatchFill = Color(0xCCFFB300)  // ~80% amber 700

/**
 * Renders an invisible clickable layer over the rasterised page so
 * hyperlink annotations baked into the PDF behave like real links —
 * tapping fires [onUrlClicked] which delegates to the platform's URL
 * handler (Android `ACTION_VIEW`, iOS `UIApplication.openURL`).
 *
 * Sits **below** [PdfTextSelectionOverlay] in the z-order so that
 * long-press text selection wins over a tap; a tap that doesn't slide
 * triggers the link, a long-press starts text selection. Both layers
 * coexist without fighting because `Modifier.clickable` consumes only
 * tap gestures, not long-press.
 */
@Composable
private fun PdfHyperlinkOverlay(
    hyperlinks: List<PdfHyperlink>,
    pageSize: PageSize,
    onUrlClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val widthPoints = pageSize.widthPoints.takeIf { it > 0f } ?: return
    val heightPoints = pageSize.heightPoints.takeIf { it > 0f } ?: return

    BoxWithConstraints(modifier = modifier) {
        val scaleX = maxWidth.value / widthPoints
        val scaleY = maxHeight.value / heightPoints
        hyperlinks.forEach { link ->
            Box(
                modifier = Modifier
                    .offset(
                        x = (link.xPoints * scaleX).dp,
                        y = (link.yPoints * scaleY).dp,
                    )
                    .size(
                        width = (link.widthPoints * scaleX).dp,
                        height = (link.heightPoints * scaleY).dp,
                    )
                    .clickable { onUrlClicked(link.url) },
            )
        }
    }
}

/**
 * Renders an invisible, selectable text layer over the rasterised page.
 *
 * Each [PdfTextRun] becomes a [BasicText] sized and positioned to mimic
 * the visible glyph footprint. The text colour is [Color.Transparent]
 * so the bitmap shows through; Compose's [SelectionContainer] picks up
 * long-press / drag gestures and surfaces the system copy menu — same
 * UX as Apple Books or Samsung Notes.
 *
 * The overlay matches the page's aspect-ratio-locked Box exactly, so
 * we map PDF points → fractions of the page's intrinsic dimensions and
 * multiply by the box's runtime width / height. The mapping scales
 * automatically through pinch zoom — when the box widens, the
 * fractions stay constant and positions follow.
 *
 * **Why the [TextMeasurer] dance**: Compose's selection highlight
 * tracks the laid-out text, not whatever modifier we throw on top.
 * If Compose's default font produces a narrower glyph run than what
 * PdfKmp recorded, the highlight stops mid-word and the user sees a
 * jagged tail even though the underlying text is selectable
 * end-to-end. We measure the natural Compose width once per run,
 * then dial in [TextStyle.letterSpacing] so the laid-out width
 * matches the recorded bbox to the pixel — closing the visible gap
 * without distorting the (invisible) glyphs vertically. Combined
 * with [LineHeightStyle.Trim.Both] this also strips the default
 * font-padding above and below, so the highlight rectangle hugs the
 * recorded glyph footprint instead of the typographic line slot.
 */
@Composable
private fun PdfTextSelectionOverlay(
    textRuns: List<PdfTextRun>,
    pageSize: PageSize,
    modifier: Modifier = Modifier,
) {
    val widthPoints = pageSize.widthPoints.takeIf { it > 0f } ?: return
    val heightPoints = pageSize.heightPoints.takeIf { it > 0f } ?: return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // Compose's natural width scales linearly with fontSize for a
    // fixed string, so we measure each run ONCE at its base PDF
    // fontSize and rescale in the per-frame loop. Without this cache
    // the measurer ran per-run-per-frame during pinch (≈3000
    // measure() calls per second on a typical page) and made the
    // gesture feel sluggish.
    val baseNaturalWidthsPx = remember(textRuns) {
        textRuns.map { run ->
            if (run.text.length <= 1) 0f else {
                val baseStyle = TextStyle(fontSize = run.fontSizePoints.sp)
                measurer.measure(
                    text = run.text,
                    style = baseStyle,
                    softWrap = false,
                ).size.width.toFloat()
            }
        }
    }

    SelectionContainer(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val boxWidth = maxWidth
            val boxHeight = maxHeight
            val scaleX = boxWidth.value / widthPoints
            val scaleY = boxHeight.value / heightPoints

            textRuns.forEachIndexed { idx, run ->
                val xDp = (run.xPoints * scaleX).dp
                val yDp = (run.yPoints * scaleY).dp
                val widthPx = run.widthPoints * scaleX * density.density
                val fontSizeSp = with(density) {
                    (run.fontSizePoints * scaleY).dp.toSp()
                }

                // Tight, padding-free style — line height collapses to
                // the glyph footprint rather than the typographic slot.
                val style = TextStyle(
                    color = Color.Transparent,
                    fontSize = fontSizeSp,
                    lineHeight = fontSizeSp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                )

                // Scale the cached base width by the current zoom
                // factor — fontSize and Compose's natural advance
                // both scale linearly, so this is a multiplication
                // instead of a fresh measurement.
                val naturalWidthPx = baseNaturalWidthsPx[idx] * scaleY
                val letterSpacingSp = if (run.text.length > 1 && naturalWidthPx > 0f) {
                    val extraPx = (widthPx - naturalWidthPx) / (run.text.length - 1)
                    with(density) { extraPx.toDp().toSp() }
                } else {
                    0.sp
                }

                BasicText(
                    text = run.text,
                    style = style.copy(letterSpacing = letterSpacingSp),
                    softWrap = false,
                    modifier = Modifier.offset(x = xDp, y = yDp),
                )
            }
        }
    }
}

/** Idle window after a scroll stops before the page indicator fades. */
private const val INDICATOR_IDLE_DELAY_MS: Long = 900L

@Composable
private fun PdfPageIndicator(
    listState: LazyListState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val currentPage by remember(listState, pageCount) {
        derivedStateOf {
            // Pick the page whose midpoint is closest to the viewport
            // centre — that's the one occupying the most screen real
            // estate, so the indicator flips to "2/2" the moment page
            // 2 crosses the half-way mark rather than waiting for
            // page 1 to fully scroll off (which is what
            // `firstVisibleItemIndex` would give us).
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf 1
            val viewportMid =
                (info.viewportStartOffset + info.viewportEndOffset) / 2
            val current = visible.minByOrNull { item ->
                val mid = item.offset + item.size / 2
                kotlin.math.abs(mid - viewportMid)
            } ?: visible.first()
            (current.index + 1).coerceAtMost(pageCount)
        }
    }

    // Show the chip while the user is scrolling (or when the page index
    // changes via zoom-induced auto-scroll), then fade it out after a
    // short idle window. Initial state stays visible for the same
    // window so the user can read the page count on first open.
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(listState, currentPage) {
        visible = true
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    visible = true
                } else {
                    delay(INDICATOR_IDLE_DELAY_MS)
                    visible = false
                }
            }
    }

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // Spec: rgba(20,20,22,0.78) bg, white text 13sp semibold
        // tabular-nums, padding 8x14, 0/4/16 + 0/1/2 shadow.
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xC7141416),
            contentColor = Color.White,
            shadowElevation = 4.dp,
        ) {
            // AnnotatedString: slash at 0.5 alpha, total at 0.6 alpha —
            // visually subordinates the divider/total so the current
            // page reads as the dominant glyph.
            val display = remember(currentPage, pageCount) {
                buildAnnotatedString {
                    append("$currentPage")
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f))) {
                        append(" / ")
                    }
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
                        append("$pageCount")
                    }
                }
            }
            Text(
                text = display,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    // tabular numerals so the indicator doesn't shift
                    // width when the page count crosses a digit
                    // boundary (e.g. 9 → 10, 99 → 100).
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

@Composable
private fun PdfViewerErrorState(modifier: Modifier = Modifier) {
    Text(
        text = "Could not display this PDF.",
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
    )
}

/**
 * Visits every page in `[start, end]` starting from [visible] and
 * walking outward — the page the user is currently looking at goes
 * first, then its neighbours in widening rings. Sequential ordering
 * matters because the render mutex serialises calls; visible first
 * means a fresh scroll target lands on screen quickly even when an
 * earlier prefetch is mid-flight.
 */
internal fun buildPrefetchOrder(visible: Int, start: Int, end: Int): List<Int> {
    if (end < start) return emptyList()
    val anchor = visible.coerceIn(start, end)
    val order = ArrayList<Int>(end - start + 1)
    order += anchor
    var offset = 1
    while (true) {
        val below = anchor + offset
        val above = anchor - offset
        val any = below <= end || above >= start
        if (!any) break
        if (below <= end) order += below
        if (above >= start) order += above
        offset += 1
    }
    return order
}
