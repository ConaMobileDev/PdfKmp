package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.conamobile.pdfkmp.PdfUrls

/**
 * Action that opens an HTTP/HTTPS URL in the platform's default
 * browser. Returned by [rememberPdfUrlLauncher] and invoked from the
 * hyperlink overlay rendered on top of selected PDF pages — see
 * [PdfViewer]'s `hyperlinksEnabled` parameter.
 *
 * The action is platform-aware: on Android it dispatches an
 * `Intent.ACTION_VIEW`, on iOS it calls `UIApplication.openURL`. Both
 * fall through silently when the URL cannot be opened (malformed,
 * scheme not handled by any app), since a viewer mid-render is the
 * wrong place to surface a system-level error to the user.
 */
public fun interface PdfUrlLauncher {
    public operator fun invoke(url: String)
}

/**
 * Returns a remembered [PdfUrlLauncher] bound to the current platform.
 * Snapshots the [androidx.compose.ui.platform.LocalContext] on
 * Android; on iOS it returns a stateless launcher.
 */
@Composable
internal expect fun rememberPdfUrlLauncher(): PdfUrlLauncher

/**
 * Wraps the platform launcher with the [PdfUrls] scheme allowlist so a
 * URL recorded from document content can never dispatch a `javascript:`,
 * `file:`, `data:`, or other non-web scheme to the OS, regardless of
 * how the document's annotations were authored. Rejected URLs follow
 * the same silent fall-through contract as unhandled ones.
 */
@Composable
internal fun rememberSchemeFilteredPdfUrlLauncher(): PdfUrlLauncher {
    val platformLauncher = rememberPdfUrlLauncher()
    return remember(platformLauncher) {
        PdfUrlLauncher { url ->
            if (PdfUrls.isSafeExternalUrl(url)) platformLauncher(url)
        }
    }
}
