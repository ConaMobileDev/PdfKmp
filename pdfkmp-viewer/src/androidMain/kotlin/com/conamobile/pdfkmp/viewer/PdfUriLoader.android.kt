package com.conamobile.pdfkmp.viewer

import android.net.Uri
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android resolver for the async [PdfSource] variants.
 *
 * - [PdfSource.FilePath]   → direct [File] read
 * - [PdfSource.Remote]     → [HttpURLConnection] honouring per-request
 *   headers + connect / read timeouts (the platform's `URL.openStream`
 *   doesn't expose either)
 * - [PdfSource.ContentUri] → [android.content.ContentResolver]
 * - [PdfSource.Asset]      → [android.content.Context.getAssets]
 *
 * All I/O happens on [Dispatchers.IO]. The viewer wraps the call in
 * its own `try` so platform failures (network unreachable, file
 * missing, etc.) surface as an inline error UI instead of crashing.
 */
internal actual suspend fun loadAsyncBytes(source: PdfSource): ByteArray =
    withContext(Dispatchers.IO) {
        when (source) {
            is PdfSource.FilePath -> readFile(source.path)
            is PdfSource.Remote -> readRemote(source)
            is PdfSource.ContentUri -> readContentUri(source.uri)
            is PdfSource.Asset -> readAsset(source.path)
            is PdfSource.Bytes, is PdfSource.Document ->
                error("loadAsyncBytes() called on in-memory variant ${source::class.simpleName}")
        }
    }

private fun readFile(rawPath: String): ByteArray {
    val path = rawPath.removePrefix("file://")
    return File(path).readBytes()
}

private fun readContentUri(uri: String): ByteArray {
    val context = ViewerContextHolder.get()
    val parsed = Uri.parse(uri)
    return context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        ?: error("ContentResolver.openInputStream returned null for $uri")
}

private fun readAsset(path: String): ByteArray {
    val context = ViewerContextHolder.get()
    return context.assets.open(path).use { it.readBytes() }
}

private fun readRemote(source: PdfSource.Remote): ByteArray {
    val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
        connectTimeout = source.timeoutMillis.toIntSafely()
        readTimeout = source.timeoutMillis.toIntSafely()
        for ((name, value) in source.headers) {
            setRequestProperty(name, value)
        }
    }
    return try {
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

private fun Long.toIntSafely(): Int =
    if (this > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else toInt()
