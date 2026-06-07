package com.conamobile.pdfkmp.viewer

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [invertRgb], the Desktop dark-mode inversion helper.
 * Exercises the pixel maths directly on small [BufferedImage]s so the
 * RGB-flip / alpha-preserve contract is verified without PdfBox or a
 * Compose `ImageBitmap`.
 */
class ImageInvertTest {

    @Test
    fun white_inverts_to_black() {
        val source = solid(0xFF, 0xFF, 0xFF, 0xFF)
        val out = invertRgb(source)
        val (a, r, g, b) = channels(out.getRGB(0, 0))
        assertEquals(0x00, r, "red should flip 255 → 0")
        assertEquals(0x00, g, "green should flip 255 → 0")
        assertEquals(0x00, b, "blue should flip 255 → 0")
        assertEquals(0xFF, a, "alpha must be preserved")
    }

    @Test
    fun black_inverts_to_white() {
        val source = solid(0xFF, 0x00, 0x00, 0x00)
        val out = invertRgb(source)
        val (a, r, g, b) = channels(out.getRGB(0, 0))
        assertEquals(0xFF, r)
        assertEquals(0xFF, g)
        assertEquals(0xFF, b)
        assertEquals(0xFF, a)
    }

    @Test
    fun arbitrary_colour_inverts_per_channel() {
        // 0x30 → 0xCF, 0x80 → 0x7F, 0xC0 → 0x3F (out = 255 - in).
        val source = solid(0xFF, 0x30, 0x80, 0xC0)
        val out = invertRgb(source)
        val (_, r, g, b) = channels(out.getRGB(0, 0))
        assertEquals(0xCF, r)
        assertEquals(0x7F, g)
        assertEquals(0x3F, b)
    }

    @Test
    fun translucent_alpha_is_left_untouched() {
        val source = solid(0x80, 0xFF, 0xFF, 0xFF)
        val out = invertRgb(source)
        val (a, _, _, _) = channels(out.getRGB(0, 0))
        // The RescaleOp only rescales the colour bands, so the partial
        // alpha survives the inversion unchanged.
        assertEquals(0x80, a, "partial alpha must survive inversion")
    }

    @Test
    fun output_dimensions_match_input() {
        val source = BufferedImage(7, 3, BufferedImage.TYPE_INT_ARGB)
        val out = invertRgb(source)
        assertEquals(7, out.width)
        assertEquals(3, out.height)
    }

    /** Builds a 1×1 ARGB image filled with the given channel values. */
    private fun solid(a: Int, r: Int, g: Int, b: Int): BufferedImage {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, (a shl 24) or (r shl 16) or (g shl 8) or b)
        return image
    }

    /**
     * Splits a packed ARGB int into its (a, r, g, b) byte components.
     * Returned as an [IntArray] so the call sites can destructure via
     * the stdlib `component1()`…`component4()` operators.
     */
    private fun channels(argb: Int): IntArray = intArrayOf(
        (argb ushr 24) and 0xFF,
        (argb ushr 16) and 0xFF,
        (argb ushr 8) and 0xFF,
        argb and 0xFF,
    )
}
