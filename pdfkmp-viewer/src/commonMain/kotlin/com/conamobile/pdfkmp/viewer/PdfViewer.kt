package com.conamobile.pdfkmp.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.PdfDocument
import kotlinx.coroutines.launch

/**
 * Default page background — neutral light grey that matches the chrome
 * of every native viewer (PDFKit, Adobe Reader, Google Drive) and stays
 * out of the document's way.
 */
internal val DefaultViewerBackground: Color = Color(0xFFE0E0E0)

/** Hard-coded ceiling for pinch zoom. 5× is the iOS PDFKit default. */
private const val DEFAULT_MAX_ZOOM: Float = 5f

/**
 * Compose Multiplatform viewer for PDFs produced by `:pdfkmp` (or any
 * other source that hands you raw `%PDF-…` bytes).
 *
 * Pages stream through the host platform's native PDF renderer
 * (Android `PdfRenderer`, iOS `PDFKit.PDFDocument`) one at a time,
 * driven by a [LazyColumn] — even hundred-page documents only keep
 * the visible bitmaps in memory. The encoded PDF retains its vector
 * geometry, so what users see on screen and what reaches the share
 * sheet are identical.
 *
 * Built-in UI:
 *
 * - **Pinch-to-zoom** per page (`Modifier.transformable`). Two-finger
 *   pan moves within a zoomed page. Single-finger drag always scrolls
 *   the list, so the gesture model never fights the parent
 *   [LazyColumn].
 * - **Page indicator** (`n / total`) anchored bottom-centre. Hidden
 *   automatically for single-page documents.
 * - **Share** [FloatingActionButton] anchored bottom-end. Toggle off
 *   via [showShareButton] when the host screen already has its own
 *   share affordance.
 * - **Loading + error states** rendered in the central area while the
 *   renderer opens / when the bytes can't be parsed.
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
 *   default mirrors what native PDF viewers use.
 * @param pageBackgroundColor colour painted behind each individual page
 *   (visible while the page is still rasterising or through transparent
 *   margins inside the PDF itself).
 * @param contentPadding padding applied around the [LazyColumn] content.
 * @param pageSpacing vertical gap between page previews.
 * @param renderDensity multiplicative scaling factor applied during
 *   rasterisation. `2f` is sharp on retina displays without ballooning
 *   memory; bump to `3f` for very large surfaces.
 * @param maxZoom upper bound for the pinch gesture. Defaults to `5f`
 *   to match iOS PDFKit.
 * @param showPageIndicator toggles the bottom-centre `n / total`
 *   chip. `true` by default; the chip is suppressed automatically for
 *   single-page documents regardless.
 * @param shareButtonAlignment positions the share FAB inside the
 *   outer [Box]. Defaults to [Alignment.BottomEnd] to match Material 3
 *   guidance.
 * @param shareButtonPadding padding between the share FAB and the
 *   nearest [Box] edge.
 */
@Composable
public fun PdfViewer(
    source: PdfSource,
    modifier: Modifier = Modifier,
    showShareButton: Boolean = true,
    shareFileName: String = "document.pdf",
    backgroundColor: Color = DefaultViewerBackground,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    pageSpacing: Dp = 16.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = DEFAULT_MAX_ZOOM,
    showPageIndicator: Boolean = true,
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
) {
    val bytes = remember(source) { source.bytes() }
    var renderer by remember(bytes) { mutableStateOf<PdfPageRenderer?>(null) }
    var loading by remember(bytes) { mutableStateOf(true) }
    var error by remember(bytes) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    DisposableEffect(bytes) {
        var openedRenderer: PdfPageRenderer? = null
        val job = scope.launch {
            val opened = openPdfRenderer(bytes)
            openedRenderer = opened
            renderer = opened
            error = opened == null || opened.pageCount == 0
            loading = false
        }
        onDispose {
            job.cancel()
            openedRenderer?.close()
            renderer = null
        }
    }

    val listState = rememberLazyListState()
    val shareAction = if (showShareButton) rememberPdfShareAction() else null
    val current = renderer

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            error || current == null -> PdfViewerErrorState(
                modifier = Modifier.align(Alignment.Center),
            )

            else -> {
                PdfPagesList(
                    renderer = current,
                    listState = listState,
                    pageBackgroundColor = pageBackgroundColor,
                    contentPadding = contentPadding,
                    pageSpacing = pageSpacing,
                    renderDensity = renderDensity,
                    maxZoom = maxZoom,
                )
                if (showPageIndicator && current.pageCount > 1) {
                    PdfPageIndicator(
                        listState = listState,
                        pageCount = current.pageCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }

        if (shareAction != null && current != null && !error) {
            FloatingActionButton(
                onClick = { shareAction(bytes, shareFileName) },
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
    backgroundColor: Color = DefaultViewerBackground,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    pageSpacing: Dp = 16.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = DEFAULT_MAX_ZOOM,
    showPageIndicator: Boolean = true,
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
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
        showPageIndicator = showPageIndicator,
        shareButtonAlignment = shareButtonAlignment,
        shareButtonPadding = shareButtonPadding,
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
    backgroundColor: Color = DefaultViewerBackground,
    pageBackgroundColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    pageSpacing: Dp = 16.dp,
    renderDensity: Float = 2f,
    maxZoom: Float = DEFAULT_MAX_ZOOM,
    showPageIndicator: Boolean = true,
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
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
        showPageIndicator = showPageIndicator,
        shareButtonAlignment = shareButtonAlignment,
        shareButtonPadding = shareButtonPadding,
    )
}

@Composable
private fun PdfPagesList(
    renderer: PdfPageRenderer,
    listState: LazyListState,
    pageBackgroundColor: Color,
    contentPadding: PaddingValues,
    pageSpacing: Dp,
    renderDensity: Float,
    maxZoom: Float,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(pageSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(
            count = renderer.pageCount,
            key = { it },
        ) { index ->
            PdfPageItem(
                renderer = renderer,
                index = index,
                pageSize = renderer.pageSizes.getOrNull(index)
                    ?: PageSize(widthPoints = 1f, heightPoints = 1f),
                pageBackgroundColor = pageBackgroundColor,
                renderDensity = renderDensity,
                maxZoom = maxZoom,
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    renderer: PdfPageRenderer,
    index: Int,
    pageSize: PageSize,
    pageBackgroundColor: Color,
    renderDensity: Float,
    maxZoom: Float,
) {
    var bitmap by remember(renderer, index, renderDensity) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var scale by remember(renderer, index) { mutableFloatStateOf(1f) }
    var offsetX by remember(renderer, index) { mutableFloatStateOf(0f) }
    var offsetY by remember(renderer, index) { mutableFloatStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(renderer, index, renderDensity) {
        bitmap = renderer.renderPage(index, renderDensity)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, maxZoom)
        scale = newScale
        if (newScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    // panZoomLock = true keeps single-finger drags free
                    // for the parent LazyColumn to scroll. Pinch + two
                    // finger pan engage the transform; everything else
                    // bubbles up.
                    .transformable(state = transformState),
            )
        }
    }
}

@Composable
private fun PdfPageIndicator(
    listState: LazyListState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val currentPage by remember(listState, pageCount) {
        derivedStateOf {
            // Pages are 1-based for human readers. Clamp to pageCount
            // because LazyColumn can briefly report an index past the
            // end during a fling.
            (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.65f),
        contentColor = Color.White,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = "$currentPage / $pageCount",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
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
