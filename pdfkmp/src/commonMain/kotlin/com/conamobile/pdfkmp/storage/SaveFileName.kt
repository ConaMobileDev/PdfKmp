package com.conamobile.pdfkmp.storage

/**
 * Validates a caller-supplied save filename before any platform backend
 * composes a filesystem path or a `MediaStore` display name from it.
 *
 * A filename is a leaf name only: it must not navigate directories
 * (`..`, separators), address devices or NTFS alternate data streams
 * (control bytes, `:`, Windows reserved names like `CON` or `COM1`),
 * use characters the Win32 layer rejects (`* ? " < > |`), or carry
 * trailing characters Windows silently strips (space, `.`). These
 * checks are platform-independent because the same filename crosses
 * OS boundaries via share sheets and cloud sync.
 *
 * @throws IllegalArgumentException when [filename] is not a safe leaf name.
 */
internal fun validateSaveFileName(filename: String) {
    require(filename.isNotEmpty()) {
        "Save filename must not be empty; pass an explicit name ending in .pdf"
    }
    require(filename.none { isForbiddenFileNameChar(it) }) {
        "Save filename must be a leaf name without path separators, control characters, " +
            "or the Windows-reserved characters : * ? \" < > | — got: $filename"
    }
    require(filename != "." && filename != "..") {
        "Save filename must not be a directory reference: $filename"
    }
    require(!filename.endsWith(' ') && !filename.endsWith('.')) {
        "Save filename must not end with a space or a dot: $filename"
    }
    val stem = filename.substringBefore('.').uppercase()
    require(stem !in WINDOWS_RESERVED_STEMS) {
        "Save filename uses a Windows reserved device name: $filename"
    }
}

// '/', '\' navigate directories everywhere; ':' addresses an NTFS alternate
// data stream (the payload silently vanishes into an invisible stream);
// * ? " < > | are rejected by the Win32 layer; control chars (incl. NUL)
// are never legitimate in a display name and break MediaStore/NSString.
private fun isForbiddenFileNameChar(c: Char): Boolean =
    c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' ||
        c == '"' || c == '<' || c == '>' || c == '|' ||
        c.code < 0x20 || c.code == 0x7F

private val WINDOWS_RESERVED_STEMS: Set<String> =
    setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } +
        (1..9).map { "LPT$it" }
