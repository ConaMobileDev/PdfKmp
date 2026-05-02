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
        val nsUrl = NSURL(string = url)
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
