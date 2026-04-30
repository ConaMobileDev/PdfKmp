package com.conamobile.pdfkmp.samples

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Smoke test: every public sample must produce a non-empty, valid-looking
 * PDF byte stream when executed end-to-end through the rendering pipeline.
 *
 * Catches regressions where a layout / renderer change breaks one of the
 * documented samples — running this test on the iOS Simulator surfaces
 * iOS-specific failures (e.g. CoreGraphics state mismatches) that the
 * faked-driver tests in `RenderTest` would otherwise miss.
 */
class SamplesSmokeTest {

    @Test
    fun helloWorld_producesValidPdf() = assertSamplePdf(Samples.helloWorld().toByteArray())

    @Test
    fun typography_producesValidPdf() = assertSamplePdf(Samples.typography().toByteArray())

    @Test
    fun rowAndColumn_producesValidPdf() = assertSamplePdf(Samples.rowAndColumn().toByteArray())

    @Test
    fun columnSpaceBetween_producesValidPdf() = assertSamplePdf(Samples.columnSpaceBetween().toByteArray())

    @Test
    fun tableShowcase_producesValidPdf() = assertSamplePdf(Samples.tableShowcase().toByteArray())

    @Test
    fun vectorShowcase_producesValidPdf() = assertSamplePdf(Samples.vectorShowcase().toByteArray())

    @Test
    fun vectorAdvanced_producesValidPdf() = assertSamplePdf(Samples.vectorAdvanced().toByteArray())

    @Test
    fun customDesigns_producesValidPdf() {
        // 1×1 transparent PNG is enough to exercise the image pipeline
        // without bloating the test fixture.
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        assertSamplePdf(Samples.customDesigns(pngBytes).toByteArray())
    }

    @Test
    fun customPadding_producesValidPdf() = assertSamplePdf(Samples.customPadding().toByteArray())

    @Test
    fun pageChrome_producesValidPdf() = assertSamplePdf(Samples.pageChrome().toByteArray())

    @Test
    fun showcase_producesValidPdf() = assertSamplePdf(Samples.showcase().toByteArray())

    private fun assertSamplePdf(bytes: ByteArray) {
        assertTrue(bytes.isNotEmpty(), "Sample produced no bytes")
        // Every PDF starts with the magic header `%PDF-`.
        val header = bytes.take(5).map { it.toInt().toChar() }.joinToString("")
        assertTrue(header.startsWith("%PDF-"), "Output is not a valid PDF (header was '$header')")
    }
}
