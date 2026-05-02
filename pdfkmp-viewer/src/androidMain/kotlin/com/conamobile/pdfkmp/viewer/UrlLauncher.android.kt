package com.conamobile.pdfkmp.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Captures the [Context] once and returns a [PdfUrlLauncher] that
 * dispatches `Intent.ACTION_VIEW` for any URL the overlay hands it.
 *
 * Adds [Intent.FLAG_ACTIVITY_NEW_TASK] because the captured context
 * is the application context, not an Activity. Failures (no browser
 * registered, malformed Uri) are swallowed — the user tapping a
 * broken link should not crash the viewer.
 */
@Composable
internal actual fun rememberPdfUrlLauncher(): PdfUrlLauncher {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidUrlLauncher(context) }
}

private class AndroidUrlLauncher(private val context: Context) : PdfUrlLauncher {

    override fun invoke(url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("PdfKmpViewer", "Failed to open URL: $url", e)
        }
    }
}
