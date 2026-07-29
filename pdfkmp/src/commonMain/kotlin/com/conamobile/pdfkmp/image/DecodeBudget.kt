package com.conamobile.pdfkmp.image

/**
 * Absolute pixel ceiling for platform image decodes, applied to the
 * *declared* header dimensions before any pixel memory is allocated.
 *
 * A hostile file can claim enormous dimensions in a tiny header; without
 * a pre-decode budget the platform decoder obliges with a multi-gigabyte
 * allocation and kills the process. 50 megapixels is far beyond any
 * legitimate document illustration and corresponds to roughly 200 MB of
 * decoded ARGB pixels — large, but survivable on any desktop-class host.
 */
internal const val MAX_DECODE_PIXELS: Long = 50_000_000L

/**
 * Returns `true` when [bytes] carry a recognizable image header whose
 * declared dimensions exceed [MAX_DECODE_PIXELS]. Unrecognized headers
 * return `false` — the platform decoder remains the last line of defense
 * for formats [readImageInfo] does not parse.
 */
internal fun exceedsDecodeBudget(bytes: ByteArray): Boolean {
    val info = readImageInfo(bytes) ?: return false
    return info.widthPx.toLong() * info.heightPx.toLong() > MAX_DECODE_PIXELS
}
