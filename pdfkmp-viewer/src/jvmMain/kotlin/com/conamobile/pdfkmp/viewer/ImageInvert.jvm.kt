package com.conamobile.pdfkmp.viewer

import java.awt.image.BufferedImage
import java.awt.image.RescaleOp

/**
 * Returns a colour-inverted copy of [source] for the Desktop viewer's
 * dark-mode (`invertColors`) reading surface. Each RGB channel is
 * mapped `out = 255 − in` via a [RescaleOp] with scale `-1` and offset
 * `255`; the alpha channel is left untouched so transparent regions
 * (page margins, anti-aliased edges) stay transparent rather than
 * flipping to opaque black.
 *
 * Pulled out of the renderer into its own pure function so the pixel
 * maths can be unit-tested on the JVM without spinning up PdfBox or a
 * Compose `ImageBitmap`.
 */
internal fun invertRgb(source: BufferedImage): BufferedImage {
    // Force an INT_ARGB destination so the op always sees a known
    // channel layout (PdfBox can hand us TYPE_INT_RGB, which RescaleOp
    // would otherwise rescale all three of without the alpha guard we
    // rely on, but normalising up front keeps the contract explicit).
    val dest = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    // A 3-factor RescaleOp targets only the colour bands (R, G, B) on an
    // ARGB raster and leaves the alpha band alone — exactly the dark-mode
    // behaviour we want.
    val op = RescaleOp(
        floatArrayOf(-1f, -1f, -1f),
        floatArrayOf(255f, 255f, 255f),
        null,
    )
    op.filter(toArgb(source), dest)
    return dest
}

/**
 * Normalises [source] to a TYPE_INT_ARGB image so [invertRgb]'s
 * 3-factor [RescaleOp] (which expects R, G, B + an untouched alpha
 * band) has a consistent raster to operate on regardless of the type
 * PdfBox produced.
 */
private fun toArgb(source: BufferedImage): BufferedImage {
    if (source.type == BufferedImage.TYPE_INT_ARGB) return source
    val argb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val g = argb.createGraphics()
    g.drawImage(source, 0, 0, null)
    g.dispose()
    return argb
}
