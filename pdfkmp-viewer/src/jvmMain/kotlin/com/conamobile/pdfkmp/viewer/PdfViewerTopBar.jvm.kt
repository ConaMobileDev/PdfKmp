package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Desktop default — delegates to [PdfViewerTopBarMinimalMono], the same
 * brand-neutral, Material-flavoured bar Android uses. It reads naturally with
 * a mouse and keyboard, where the iOS chevron-and-label convention would feel
 * out of place.
 *
 * `backLabel` is accepted but ignored, mirroring the Android backend.
 */
@Composable
public actual fun PdfViewerTopBar(
    title: String,
    modifier: Modifier,
    titleOverflow: PdfTopBarTitleOverflow,
    subtitle: String?,
    backLabel: String?,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onDownload: () -> Unit,
    onAnnotate: () -> Unit,
    showBack: Boolean,
    showSearch: Boolean,
    showShare: Boolean,
    showPrint: Boolean,
    showDownload: Boolean,
    showAnnotate: Boolean,
    annotateActive: Boolean,
) {
    PdfViewerTopBarMinimalMono(
        title = title,
        modifier = modifier,
        titleOverflow = titleOverflow,
        subtitle = subtitle,
        onBack = onBack,
        onSearch = onSearch,
        onShare = onShare,
        onPrint = onPrint,
        onDownload = onDownload,
        onAnnotate = onAnnotate,
        showBack = showBack,
        showSearch = showSearch,
        showShare = showShare,
        showPrint = showPrint,
        showDownload = showDownload,
        showAnnotate = showAnnotate,
        annotateActive = annotateActive,
    )
}
