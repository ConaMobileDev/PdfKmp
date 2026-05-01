package com.conamobile.pdfkmp.composeresources

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the magic-byte sniffing used by [DrawableResource.toPdfDrawable]
 * to decide between vector XML and raster bytes. The receiver-style helper
 * is internal, so we exercise it through the public surface by handing
 * synthesised byte arrays to [ByteArray.looksLikeXml].
 */
class PdfDrawableTest {

    @Test
    fun looksLikeXml_acceptsAndroidVectorXml() {
        val xml = """<?xml version="1.0"?><vector android:width="24dp"/>""".encodeToByteArray()
        assertTrue(xml.looksLikeXml(), "Plain XML declaration should be detected as XML")
    }

    @Test
    fun looksLikeXml_acceptsSvg() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"></svg>""".encodeToByteArray()
        assertTrue(svg.looksLikeXml(), "SVG content starting with `<svg` should be detected as XML")
    }

    @Test
    fun looksLikeXml_acceptsXmlAfterUtf8Bom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val xml = """<?xml version="1.0"?><vector/>""".encodeToByteArray()
        assertTrue((bom + xml).looksLikeXml(), "UTF-8 BOM followed by XML should still detect")
    }

    @Test
    fun looksLikeXml_acceptsXmlAfterLeadingWhitespace() {
        val xml = "\n\t   <vector/>".encodeToByteArray()
        assertTrue(xml.looksLikeXml(), "Leading whitespace before `<` should not block detection")
    }

    @Test
    fun looksLikeXml_rejectsPng() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        assertFalse(pngHeader.looksLikeXml(), "PNG magic bytes must not be classed as XML")
    }

    @Test
    fun looksLikeXml_rejectsJpeg() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertFalse(jpegHeader.looksLikeXml(), "JPEG SOI marker must not be classed as XML")
    }

    @Test
    fun looksLikeXml_rejectsWebp() {
        val webpHeader = "RIFF????WEBPVP8 ".encodeToByteArray()
        assertFalse(webpHeader.looksLikeXml(), "WEBP container must not be classed as XML")
    }

    @Test
    fun looksLikeXml_rejectsGif() {
        val gif89 = "GIF89a".encodeToByteArray()
        assertFalse(gif89.looksLikeXml(), "GIF89a header must not be classed as XML")
    }

    @Test
    fun looksLikeXml_rejectsEmptyArray() {
        assertFalse(ByteArray(0).looksLikeXml(), "Empty input should not be classed as XML")
    }

    @Test
    fun looksLikeXml_rejectsWhitespaceOnly() {
        val whitespace = "   \t\n\r".encodeToByteArray()
        assertFalse(whitespace.looksLikeXml(), "Whitespace without a `<` should not be classed as XML")
    }
}
