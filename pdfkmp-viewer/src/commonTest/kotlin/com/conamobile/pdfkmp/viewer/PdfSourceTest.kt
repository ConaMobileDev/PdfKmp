package com.conamobile.pdfkmp.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Smoke tests for the [PdfSource] sealed type and its [bytes] accessor.
 * Real renderer / share-sheet behaviour lives behind `expect` and is
 * exercised through the platform-specific test surfaces.
 */
class PdfSourceTest {

    @Test
    fun bytes_factoryReturnsBytesVariant() {
        val raw = byteArrayOf(0x25, 0x50, 0x44, 0x46) // %PDF
        val source = PdfSource.of(raw)
        assertTrue(source is PdfSource.Bytes, "of(ByteArray) must yield a Bytes variant")
        assertSame(raw, source.bytes, "of(ByteArray) must not copy the input array")
    }

    @Test
    fun bytes_extensionUnwrapsBytesVariant() {
        val raw = byteArrayOf(1, 2, 3, 4)
        val source: PdfSource = PdfSource.Bytes(raw)
        assertSame(raw, source.bytes(), "PdfSource.bytes() must return the original array")
    }

    @Test
    fun bytes_emptyArrayIsLegalSource() {
        val source = PdfSource.of(ByteArray(0))
        assertEquals(0, source.bytes().size, "Empty PDFs are valid input — the renderer decides what to do")
    }
}
