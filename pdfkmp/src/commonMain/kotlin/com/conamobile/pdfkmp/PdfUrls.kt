package com.conamobile.pdfkmp

/**
 * Scheme allowlist for URLs the library hands to external actors: PDF
 * `/URI` link annotations written by `link(url) { … }`, and the system
 * browser / dialer the `:pdfkmp-viewer` module launches on a link tap.
 *
 * A generated PDF outlives the generating app and is consumed by
 * arbitrary readers; an annotation carrying a `javascript:`, `file:`,
 * `data:`, or `content:` URI is a code-execution / local-file channel
 * in readers that honour those schemes. The launcher side is the mirror
 * image: a document (or a host feature rendering attacker-supplied
 * text) must not turn a tap into an OS-level deep link the user never
 * consented to.
 */
public object PdfUrls {

    private val allowedSchemes: Set<String> = setOf("http", "https", "mailto", "tel")

    /**
     * Returns `true` when [url] carries an explicit RFC 3986 scheme
     * from the allowlist (`http`, `https`, `mailto`, `tel`). Relative,
     * scheme-relative, empty, and non-ASCII scheme candidates are
     * rejected: a PDF is a standalone artefact, so a relative URL has
     * no meaningful resolution base.
     */
    public fun isSafeExternalUrl(url: String): Boolean {
        // Control characters are invalid in any URI and are exactly what
        // log-forging / header-smuggling payloads rely on — reject them
        // anywhere in the string, not just in the scheme.
        if (url.any { it.code < 0x20 || it.code == 0x7F }) return false
        val colon = url.indexOf(':')
        if (colon <= 0 || !url[0].isAsciiLetter()) return false
        for (i in 1 until colon) {
            val c = url[i]
            if (!c.isAsciiLetter() && !c.isAsciiDigit() && c != '+' && c != '-' && c != '.') return false
        }
        return url.substring(0, colon).lowercase() in allowedSchemes
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
