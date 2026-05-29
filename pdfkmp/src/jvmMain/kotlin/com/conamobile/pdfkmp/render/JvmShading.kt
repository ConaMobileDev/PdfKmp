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
        val color = sorted.firstOrNull()?.let { rgb(it.color) } ?: floatArrayOf(0f, 0f, 0f)
        return type2(color, color)
    }

    // Build a type-3 stitching function whose segment boundaries match the
    // REAL stop offsets, with constant leading/trailing segments that clamp
    // the end colours outside [first.offset, last.offset]. This mirrors
    // Android's Shader.TileMode.CLAMP and iOS's kCGGradientDrawsBefore/After
    // so a gradient authored with non-0/1 endpoints renders identically on
    // every platform. (The earlier code stretched the transition across the
    // whole axis, ignoring the first/last offsets.)
    val first = sorted.first().offset.coerceIn(0f, 1f)
    val last = sorted.last().offset.coerceIn(0f, 1f)

    val functions = COSArray()
    val bounds = COSArray()
    val encode = COSArray()

    fun addSegment(c0: FloatArray, c1: FloatArray) {
        functions.add(type2(c0, c1).cosObject)
        encode.add(COSFloat(0f))
        encode.add(COSFloat(1f))
    }

    if (first > 0f) {
        // Constant clamp from t=0 to the first stop's offset.
        addSegment(rgb(sorted.first().color), rgb(sorted.first().color))
        bounds.add(COSFloat(first))
    }
    for (i in 0 until sorted.size - 1) {
        addSegment(rgb(sorted[i].color), rgb(sorted[i + 1].color))
        // Interior boundary between this segment and the next.
        if (i < sorted.size - 2) {
            bounds.add(COSFloat(sorted[i + 1].offset.coerceIn(0f, 1f)))
        }
    }
    if (last < 1f) {
        // Constant clamp from the last stop's offset to t=1.
        bounds.add(COSFloat(last))
        addSegment(rgb(sorted.last().color), rgb(sorted.last().color))
    }

    val dict = COSDictionary()
    dict.setInt(COSName.FUNCTION_TYPE, 3)
    dict.setItem(COSName.DOMAIN, cosArrayOf(0f, 1f))
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
