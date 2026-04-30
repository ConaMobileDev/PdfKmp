package com.conamobile.pdfkmp.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.platform.androidApplicationContext
import java.io.File

/**
 * Android implementation of [PdfStorage].
 *
 * Public locations ([StorageLocation.Downloads],
 * [StorageLocation.Documents]) use Android's MediaStore on API 29+ so the
 * caller does not need any storage permission. On API 26-28 the library
 * falls back to writing into `Environment.DIRECTORY_*` directly, which
 * requires the app to have the `WRITE_EXTERNAL_STORAGE` runtime
 * permission. The library does not request permissions itself — the app
 * is responsible for that.
 */
internal actual object PdfStorage {

    actual fun save(document: PdfDocument, location: StorageLocation, filename: String): SavedPdf {
        val context = androidApplicationContext()
        val bytes = document.toByteArray()
        return when (location) {
            StorageLocation.Cache -> writeToFile(File(context.cacheDir, filename), bytes)
            StorageLocation.Temp -> writeToFile(File(context.cacheDir, filename), bytes)
            StorageLocation.AppFiles -> writeToFile(File(context.filesDir, filename), bytes)
            StorageLocation.AppExternalFiles -> {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                writeToFile(File(dir, filename), bytes)
            }
            StorageLocation.Downloads -> writeToPublicLocation(
                context = context,
                bytes = bytes,
                filename = filename,
                relativeDir = Environment.DIRECTORY_DOWNLOADS,
                publicCollection = downloadsCollection(),
            )
            StorageLocation.Documents -> writeToPublicLocation(
                context = context,
                bytes = bytes,
                filename = filename,
                relativeDir = Environment.DIRECTORY_DOCUMENTS,
                publicCollection = documentsCollection(),
            )
            is StorageLocation.Custom -> {
                val target = if (filename.isEmpty()) File(location.path)
                else File(location.path, filename)
                writeToFile(target, bytes)
            }
        }
    }

    /**
     * Writes [bytes] to [file], creating any missing parent directories.
     * Returned [SavedPdf] has no content URI — the file is reachable
     * through `FileProvider` if the caller wants to share it.
     */
    private fun writeToFile(file: File, bytes: ByteArray): SavedPdf {
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        file.writeBytes(bytes)
        return SavedPdf(path = file.absolutePath, uri = null)
    }

    /**
     * Writes [bytes] to a public collection. On API 29+ goes through
     * MediaStore (no permission). Below that, falls back to the
     * traditional public directory under
     * `Environment.getExternalStoragePublicDirectory(...)` — this
     * requires the app to declare and request
     * `WRITE_EXTERNAL_STORAGE`.
     */
    private fun writeToPublicLocation(
        context: Context,
        bytes: ByteArray,
        filename: String,
        relativeDir: String,
        publicCollection: Uri,
    ): SavedPdf {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(publicCollection, values)
                ?: error("Failed to create MediaStore entry for $filename")
            resolver.openOutputStream(uri).use { stream ->
                requireNotNull(stream) { "MediaStore returned a null stream for $uri" }
                stream.write(bytes)
            }
            return SavedPdf(path = filename, uri = uri.toString())
        }

        @Suppress("DEPRECATION")
        val publicDir = Environment.getExternalStoragePublicDirectory(relativeDir)
        if (!publicDir.exists()) publicDir.mkdirs()
        val file = File(publicDir, filename)
        file.writeBytes(bytes)
        return SavedPdf(path = file.absolutePath, uri = null)
    }

    private fun downloadsCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Files.getContentUri("external")
    }

    private fun documentsCollection(): Uri = MediaStore.Files.getContentUri("external")
}
