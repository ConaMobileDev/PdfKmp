package com.conamobile.pdfkmp.layout

/**
 * Position of a child inside a [com.conamobile.pdfkmp.dsl.box].
 *
 * Box children stack along the Z-axis in source order — first added is at
 * the bottom, last is on top. Each child is positioned within the box's
 * resolved interior using one of these nine anchor points.
 *
 * The naming follows Compose's `Alignment` constants exactly so anyone
 * familiar with Compose UI can carry their intuition straight over.
 */
public enum class BoxAlignment {
    TopStart,
    TopCenter,
    TopEnd,
    CenterStart,
    Center,
    CenterEnd,
    BottomStart,
    BottomCenter,
    BottomEnd,
}
