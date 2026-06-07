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
    PdfViewerTopBarClassicIos(
        title = title,
        modifier = modifier,
        titleOverflow = titleOverflow,
        backLabel = backLabel,
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
