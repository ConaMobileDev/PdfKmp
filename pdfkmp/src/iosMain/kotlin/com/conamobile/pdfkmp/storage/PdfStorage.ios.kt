package com.conamobile.pdfkmp.storage

import com.conamobile.pdfkmp.PdfDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation of [PdfStorage].
 *
 * Every variant of [StorageLocation] maps to a directory inside the app
 * sandbox — iOS apps cannot write outside it. `Downloads` therefore lands
 * in the Documents directory; surface the file via a share sheet
 * (`UIActivityViewController`) or enable file sharing in the app's
 * `Info.plist` to expose it through the Files app.
 *
 * The actual byte writing is delegated to [PdfDocument.save] which uses
 * `kotlinx.io` for a portable, well-tested I/O path that doesn't depend
 * on Foundation's deprecated `NSData.writeToFile:atomically:`.
 */
internal actual object PdfStorage {

    actual fun save(document: PdfDocument, location: StorageLocation, filename: String): SavedPdf {
        val targetPath = when (location) {
            StorageLocation.Cache -> joinPath(searchPath(NSCachesDirectory), filename)
            StorageLocation.Temp -> joinPath(NSTemporaryDirectory(), filename)
            StorageLocation.AppFiles -> joinPath(searchPath(NSApplicationSupportDirectory), filename)
            StorageLocation.AppExternalFiles -> joinPath(searchPath(NSDocumentDirectory), filename)
            StorageLocation.Downloads -> joinPath(searchPath(NSDocumentDirectory), filename)
            StorageLocation.Documents -> joinPath(searchPath(NSDocumentDirectory), filename)
            is StorageLocation.Custom ->
                if (filename.isEmpty()) location.path
                else joinPath(location.path, filename)
        }

        ensureParentExists(targetPath)
        document.save(targetPath)
        return SavedPdf(path = targetPath, uri = "file://$targetPath")
    }

    /** Returns the first absolute path for [directory] inside the user domain. */
    private fun searchPath(directory: NSSearchPathDirectory): String {
        val paths = NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true)
        return paths.firstOrNull() as? String
            ?: throw IllegalStateException("No directory found for $directory")
    }

    /** Joins [base] and [name] with a single `/`, regardless of trailing slash. */
    private fun joinPath(base: String, name: String): String {
        val sanitized = base.trimEnd('/')
        return "$sanitized/$name"
    }

    /**
     * Creates every parent directory of [filePath] if missing — the iOS
     * equivalent of Java's `File#mkdirs()`.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun ensureParentExists(filePath: String) {
        val lastSlash = filePath.lastIndexOf('/')
        if (lastSlash <= 0) return
        val dir = filePath.substring(0, lastSlash)
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(dir)) {
            fm.createDirectoryAtPath(
                path = dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }
}
