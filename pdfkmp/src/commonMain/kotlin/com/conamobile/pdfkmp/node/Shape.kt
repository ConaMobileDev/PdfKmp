package com.conamobile.pdfkmp.node

import com.conamobile.pdfkmp.style.PdfPaint
import com.conamobile.pdfkmp.unit.Dp

/**
 * Geometric primitive — a circle or ellipse drawn as a filled and / or
 * stroked shape. Use the [com.conamobile.pdfkmp.dsl.ContainerScope.circle]
 * / [com.conamobile.pdfkmp.dsl.ContainerScope.ellipse] DSL functions
 * rather than constructing this directly.
 */
public data class ShapeNode(
    val shape: Shape,
    val width: Dp,
    val height: Dp,
    val fill: PdfPaint? = null,
    val strokeColor: com.conamobile.pdfkmp.style.PdfColor? = null,
    val strokeWidth: Dp = Dp.Zero,
) : PdfNode

/**
 * Discriminator for [ShapeNode]. Other geometric shapes (squircles,
 * rounded squares, etc.) can plug in here — the renderer dispatches on
 * this variant when generating path commands.
 */
public sealed interface Shape {
    /** Perfect circle whose diameter equals the smaller of the bounding dims. */
    public data object Circle : Shape

    /** Ellipse that fully fills the supplied [ShapeNode.width] / [ShapeNode.height]. */
    public data object Ellipse : Shape
}
