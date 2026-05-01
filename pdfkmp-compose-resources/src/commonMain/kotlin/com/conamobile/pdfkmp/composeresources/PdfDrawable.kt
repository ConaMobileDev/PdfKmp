package com.conamobile.pdfkmp.composeresources

import com.conamobile.pdfkmp.vector.VectorImage

/**
 * Lazily-loaded drawable that can be embedded in a PdfKmp document
 * regardless of whether the source resource is XML (parsed into a
 * [VectorImage]) or a raster bitmap (kept as raw bytes for the
 * platform decoder).
 *
 * Produce one with [DrawableResource.toPdfDrawable] — that helper
 * sniffs the file's leading bytes and picks the right variant, so
 * the call site never has to know whether the asset is a `<vector>` /
 * `<svg>` XML or a PNG / JPEG / WEBP. Hand the result to
 * `drawable(...)` inside the DSL and it dispatches to `vector(...)` or
 * `image(...)` accordingly.
 */
public sealed class PdfDrawable {

    /** Vector drawable parsed from `<vector>` or `<svg>` XML. */
    public class Vector internal constructor(public val image: VectorImage) : PdfDrawable()

    /** Raster drawable (PNG / JPEG / WEBP / HEIF) ready to feed to `image(bytes = ...)`. */
    public class Raster internal constructor(public val bytes: ByteArray) : PdfDrawable()
}

/**
 * Returns `true` when [bytes] start with what looks like XML — an
 * optional UTF-8 BOM, optional leading whitespace, then `<`. Every
 * raster format PdfKmp accepts (PNG, JPEG, WEBP, HEIF, GIF, BMP)
 * starts with binary magic bytes that are never `<`, so a `<` on the
 * first non-whitespace position is a reliable XML signal.
 */
internal fun ByteArray.looksLikeXml(): Boolean {
    if (isEmpty()) return false
    var i = 0
    if (size >= 3 &&
        this[0] == 0xEF.toByte() &&
        this[1] == 0xBB.toByte() &&
        this[2] == 0xBF.toByte()
    ) {
        i = 3
    }
    while (i < size) {
        val b = this[i].toInt() and 0xFF
        // ASCII whitespace per the XML spec: space, tab, CR, LF.
        if (b != 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) break
        i++
    }
    return i < size && this[i] == '<'.code.toByte()
}
