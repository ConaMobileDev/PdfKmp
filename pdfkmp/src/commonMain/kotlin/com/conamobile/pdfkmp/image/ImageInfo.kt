package com.conamobile.pdfkmp.image

/**
 * Intrinsic dimensions of an encoded image, in pixels.
 *
 * Returned by [readImageInfo] when the supplied bytes carry a recognizable
 * PNG or JPEG header. The library uses these to:
 *
 * 1. Compute layout sizes for `image(bytes)` calls that omit one or both
 *    explicit dimensions (the missing axis is derived from the intrinsic
 *    aspect ratio).
 * 2. Skip a full image decode at layout time — the renderer only decodes
 *    later, on the platform side, when it actually needs to draw.
 */
public data class ImageInfo(
    /** Intrinsic width in pixels. */
    val widthPx: Int,
    /** Intrinsic height in pixels. */
    val heightPx: Int,
)

/**
 * Parses the supplied image bytes and returns the intrinsic dimensions if
 * the format header is recognized.
 *
 * Supported formats:
 *
 * - **PNG** — bytes 16..23 of the IHDR chunk (big-endian u32 width then
 *   height).
 * - **JPEG** — first SOF marker (`FF C0..FF CF` excluding `FF C4`,
 *   `FF C8`, `FF CC`) carries a 2-byte height followed by 2-byte width.
 *
 * Returns `null` for unrecognized or malformed input — callers must then
 * supply explicit dimensions through the DSL. WebP, GIF, and HEIF support
 * is intentionally deferred until the platform decoders prove insufficient.
 */
public fun readImageInfo(bytes: ByteArray): ImageInfo? {
    if (bytes.size < MIN_HEADER_BYTES) return null
    return when {
        isPng(bytes) -> readPngInfo(bytes)
        isJpeg(bytes) -> readJpegInfo(bytes)
        else -> null
    }
}

private const val MIN_HEADER_BYTES = 8

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

private fun isPng(bytes: ByteArray): Boolean {
    if (bytes.size < PNG_SIGNATURE.size) return false
    for (i in PNG_SIGNATURE.indices) {
        if (bytes[i] != PNG_SIGNATURE[i]) return false
    }
    return true
}

private fun isJpeg(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

private fun readPngInfo(bytes: ByteArray): ImageInfo? {
    // PNG layout: 8-byte signature, then chunks. The first chunk must be
    // IHDR which carries width (u32) and height (u32) at offsets 16 and 20.
    if (bytes.size < 24) return null
    val width = readU32BigEndian(bytes, 16)
    val height = readU32BigEndian(bytes, 20)
    if (width <= 0 || height <= 0) return null
    return ImageInfo(widthPx = width, heightPx = height)
}

private fun readJpegInfo(bytes: ByteArray): ImageInfo? {
    // Walk the segment chain looking for the first start-of-frame marker.
    // Each segment after the SOI starts with FF marker, then a 2-byte
    // big-endian length (which includes those two length bytes), then the
    // payload.
    var offset = 2 // skip SOI
    while (offset < bytes.size - 1) {
        if (bytes[offset] != 0xFF.toByte()) return null
        // FF FF padding bytes are allowed; skip them.
        var marker = bytes[offset + 1].toInt() and 0xFF
        var pad = 0
        while (marker == 0xFF) {
            pad += 1
            if (offset + 1 + pad >= bytes.size) return null
            marker = bytes[offset + 1 + pad].toInt() and 0xFF
        }
        offset += 1 + pad // now points at the marker byte itself

        // Standalone markers without payload — RST0..RST7, SOI, EOI, TEM.
        if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) {
            offset += 1
            continue
        }

        if (offset + 3 >= bytes.size) return null
        val segmentLength = ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
        if (segmentLength < 2) return null

        // Start of Frame markers — every C0..CF except C4 (DHT), C8 (JPG),
        // and CC (DAC) — encode the frame dimensions starting 5 bytes in.
        val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
        if (isSof) {
            if (offset + 8 >= bytes.size) return null
            val height = ((bytes[offset + 4].toInt() and 0xFF) shl 8) or
                (bytes[offset + 5].toInt() and 0xFF)
            val width = ((bytes[offset + 6].toInt() and 0xFF) shl 8) or
                (bytes[offset + 7].toInt() and 0xFF)
            if (width <= 0 || height <= 0) return null
            return ImageInfo(widthPx = width, heightPx = height)
        }

        offset += 1 + segmentLength
    }
    return null
}

private fun readU32BigEndian(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)
}
