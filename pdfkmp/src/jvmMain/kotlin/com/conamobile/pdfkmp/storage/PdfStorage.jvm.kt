package com.conamobile.pdfkmp.storage

import com.conamobile.pdfkmp.PdfDocument
import java.io.File

/**
 * JVM / Desktop implementation of [PdfStorage].
 *
 * Desktop platforms (macOS, Windows, Linux) expose the full user filesystem,
 * so every [StorageLocation] maps to a conventional directory under the
 * current user's home:
 *
 * - [StorageLocation.Cache] / [StorageLocation.Temp] → the JVM temp directory
 *   (`java.io.tmpdir`).
 * - [StorageLocation.AppFiles] / [StorageLocation.AppExternalFiles] → a
 *   private `~/.pdfkmp` directory.
 * - [StorageLocation.Downloads] → `~/Downloads`.
 * - [StorageLocation.Documents] → `~/Documents`.
 * - [StorageLocation.Custom] → the caller-supplied path verbatim.
 *
 * The actual byte writing is delegated to [PdfDocument.save] which uses
 * `kotlinx.io` for a portable I/O path shared with the iOS backend.
 */
internal actual object PdfStorage {

    actual fun save(document: PdfDocument, location: StorageLocation, filename: String): SavedPdf {
        val target = when (location) {
            StorageLocation.Cache -> File(tempDir(), filename)
            StorageLocation.Temp -> File(tempDir(), filename)
            StorageLocation.AppFiles -> File(appDir(), filename)
            StorageLocation.AppExternalFiles -> File(appDir(), filename)
            StorageLocation.Downloads -> File(userDir("Downloads"), filename)
            StorageLocation.Documents -> File(userDir("Documents"), filename)
            is StorageLocation.Custom ->
                if (filename.isEmpty()) File(location.path) else File(location.path, filename)
        }
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        document.save(target.absolutePath)
        return SavedPdf(path = target.absolutePath, uri = target.toURI().toString())
    }

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir") ?: ".")

    /** A private, persistent per-app directory under the user's home. */
    private fun appDir(): File =
        File(homeDir(), ".pdfkmp")

    /** A conventional user-facing folder (Downloads, Documents) under home. */
    private fun userDir(name: String): File =
        File(homeDir(), name)

    private fun homeDir(): File =
        File(System.getProperty("user.home") ?: ".")
}
