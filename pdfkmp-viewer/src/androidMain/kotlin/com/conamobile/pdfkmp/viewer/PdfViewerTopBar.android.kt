package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android default — delegates to [PdfViewerTopBarMinimalMono].
 *
 * `backLabel` is intentionally accepted but ignored: Material's back
 * affordance is glyph-only, so emulating iOS's "Files →" prefix on
 * Android would clash with platform conventions.
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
