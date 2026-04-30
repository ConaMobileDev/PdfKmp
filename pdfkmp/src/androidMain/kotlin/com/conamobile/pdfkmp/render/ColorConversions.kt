package com.conamobile.pdfkmp.render

import android.graphics.Color
import com.conamobile.pdfkmp.style.PdfColor

/**
 * Converts a [PdfColor] (RGBA in `0f..1f`) to the packed `0xAARRGGBB` integer
 * form expected by Android's [Color] / [android.graphics.Paint] APIs.
 *
 * Components are clamped to `0f..1f` before conversion; the rounding rule is
 * `(value * 255)` truncated to int — fast and consistent with how Compose and
 * the platform itself round.
 */
internal fun PdfColor.toArgb(): Int = Color.argb(
    (alpha.coerceIn(0f, 1f) * 255).toInt(),
    (red.coerceIn(0f, 1f) * 255).toInt(),
    (green.coerceIn(0f, 1f) * 255).toInt(),
    (blue.coerceIn(0f, 1f) * 255).toInt(),
)
