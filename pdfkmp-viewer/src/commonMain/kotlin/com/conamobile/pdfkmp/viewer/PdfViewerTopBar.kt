package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-aware default topbar for [PdfViewer]. Picks the design
 * direction the host platform expects out of the box:
 *
 * - **Android** → [PdfViewerTopBarMinimalMono] — modern, brand-neutral,
 *   download as the visually dominant action.
 * - **iOS** → [PdfViewerTopBarClassicIos] — chevron + back label,
 *   centered title, three iOS-blue trailing icons.
 *
 * Both variants are public composables in their own right — call
 * them directly when you need to pin a specific look (e.g. a
 * cross-platform Material-style topbar on iOS as well).
 *
 * Each action button can be hidden via the matching `show…` flag
 * without removing the wired callback. The search affordance is
 * `false` by default since search is opt-in — wire [onSearch] *and*
 * set [showSearch] to `true` to surface it.
 *
 * `subtitle` only shows up in the Minimal Mono variant; the iOS
 * variant has no room for it. `backLabel` only shows up in the iOS
 * variant.
 *
 * @param title filename / document name.
 * @param modifier applied to the outer container.
 * @param subtitle UPPERCASE meta line for Minimal Mono — typically
 *   `"PDF · 2.4 MB"`.
 * @param backLabel iOS-only previous-screen label rendered next to
 *   the chevron (e.g. `"Files"`). Ignored on Android.
 * @param onBack callback when the back affordance is tapped.
 * @param onSearch callback when the search affordance is tapped.
 * @param onShare callback when the share affordance is tapped.
 * @param onDownload callback when the download affordance is tapped.
 * @param showBack visibility of the back affordance.
 * @param showSearch visibility of the search affordance.
 * @param showShare visibility of the share affordance.
 * @param showDownload visibility of the download affordance.
 */
@Composable
public expect fun PdfViewerTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    onSearch: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    showBack: Boolean = true,
    showSearch: Boolean = false,
    showShare: Boolean = true,
    showDownload: Boolean = true,
)
