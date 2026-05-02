package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * iOS default — delegates to [PdfViewerTopBarClassicIos].
 *
 * `subtitle` is intentionally accepted but ignored: the Classic iOS
 * topbar is 52dp tall with no room for a second line, matching
 * Mail / Files / Notes conventions.
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
    PdfViewerTopBarClassicIos(
        title = title,
        modifier = modifier,
        backLabel = backLabel,
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
