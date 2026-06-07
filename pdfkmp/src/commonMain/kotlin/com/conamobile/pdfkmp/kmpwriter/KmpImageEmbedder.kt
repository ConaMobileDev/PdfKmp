package com.conamobile.pdfkmp.kmpwriter

import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.image.readImageInfo

/**
 * Turns encoded image bytes into a PDF image XObject *without decoding the
 * pixels* — the bytes are embedded verbatim and the PDF viewer's own codec
 * inflates them. This is what lets the pure-Kotlin writer embed images with no
 * platform image library (and so compile for wasmJs), at the cost of supporting
 * only the formats and sub-formats whose on-disk byte layout maps directly onto
 * a PDF stream filter:
 *
 * - **JPEG** → `/DCTDecode`. The whole JFIF byte stream is a valid DCT stream,
 *   so it embeds as-is; the SOF marker is parsed only to choose `/DeviceRGB`
 *   vs `/DeviceGray` (1-component) and to read the dimensions.
 * - **PNG** → `/FlateDecode` with a PNG predictor. PNG's IDAT chunks already
 *   hold zlib-compressed, row-filtered samples — exactly the bytes a
 *   `/Predictor 15` Flate stream expects — but *only* for the non-interlaced,
 *   8-bit, truecolor (type 2) or grayscale (type 0) sub-formats. Palette (3),
 *   grayscale+alpha (4), and truecolor+alpha (6) need a decode pass this writer
 *   doesn't do, so they are skipped with a warning.
 *
 * Anything else (WebP, GIF, …) is skipped with a warning. A skip returns `null`;
 * the caller draws nothing, matching the "render keeps going" contract.
 */
internal object KmpImageEmbedder {

    /** Parsed, ready-to-embed image plus its intrinsic pixel size. */
    internal class Embeddable(
        val def: KmpImageDef,
        val widthPx: Int,
        val heightPx: Int,
    )

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /**
     * Returns an [Embeddable] for [bytes], or `null` (after a [PdfLog] warning)
     * when the format / sub-format can't be passed through losslessly.
     */
    fun embed(bytes: ByteArray): Embeddable? {
        if (bytes.size < 8) {
            PdfLog.warn("drawImage skipped: payload too small to be an image (pure-Kotlin backend)")
            return null
        }
        return when {
            isPng(bytes) -> embedPng(bytes)
            isJpeg(bytes) -> embedJpeg(bytes)
            else -> {
                PdfLog.warn("drawImage skipped: unsupported image format (pure-Kotlin backend embeds PNG/JPEG only)")
                null
            }
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    private fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < PNG_SIGNATURE.size) return false
        for (i in PNG_SIGNATURE.indices) if (bytes[i] != PNG_SIGNATURE[i]) return false
        return true
    }

    // -- JPEG -------------------------------------------------------------

    private fun embedJpeg(bytes: ByteArray): Embeddable? {
        val info = readImageInfo(bytes)
        if (info == null) {
            PdfLog.warn("drawImage skipped: JPEG header could not be parsed (pure-Kotlin backend)")
            return null
        }
        val components = jpegComponentCount(bytes) ?: 3
        val colorSpace = when (components) {
            1 -> "/DeviceGray"
            // 4-component (CMYK/YCCK) JPEGs would need /DeviceCMYK + a /Decode
            // inversion for Adobe-marked files; the common 3-component case is
            // DeviceRGB. Anything else falls back to RGB, which renders correct
            // colours for the overwhelmingly common baseline-RGB JPEG.
            else -> "/DeviceRGB"
        }
        val dict = buildString {
            append("<< /Type /XObject /Subtype /Image ")
            append("/Width ${info.widthPx} /Height ${info.heightPx} ")
            append("/ColorSpace $colorSpace /BitsPerComponent 8 ")
            append("/Filter /DCTDecode >>")
        }
        return Embeddable(KmpImageDef(dict, bytes), info.widthPx, info.heightPx)
    }

