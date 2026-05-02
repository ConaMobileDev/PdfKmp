package com.conamobile.pdfkmp.viewer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Captures the [Context] once and returns a [PdfSaveAction] that
 * persists the bytes to the user-visible Downloads directory.
 *
 * Two code paths:
 *
 * - **API 29+ (Android 10 / Q+)** — uses the Scoped-Storage-friendly
 *   `MediaStore.Downloads` content URI. No runtime permission
 *   required; the file is owned by the host app and visible to every
 *   reader (Files, Drive, Gmail attach, etc.).
 * - **API < 29** — falls back to writing into
 *   `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`.
 *   On API 28 and below this needs `WRITE_EXTERNAL_STORAGE`. The
 *   library does not request the permission itself; consuming apps
 *   targeting API < 29 must declare it in their manifest. On
 *   permission denial the action surfaces a Toast and bails.
 *
 * Existing files with the same name are overwritten. A short Toast
 * confirms success; failures are logged but never rethrown — a
 * viewer's save action shouldn't be able to crash the host app.
 */
@Composable
public actual fun rememberPdfSaveAction(): PdfSaveAction {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidSaveAction(context) }
}

private class AndroidSaveAction(private val context: Context) : PdfSaveAction {

    override fun invoke(bytes: ByteArray, fileName: String) {
        val safeName = fileName.takeIf { it.isNotBlank() } ?: "document.pdf"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(safeName, bytes)
            } else {
                writeViaLegacyPath(safeName, bytes)
            }
            toast("Saved to Downloads")
        } catch (e: Exception) {
            Log.w("PdfKmpViewer", "Failed to save PDF $safeName", e)
            toast("Couldn't save PDF")
        }
    }

    private fun writeViaMediaStore(name: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // Overwrite existing entries with the same display name so the
        // user sees a fresh copy after re-saving the same document.
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, arrayOf(name), null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val uri = MediaStore.Downloads.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY,
                        id,
                    )
                    resolver.delete(uri, null, null)
                }
            }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore.insert returned null for $name")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Could not open output stream for $uri")
    }

    @Suppress("DEPRECATION")
    private fun writeViaLegacyPath(name: String, bytes: ByteArray) {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        if (!downloads.exists()) downloads.mkdirs()
        File(downloads, name).writeBytes(bytes)
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
