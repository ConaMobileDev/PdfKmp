package com.conamobile.pdfkmp.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Smoke tests for the [PdfSource] sealed type, the [auto] dispatcher,
 * and the internal helpers used by [PdfViewer]. Real renderer /
 * share-sheet behaviour lives behind `expect` and is exercised
 * through the platform-specific test surfaces.
 */
class PdfSourceTest {

    @Test
    fun of_bytes_returnsBytesVariant() {
        val raw = byteArrayOf(0x25, 0x50, 0x44, 0x46) // %PDF
        val source = PdfSource.of(raw)
        assertTrue(source is PdfSource.Bytes, "of(ByteArray) must yield a Bytes variant")
        assertSame(raw, source.bytes, "of(ByteArray) must not copy the input array")
    }

    @Test
    fun inMemoryBytes_unwrapsBytesVariant() {
        val raw = byteArrayOf(1, 2, 3, 4)
        val source: PdfSource = PdfSource.Bytes(raw)
        assertSame(raw, source.inMemoryBytesOrNull(), "Bytes variant must expose its array directly")
    }

    @Test
    fun inMemoryBytes_isNullForAsyncVariants() {
        assertNull(PdfSource.FilePath("/tmp/x.pdf").inMemoryBytesOrNull())
        assertNull(PdfSource.Remote("https://example.com/x.pdf").inMemoryBytesOrNull())
        assertNull(PdfSource.ContentUri("content://x/y").inMemoryBytesOrNull())
        assertNull(PdfSource.Asset("manual.pdf").inMemoryBytesOrNull())
    }

    @Test
    fun emptyByteArray_isLegalSource() {
        val source = PdfSource.of(ByteArray(0))
        assertEquals(0, source.inMemoryBytesOrNull()?.size, "Empty PDFs are valid input")
    }

    @Test
    fun auto_recognisesHttpUrls() {
        val httpsSource = PdfSource.auto("https://example.com/invoice.pdf")
        assertTrue(httpsSource is PdfSource.Remote)
        assertEquals("https://example.com/invoice.pdf", httpsSource.url)

        val httpSource = PdfSource.auto("http://example.com/invoice.pdf")
        assertTrue(httpSource is PdfSource.Remote)
    }

    @Test
    fun auto_recognisesContentUris() {
        val source = PdfSource.auto("content://com.example.docs/123")
        assertTrue(source is PdfSource.ContentUri)
        assertEquals("content://com.example.docs/123", source.uri)
    }

    @Test
    fun auto_recognisesAssetSchemes() {
        val androidStyle = PdfSource.auto("asset:///docs/manual.pdf")
        assertTrue(androidStyle is PdfSource.Asset)
        assertEquals("docs/manual.pdf", androidStyle.path)

        val iosStyle = PdfSource.auto("bundle:///docs/manual.pdf")
        assertTrue(iosStyle is PdfSource.Asset)
        assertEquals("docs/manual.pdf", iosStyle.path)
    }

    @Test
    fun auto_treatsFileUrlsAndBarePathsAsFilePath() {
        val bare = PdfSource.auto("/storage/emulated/0/Download/x.pdf")
        assertTrue(bare is PdfSource.FilePath)
        assertEquals("/storage/emulated/0/Download/x.pdf", bare.path)

        val withScheme = PdfSource.auto("file:///storage/emulated/0/Download/x.pdf")
        assertTrue(withScheme is PdfSource.FilePath)
        assertEquals("/storage/emulated/0/Download/x.pdf", withScheme.path)
    }

    @Test
    fun remote_defaultsAreSane() {
        val source = PdfSource.Remote("https://example.com/x.pdf")
        assertTrue(source.headers.isEmpty(), "Remote().headers must default to empty")
        assertEquals(PdfSource.DEFAULT_TIMEOUT_MILLIS, source.timeoutMillis)
    }
}
