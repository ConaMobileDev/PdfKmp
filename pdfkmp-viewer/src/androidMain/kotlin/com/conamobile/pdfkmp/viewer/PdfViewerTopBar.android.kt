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
    subtitle: String?,
    backLabel: String?,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    showBack: Boolean,
    showSearch: Boolean,
    showShare: Boolean,
    showDownload: Boolean,
) {
    PdfViewerTopBarMinimalMono(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        onSearch = onSearch,
        onShare = onShare,
        onDownload = onDownload,
        showBack = showBack,
        showSearch = showSearch,
        showShare = showShare,
        showDownload = showDownload,
    )
}
