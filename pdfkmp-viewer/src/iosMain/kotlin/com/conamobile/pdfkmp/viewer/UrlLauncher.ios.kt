@file:OptIn(ExperimentalForeignApi::class)

package com.conamobile.pdfkmp.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Returns a stateless [PdfUrlLauncher] backed by
 * [UIApplication.openURL]. The shared application is fetched lazily
 * inside the launcher so the composable function stays cheap to
 * remember — no per-recompose UIKit calls.
 *
 * `openURL:options:completionHandler:` returns synchronously without
 * blocking; failures (malformed URL, no app registered for the
 * scheme) are surfaced to the completion handler we deliberately
 * ignore — see the [PdfUrlLauncher] KDoc.
 */
@Composable
internal actual fun rememberPdfUrlLauncher(): PdfUrlLauncher =
    remember { IosUrlLauncher() }

private class IosUrlLauncher : PdfUrlLauncher {
    override fun invoke(url: String) {
        if (url.isBlank()) return
        // URLWithString returns null for RFC-invalid strings (e.g. an
        // embedded space) — the failable-initializer constructor form would
        // make Kotlin/Native throw NPE and crash the host on a link tap,
        // violating the launcher's silent fall-through contract.
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
