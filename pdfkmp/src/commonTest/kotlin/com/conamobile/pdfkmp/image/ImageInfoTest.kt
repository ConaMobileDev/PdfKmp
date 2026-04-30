package com.conamobile.pdfkmp.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies that [readImageInfo] picks up dimensions from real PNG/JPEG headers. */
class ImageInfoTest {

    @Test
    fun pngHeader_returnsCorrectDimensions() {
        // Hand-crafted minimal PNG: 8-byte signature, IHDR chunk (length=13).
        val bytes = pngHeader(width = 320, height = 240)
        val info = readImageInfo(bytes)
        assertEquals(ImageInfo(320, 240), info)
    }

    @Test
    fun jpegHeader_returnsCorrectDimensions() {
        val bytes = minimalJpegWithSof0(width = 1024, height = 768)
        val info = readImageInfo(bytes)
        assertEquals(ImageInfo(1024, 768), info)
    }

    @Test
    fun unknownFormat_returnsNull() {
        assertNull(readImageInfo(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
    }

    @Test
    fun emptyInput_returnsNull() {
        assertNull(readImageInfo(byteArrayOf()))
    }

    @Test
    fun truncatedPng_returnsNull() {
        val full = pngHeader(width = 100, height = 100)
        assertNull(readImageInfo(full.copyOf(15)))
    }

    private fun pngHeader(width: Int, height: Int): ByteArray = byteArrayOf(
        // 8-byte PNG signature
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        // IHDR chunk length (13)
        0x00, 0x00, 0x00, 0x0D,
        // IHDR type
        0x49, 0x48, 0x44, 0x52,
        // width (big-endian u32)
        ((width ushr 24) and 0xFF).toByte(),
        ((width ushr 16) and 0xFF).toByte(),
        ((width ushr 8) and 0xFF).toByte(),
        (width and 0xFF).toByte(),
        // height
        ((height ushr 24) and 0xFF).toByte(),
        ((height ushr 16) and 0xFF).toByte(),
        ((height ushr 8) and 0xFF).toByte(),
        (height and 0xFF).toByte(),
        // bit depth, colour type, compression, filter, interlace, CRC (junk)
        0x08, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    private fun minimalJpegWithSof0(width: Int, height: Int): ByteArray = byteArrayOf(
        // SOI
        0xFF.toByte(), 0xD8.toByte(),
        // SOF0 marker
        0xFF.toByte(), 0xC0.toByte(),
        // segment length (8)
        0x00, 0x08,
        // bits per sample
        0x08,
        // height (big-endian u16)
        ((height ushr 8) and 0xFF).toByte(),
        (height and 0xFF).toByte(),
        // width
        ((width ushr 8) and 0xFF).toByte(),
        (width and 0xFF).toByte(),
        // components
        0x01,
        0x01, 0x11, 0x00,
    )
}
