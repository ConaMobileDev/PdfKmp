package com.conamobile.pdfkmp

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write

/**
 * A fully-rendered PDF document, ready to be persisted or transferred.
 *
 * Instances are immutable and safe to share across threads. The encoded bytes
 * are produced once during [pdf] and reused for every accessor call —
 * [toByteArray] returns a defensive copy so the caller cannot mutate the
 * library-internal buffer.
 *
 * `kotlinx-io` is used for file I/O so [save] works the same way on Android,
 * iOS, and any future Kotlin/JVM, Kotlin/Native, or Kotlin/JS target without
 * platform-specific code.
 */
public class PdfDocument internal constructor(private val bytes: ByteArray) {

    /** Total size of the encoded document in bytes. */
    public val size: Int get() = bytes.size

    /**
     * Returns a fresh copy of the encoded PDF bytes.
     *
     * The library never hands out its internal buffer; mutating the returned
     * array does not affect future calls.
     */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    /**
     * Writes the encoded PDF to the file at [path], creating any missing
     * parent directories.
     *
     * @param path absolute or working-directory-relative file system path.
     */
    public fun save(path: String) {
        val target = Path(path)
        target.parent?.let { parent ->
            if (!SystemFileSystem.exists(parent)) {
                SystemFileSystem.createDirectories(parent)
            }
        }
        SystemFileSystem.sink(target).buffered().use { sink ->
            sink.write(bytes)
        }
    }
}
