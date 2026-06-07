package com.conamobile.pdfkmp

import com.conamobile.pdfkmp.samples.Samples
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves the pure-Kotlin backend actually runs *on Wasm* (under the Node
 * test runner): real sample documents render to valid-looking PDF bytes
 * through `defaultPdfDriverFactory()`. Structural correctness of the
 * writer itself is gated on the JVM, where the identical common code is
 * re-parsed and rasterised with PdfBox (`KmpWriterBackendTest`).
 */
class WasmBackendSmokeTest {

    @Test
    fun helloWorld_rendersOnWasm() = assertPdf(Samples.helloWorld().toByteArray())

    @Test
    fun typography_rendersOnWasm() = assertPdf(Samples.typography().toByteArray())

    @Test
    fun barcodes_rendersOnWasm() = assertPdf(Samples.barcodes().toByteArray())

    @Test
    fun navigation_withTocAndOutline_rendersOnWasm() =
        assertPdf(Samples.navigation().toByteArray())

    @Test
    fun longTable_paginatesOnWasm() = assertPdf(Samples.longTable().toByteArray())

    @Test
    fun designExtras_gradientsAndShadows_renderOnWasm() =
        assertPdf(Samples.designExtras().toByteArray())

    private fun assertPdf(bytes: ByteArray) {
        assertTrue(bytes.size > 500, "suspiciously small output: ${bytes.size} bytes")
        val header = bytes.copyOfRange(0, 5).decodeToString()
        assertTrue(header.startsWith("%PDF-"), "not a PDF header: '$header'")
        val tail = bytes.copyOfRange(bytes.size - 32, bytes.size).decodeToString()
        assertTrue(tail.contains("%%EOF"), "missing %%EOF trailer")
    }
}
