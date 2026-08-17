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
 *
 * The default set suits the untrusted case. A document assembled entirely
 * from trusted, in-house targets can widen it once at startup — see
 * [allowedSchemes].
 */
public object PdfUrls {

    /**
     * Schemes allowed unless [allowedSchemes] is reassigned: `http`,
     * `https`, `mailto`, `tel`.
     */
    public val DEFAULT_ALLOWED_SCHEMES: Set<String> = setOf("http", "https", "mailto", "tel")

    /**
     * Schemes [isSafeExternalUrl] accepts. Assigning normalises entries to
     * lower case, so `setOf("HTTPS")` and `setOf("https")` behave alike.
     *
     * Widen this only for targets the *document author* controls — an
     * internal-distribution PDF pointing at a company `myapp://` deep link,
     * say. It is a process-wide switch that applies to annotation writing
     * *and* to the viewer's tap handling, so adding `javascript` or `file`
     * here re-opens the code-execution and local-file channels the default
     * set exists to close. Never derive it from document content.
     *
     * ```
     * PdfUrls.allowedSchemes = PdfUrls.DEFAULT_ALLOWED_SCHEMES + "myapp"
     * ```
     *
     * @throws IllegalArgumentException when the set is empty or an entry is
     *   not a syntactically valid RFC 3986 scheme.
     */
    public var allowedSchemes: Set<String> = DEFAULT_ALLOWED_SCHEMES
        set(value) {
            require(value.isNotEmpty()) {
                "allowedSchemes must not be empty; assign DEFAULT_ALLOWED_SCHEMES to restore the default"
            }
            val normalized = value.mapTo(mutableSetOf()) { it.lowercase() }
            val invalid = normalized.filterNot { isValidScheme(it) }
            require(invalid.isEmpty()) {
                "Not valid RFC 3986 schemes (letter followed by letters/digits/+/-/.): $invalid"
            }
            field = normalized
        }

    /**
     * Returns `true` when [url] carries an explicit RFC 3986 scheme found in
     * [allowedSchemes]. Relative, scheme-relative, empty, and non-ASCII
     * scheme candidates are rejected: a PDF is a standalone artefact, so a
     * relative URL has no meaningful resolution base.
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

    /** RFC 3986 `scheme`: ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ). */
    private fun isValidScheme(candidate: String): Boolean {
        if (candidate.isEmpty() || !candidate[0].isAsciiLetter()) return false
        for (i in 1 until candidate.length) {
            val c = candidate[i]
            if (!c.isAsciiLetter() && !c.isAsciiDigit() && c != '+' && c != '-' && c != '.') return false
        }
        return true
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
