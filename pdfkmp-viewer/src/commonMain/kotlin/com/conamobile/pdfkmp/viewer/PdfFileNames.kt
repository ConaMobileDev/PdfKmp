package com.conamobile.pdfkmp.viewer

/**
 * Reduces a caller-supplied file name to a safe leaf name before any
 * launcher composes a filesystem path or a `MediaStore` display name
 * from it.
 *
 * Host apps routinely build these names from untrusted input (document
 * titles, server-supplied names), and the launchers' contract is to never
 * crash the host — so instead of rejecting like the core `save()`
 * validator does, directory components are stripped with one shared rule
 * on every platform, keeping a `../`-style name from escaping the
 * launcher's target directory.
 */
internal fun sanitizePdfFileName(raw: String): String {
    val leaf = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    return if (leaf.isEmpty() || leaf == "." || leaf == "..") "document.pdf" else leaf
}
