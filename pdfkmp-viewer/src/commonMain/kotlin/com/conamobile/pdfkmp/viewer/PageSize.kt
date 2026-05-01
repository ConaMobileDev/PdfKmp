package com.conamobile.pdfkmp.viewer

/**
 * Intrinsic dimensions of a PDF page in PDF points (1 pt = 1/72 in).
 *
 * The viewer uses [aspectRatio] to reserve the correct slot for each
 * page in the [androidx.compose.foundation.lazy.LazyColumn] before its
 * bitmap finishes rendering — this is what keeps scrolling smooth on
 * long documents (no layout jumps as pages stream in).
 */
internal data class PageSize(
    val widthPoints: Float,
    val heightPoints: Float,
) {
    /** Width / height. Defaults to `1f` when the page reports a zero height. */
    val aspectRatio: Float = if (heightPoints > 0f) widthPoints / heightPoints else 1f
}
