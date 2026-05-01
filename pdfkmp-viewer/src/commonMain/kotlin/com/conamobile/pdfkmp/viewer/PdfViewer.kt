package com.conamobile.pdfkmp.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conamobile.pdfkmp.PdfDocument

/**
 * Default page background — neutral light grey that matches the chrome
 * of every native viewer (PDFKit, Adobe Reader, Google Drive) and stays
 * out of the document's way.
 */
internal val DefaultViewerBackground: Color = Color(0xFFE0E0E0)

/**
 * Compose Multiplatform viewer for PDFs produced by `:pdfkmp` (or any
 * other source that hands you raw `%PDF-…` bytes).
 *
 * The composable rasterises every page through the host platform's
 * native PDF renderer (Android `PdfRenderer`, iOS `CGPDFDocument`) for
 * display only — the underlying document keeps its vector geometry, so
 * what users see on screen and what reaches the share sheet are
 * identical.
 *
 * Internally the layout is a [LazyColumn] of bitmap previews stacked on
 * top of a coloured [Box]. When [showShareButton] is `true`, a
 * Material 3 [FloatingActionButton] floats above the list at
 * [shareButtonAlignment] / [shareButtonPadding] and triggers the
 * platform share sheet through [PdfShareAction] — `Intent.ACTION_SEND`
 * via `FileProvider` on Android, `UIActivityViewController` on iOS.
 *
 * Callers who prefer a different button placement, label, or icon can
 * set `showShareButton = false` and overlay their own Composable on top
 * of the viewer.
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
 *   bitmap (visible through any transparent margins inside the PDF).
 * @param contentPadding padding applied around the [LazyColumn] content.
 * @param pageSpacing vertical gap between page previews.
 * @param renderDensity multiplicative scaling factor applied during
 *   rasterisation. `2f` is sharp on retina displays without ballooning
 *   memory; bump to `3f` for very large surfaces.
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
    shareButtonAlignment: Alignment = Alignment.BottomEnd,
    shareButtonPadding: PaddingValues = PaddingValues(16.dp),
) {
    val bytes = remember(source) { source.bytes() }
    var pages by remember(bytes, renderDensity) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var rendering by remember(bytes, renderDensity) { mutableStateOf(true) }

    LaunchedEffect(bytes, renderDensity) {
        rendering = true
        pages = renderPdfPages(bytes, renderDensity)
        rendering = false
    }

    val shareAction = if (showShareButton) rememberPdfShareAction() else null

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        when {
            rendering && pages.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(pageSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(pages) { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(pageBackgroundColor),
                    )
                }
            }
        }

        if (shareAction != null) {
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
        shareButtonAlignment = shareButtonAlignment,
        shareButtonPadding = shareButtonPadding,
    )
}
