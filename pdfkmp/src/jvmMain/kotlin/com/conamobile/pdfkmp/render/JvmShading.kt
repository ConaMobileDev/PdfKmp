package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.style.GradientStop
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBoolean
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSFloat
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.common.function.PDFunction
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType3
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType3

/**
 * Builds the native PDF shading objects PdfBox needs to fill a path with a
 * gradient — the JVM analogue of Android's `LinearGradient` /
 * `RadialGradient` shaders and iOS's `CGGradient`.
 *
 * Colour transitions are encoded as a PDF function: a single exponential
 * interpolation (type 2) for a two-stop gradient, or a stitching function
 * (type 3) that chains one type-2 segment per adjacent stop pair for
 * multi-stop gradients. Per-stop alpha is not represented — PDF gradients
 * need a separate soft mask for transparency — so stops are treated as
 * opaque, matching the common opaque-gradient case.
 *
 * All coordinates are expected in PDF user space (bottom-left origin); the
 * caller is responsible for flipping the Y axis from PdfKmp's top-left
 * convention before calling in.
 */

/** Axial (linear) gradient between two flipped-space points. */
internal fun buildAxialShading(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    stops: List<GradientStop>,
): PDShading {
    val shading = PDShadingType2(COSDictionary())
    shading.shadingType = PDShading.SHADING_TYPE2
    shading.colorSpace = PDDeviceRGB.INSTANCE
    shading.coords = cosArrayOf(startX, startY, endX, endY)
    shading.function = buildColorFunction(stops)
    shading.cosObject.setItem(COSName.EXTEND, extendBoth())
    return shading
}

/** Radial gradient growing from the centre to [radius] in flipped space. */
internal fun buildRadialShading(
    centerX: Float,
    centerY: Float,
    radius: Float,
    stops: List<GradientStop>,
): PDShading {
    val shading = PDShadingType3(COSDictionary())
    shading.shadingType = PDShading.SHADING_TYPE3
    shading.colorSpace = PDDeviceRGB.INSTANCE
    shading.coords = cosArrayOf(centerX, centerY, 0f, centerX, centerY, radius)
    shading.function = buildColorFunction(stops)
    shading.cosObject.setItem(COSName.EXTEND, extendBoth())
    return shading
}

private fun extendBoth(): COSArray = COSArray().apply {
    add(COSBoolean.TRUE)
    add(COSBoolean.TRUE)
}

private fun cosArrayOf(vararg values: Float): COSArray = COSArray().apply {
    values.forEach { add(COSFloat(it)) }
}

/**
 * Builds the PDF colour function mapping the gradient parameter `t ∈ [0,1]`
 * to an RGB triple. Stops are sorted by offset; a degenerate single-stop
 * gradient collapses to a constant colour.
 */
private fun buildColorFunction(stops: List<GradientStop>): PDFunction {
    val sorted = stops.sortedBy { it.offset }
    if (sorted.size <= 1) {
        val color = sorted.firstOrNull()?.color
        return type2(color?.let { rgb(it) } ?: floatArrayOf(0f, 0f, 0f), color?.let { rgb(it) } ?: floatArrayOf(0f, 0f, 0f))
    }
    if (sorted.size == 2) {
        return type2(rgb(sorted[0].color), rgb(sorted[1].color))
    }

    // Multi-stop: stitch one type-2 segment per adjacent stop pair.
    val dict = COSDictionary()
    dict.setInt(COSName.FUNCTION_TYPE, 3)
    dict.setItem(COSName.DOMAIN, cosArrayOf(0f, 1f))

    val functions = COSArray()
    val encode = COSArray()
    for (i in 0 until sorted.size - 1) {
        functions.add(type2(rgb(sorted[i].color), rgb(sorted[i + 1].color)).cosObject)
        encode.add(COSFloat(0f))
        encode.add(COSFloat(1f))
    }
    val bounds = COSArray()
    for (i in 1 until sorted.size - 1) {
        bounds.add(COSFloat(sorted[i].offset.coerceIn(0f, 1f)))
    }
    dict.setItem(COSName.FUNCTIONS, functions)
    dict.setItem(COSName.BOUNDS, bounds)
    dict.setItem(COSName.ENCODE, encode)
    return PDFunctionType3(dict)
}

/** A type-2 exponential interpolation function from [c0] to [c1] (linear, N=1). */
private fun type2(c0: FloatArray, c1: FloatArray): PDFunctionType2 {
    val dict = COSDictionary()
    dict.setInt(COSName.FUNCTION_TYPE, 2)
    dict.setItem(COSName.DOMAIN, cosArrayOf(0f, 1f))
    dict.setItem(COSName.C0, COSArray().apply { c0.forEach { add(COSFloat(it)) } })
    dict.setItem(COSName.C1, COSArray().apply { c1.forEach { add(COSFloat(it)) } })
    dict.setItem(COSName.N, COSFloat(1f))
    return PDFunctionType2(dict)
}

private fun rgb(color: com.conamobile.pdfkmp.style.PdfColor): FloatArray =
    floatArrayOf(
        color.red.coerceIn(0f, 1f),
        color.green.coerceIn(0f, 1f),
        color.blue.coerceIn(0f, 1f),
    )
