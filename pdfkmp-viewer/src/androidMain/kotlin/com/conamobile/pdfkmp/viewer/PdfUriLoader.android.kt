package com.conamobile.pdfkmp.viewer

import android.content.Context
import android.net.Uri
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Resolves a wide set of URI shapes into a `ByteArray`:
 *
 * - `content://...`   → [Context.contentResolver]
 * - `file://...`      → direct [File] read
 * - `http(s)://...`   → [URL.openStream]
 * - `asset:///path`   → [Context.assets]
 * - `/path/...`       → bare filesystem path
 *
 * Network and disk reads happen on [Dispatchers.IO].
 */
internal actual suspend fun loadPdfBytesFromUri(uri: String): ByteArray =
    withContext(Dispatchers.IO) {
        val context = ViewerContextHolder.get()
        when {
            uri.startsWith("content://") -> readContentUri(context, uri)
            uri.startsWith("asset:///") -> {
                val assetPath = uri.removePrefix("asset:///")
                context.assets.open(assetPath).use { it.readBytes() }
            }
            uri.startsWith("http://") || uri.startsWith("https://") -> {
                URL(uri).openStream().use { it.readBytes() }
            }
            uri.startsWith("file://") -> File(uri.removePrefix("file://")).readBytes()
            else -> File(uri).readBytes()
        }
    }

private fun readContentUri(context: Context, uri: String): ByteArray {
    val parsed = Uri.parse(uri)
    return context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        ?: error("ContentResolver.openInputStream returned null for $uri")
}
