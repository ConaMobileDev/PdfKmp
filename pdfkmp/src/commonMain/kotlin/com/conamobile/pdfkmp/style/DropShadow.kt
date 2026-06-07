package com.conamobile.pdfkmp.style

import com.conamobile.pdfkmp.unit.Dp

/**
 * Soft shadow drawn behind a decorated container (card, box, column, row).
 *
 * PDF has no native gaussian-blur primitive, so the renderer approximates
 * the shadow with a small stack of concentric translucent rounded
 * rectangles — visually close to a blur at typical card sizes while
 * keeping the output fully vector.
 *
 * @property color shadow colour. The alpha channel is the *total* shadow
 *   opacity at its centre — the renderer divides it across the blur
 *   layers.
 * @property offsetX horizontal displacement of the shadow.
 * @property offsetY vertical displacement; positive values drop the
 *   shadow downward, the usual elevation direction.
 * @property blur how far the shadow fades beyond the container edge.
 */
public data class DropShadow(
    val color: PdfColor = PdfColor(0f, 0f, 0f, 0.18f),
    val offsetX: Dp = Dp.Zero,
    val offsetY: Dp = Dp(2f),
    val blur: Dp = Dp(6f),
)
