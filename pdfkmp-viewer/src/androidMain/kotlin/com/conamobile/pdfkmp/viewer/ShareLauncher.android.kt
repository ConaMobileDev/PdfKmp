package com.conamobile.pdfkmp.viewer

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider authority used by the viewer's share sheet. Composed
 * from the consumer app's `applicationId` (substituted at build time
 * via the `${applicationId}` manifest placeholder), so two apps that
 * both depend on `pdfkmp-viewer` cannot collide.
 */
private const val FILE_PROVIDER_SUFFIX = ".pdfkmp.viewer.fileprovider"

/**
 * Subdirectory inside `cacheDir` reserved for the share sheet's
 * scratch files. Mirrors the path mapped in
 * `res/xml/pdfkmp_viewer_file_paths.xml` so `FileProvider` can hand
 * out a `content://` URI for it.
 */
private const val SHARE_CACHE_DIR = "pdfkmp-viewer-share"

/**
 * Captures the [Context] once and returns a [PdfShareAction] that
 * writes the bytes to `cacheDir/pdfkmp-viewer-share/<filename>` and
 * launches a `Intent.ACTION_SEND` chooser through `FileProvider`.
 *
 * The temp file is written every call (it would be wrong to keep a
 * stale copy around — the user might be sharing a freshly-regenerated
 * document with the same name) and is overwritten in place. Android's
 * cache eviction policy reclaims the bytes when the device runs low on
 * space; the library does not delete it eagerly because the receiving
 * app needs the file to remain readable for the duration of the share
 * (which can include a chooser dismissal, a multi-second upload, etc.).
 */
@Composable
internal actual fun rememberPdfShareAction(): PdfShareAction {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidShareAction(context) }
}

private class AndroidShareAction(private val context: Context) : PdfShareAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val shareDir = File(context.cacheDir, SHARE_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
        val target = File(shareDir, fileName.takeIf { it.isNotBlank() } ?: "document.pdf")
        target.writeBytes(bytes)

        val authority = "${context.packageName}$FILE_PROVIDER_SUFFIX"
        val uri = FileProvider.getUriForFile(context, authority, target)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, null).apply {
            // Required when launching from a non-Activity context.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
