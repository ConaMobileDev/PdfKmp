package com.conamobile.pdfkmp

/**
 * Diagnostics hook for conditions PdfKmp handles gracefully but silently —
 * undecodable image bytes, missing fonts falling back to the default, and
 * similar "the document still renders, but not the way you meant" cases.
 *
 * Off by default: a PDF library should not write to an app's console
 * uninvited. Install a logger during development to surface the warnings:
 *
 * ```
 * PdfLog.logger = { message -> println("PdfKmp: $message") }
 * ```
 */
public object PdfLog {

    /**
     * Receives one human-readable line per swallowed problem. `null`
     * (default) disables logging entirely.
     */
    public var logger: ((String) -> Unit)? = null

    /** Forwards [message] to the installed [logger], if any. */
    public fun warn(message: String) {
        logger?.invoke(message)
    }
}