    /**
     * Reads the component count from the first start-of-frame marker (the byte
     * at offset SOF+9). Returns `null` when no SOF is found, in which case the
     * caller assumes 3-component RGB.
     */
    private fun jpegComponentCount(bytes: ByteArray): Int? {
        var offset = 2 // skip SOI
        while (offset < bytes.size - 1) {
            if (bytes[offset] != 0xFF.toByte()) return null
            var marker = bytes[offset + 1].toInt() and 0xFF
            var pad = 0
            while (marker == 0xFF) {
                pad++
                if (offset + 1 + pad >= bytes.size) return null
                marker = bytes[offset + 1 + pad].toInt() and 0xFF
            }
            offset += 1 + pad
            // Standalone markers carry no length payload.
            if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) {
                offset += 1
                continue
            }
            if (offset + 3 >= bytes.size) return null
            val segmentLength = ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                (bytes[offset + 2].toInt() and 0xFF)
            if (segmentLength < 2) return null
            val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (isSof) {
                // SOF payload: precision(1) height(2) width(2) components(1).
                val componentsAt = offset + 1 + 6
                if (componentsAt >= bytes.size) return null
                return bytes[componentsAt].toInt() and 0xFF
            }
            offset += 1 + segmentLength
        }
        return null
    }

    // -- PNG --------------------------------------------------------------

    private fun embedPng(bytes: ByteArray): Embeddable? {
        // IHDR is the first chunk: 8-byte signature, then 4-byte length, "IHDR",
        // then 13 data bytes: width(4) height(4) bitDepth(1) colorType(1)
        // compression(1) filter(1) interlace(1).
        if (bytes.size < 33) {
            PdfLog.warn("drawImage skipped: PNG too small to contain IHDR (pure-Kotlin backend)")
            return null
        }
        val width = readU32(bytes, 16)
        val height = readU32(bytes, 20)
        val bitDepth = bytes[24].toInt() and 0xFF
        val colorType = bytes[25].toInt() and 0xFF
        val interlace = bytes[28].toInt() and 0xFF

        if (width <= 0 || height <= 0) {
            PdfLog.warn("drawImage skipped: PNG has invalid dimensions (pure-Kotlin backend)")
            return null
        }
        if (interlace != 0 || bitDepth != 8 || (colorType != 2 && colorType != 0)) {
            // Palette / alpha / interlaced / non-8-bit PNGs would each need a
            // real decode + re-encode pass this writer doesn't do.
            PdfLog.warn(
                "drawImage skipped: PNG must be non-interlaced 8-bit truecolor (type 2) or " +
                    "grayscale (type 0) for verbatim embedding; got colorType=$colorType " +
                    "bitDepth=$bitDepth interlace=$interlace (pure-Kotlin backend)",
            )
            return null
        }

        val idat = concatenateIdat(bytes)
        if (idat == null || idat.isEmpty()) {
            PdfLog.warn("drawImage skipped: PNG has no IDAT data (pure-Kotlin backend)")
            return null
        }

        val colors = if (colorType == 2) 3 else 1
        val colorSpace = if (colorType == 2) "/DeviceRGB" else "/DeviceGray"
        val dict = buildString {
            append("<< /Type /XObject /Subtype /Image ")
            append("/Width $width /Height $height ")
            append("/ColorSpace $colorSpace /BitsPerComponent 8 ")
            append("/Filter /FlateDecode ")
            // Predictor 15 (PNG "optimum") tells the Flate filter to undo the
            // per-row PNG filtering that the IDAT bytes still carry.
            append("/DecodeParms << /Predictor 15 /Colors $colors ")
            append("/BitsPerComponent 8 /Columns $width >> >>")
        }
        return Embeddable(KmpImageDef(dict, idat), width, height)
    }

    /**
     * Concatenates every IDAT chunk's data bytes in file order — PNG allows the
     * compressed image to be split across multiple IDAT chunks, and the zlib
     * stream is their data fields joined end to end. Returns `null` if the chunk
     * structure is malformed.
     */
    private fun concatenateIdat(bytes: ByteArray): ByteArray? {
        val pieces = ArrayList<ByteArray>()
        var total = 0
        var offset = 8 // past the signature
        while (offset + 8 <= bytes.size) {
            val length = readU32(bytes, offset)
            if (length < 0) return null
            val typeStart = offset + 4
            val dataStart = typeStart + 4
            if (dataStart + length + 4 > bytes.size) {
                // Truncated chunk: stop. If we already gathered IDAT, use it.
                break
            }
            val type = chunkType(bytes, typeStart)
            if (type == "IDAT") {
                pieces.add(bytes.copyOfRange(dataStart, dataStart + length))
                total += length
            }
            if (type == "IEND") break
            // Advance past data + 4-byte CRC.
            offset = dataStart + length + 4
        }
        if (pieces.isEmpty()) return null
        val out = ByteArray(total)
        var pos = 0
        for (piece in pieces) {
            piece.copyInto(out, pos)
            pos += piece.size
        }
        return out
    }

    private fun chunkType(bytes: ByteArray, at: Int): String =
        buildString {
            for (i in 0 until 4) append((bytes[at + i].toInt() and 0xFF).toChar())
        }

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
