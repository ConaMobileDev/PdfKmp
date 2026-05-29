package com.conamobile.pdfkmp.viewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM / Desktop loader for the async [PdfSource] variants.
 *
 * - [PdfSource.FilePath] → [File.readBytes] (a leading `file://` is stripped).
 * - [PdfSource.Remote] → [HttpURLConnection], honouring per-request headers
 *   and the connect/read timeout (unlike the iOS `NSData` loader).
 * - [PdfSource.Asset] → a classpath resource, so bundling a PDF under
 *   `src/jvmMain/resources/` and opening `PdfSource.Asset("manuals/x.pdf")`
 *   works the same way assets do on Android.
 * - [PdfSource.ContentUri] → throws; `content://` is an Android-only scheme.
 */
internal actual suspend fun loadAsyncBytes(source: PdfSource): ByteArray = withContext(Dispatchers.IO) {
    when (source) {
        is PdfSource.FilePath -> File(source.path.removePrefix("file://")).readBytes()

        is PdfSource.Remote -> {
            val connection = URI(source.url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = source.timeoutMillis.toInt()
                connection.readTimeout = source.timeoutMillis.toInt()
                source.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }

        is PdfSource.Asset -> {
            val classLoader = Thread.currentThread().contextClassLoader
                ?: PdfSource::class.java.classLoader
            val stream = classLoader?.getResourceAsStream(source.path)
                ?: error("Asset not found on the classpath: ${source.path}")
            stream.use { it.readBytes() }
        }

        is PdfSource.ContentUri ->
            error("content:// URIs are Android-only and cannot be resolved on Desktop")

        is PdfSource.Bytes, is PdfSource.Document ->
            error("In-memory PdfSource should be read via inMemoryBytesOrNull(), not loadAsyncBytes()")
    }
}
