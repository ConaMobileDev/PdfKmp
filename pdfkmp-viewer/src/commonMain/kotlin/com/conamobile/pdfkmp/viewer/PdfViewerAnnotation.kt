package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.Color

/**
 * One in-viewer highlight annotation, expressed in the same coordinate
 * system as [com.conamobile.pdfkmp.text.PdfTextRun] and
 * [PdfSearchHighlight] — PDF points, top-left origin, Y growing
 * downward — so a highlight scales with pinch zoom exactly like a
 * search-match rectangle does.
 *
 * **Honest scope:** these annotations are an **overlay only**. They are
 * held in viewer state and painted on top of the rasterised page; they
 * are **not** written back into the encoded PDF bytes. The bytes handed
 * to share / save / print are untouched, so a highlight is not visible
 * if the same PDF is opened in another reader. Persist them yourself via
 * [KmpPdfViewer]'s `onAnnotationsChanged` callback (serialise the list)
 * and restore through `initialAnnotations` — see that composable's KDoc.
 *
 * @property pageIndex zero-based page the annotation belongs to.
 * @property x left edge of the highlight box, in PDF points.
 * @property y top edge of the highlight box, in PDF points.
 * @property width width of the highlight box, in PDF points.
 * @property height height of the highlight box, in PDF points.
 * @property color fill colour painted over the page. Defaults to the
 *   translucent yellow [DefaultHighlightColor], matching the look of a
 *   physical highlighter pen and the in-document search highlights.
 */
public data class PdfViewerAnnotation(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val color: Color = DefaultHighlightColor,
) {
    public companion object {
        /**
         * Translucent amber, the resting fill for both the in-viewer
         * highlight tool and the search-match overlay — a deliberately
         * low alpha so the underlying glyphs stay legible through the
         * paint.
         */
        public val DefaultHighlightColor: Color = Color(0x66FFE57F)
    }
}

/**
 * Returns the index of the **topmost** annotation in [annotations] whose
 * box contains the point (`xPoints`, `yPoints`) on page [pageIndex], or
 * `-1` if the tap missed every annotation on that page.
 *
 * "Topmost" means the **last** matching annotation in list order — the
 * viewer paints annotations in list order, so the last one drawn sits
 * visually on top and should be the one a tap deletes. All coordinates
 * are in PDF points (top-left origin, Y down), the same space the
 * annotations are stored in, so the caller maps a screen tap into page
 * points before calling this.
 *
 * Pure logic with no Compose dependency so it can be unit-tested on the
 * JVM without a UI harness.
 */
public fun hitTestAnnotation(
    annotations: List<PdfViewerAnnotation>,
    pageIndex: Int,
    xPoints: Float,
    yPoints: Float,
): Int {
    var hit = -1
    annotations.forEachIndexed { index, a ->
        if (a.pageIndex != pageIndex) return@forEachIndexed
        val insideX = xPoints >= a.x && xPoints <= a.x + a.width
        val insideY = yPoints >= a.y && yPoints <= a.y + a.height
        // Keep scanning so a later (visually-on-top) annotation wins when
        // two overlap under the same tap.
        if (insideX && insideY) hit = index
    }
    return hit
}

/**
 * Builds a normalised [PdfViewerAnnotation] from a drag in page-point
 * space, given the two opposite corners (`startX`/`startY` →
 * `endX`/`endY`). The corners are normalised so a drag in any direction
 * (up-left, down-right, …) yields a box with non-negative width/height,
 * and the result is clamped to the page bounds (`pageWidth` ×
 * `pageHeight`) so a highlight can never spill outside the page.
 *
 * Returns `null` when the resulting box is degenerate (zero-area after
 * clamping) — e.g. a tap with no drag — so the caller can treat that as
 * "not a highlight" and fall through to hit-testing instead.
 *
 * Pure logic (no Compose dependency) so it is unit-testable on the JVM.
 */
public fun buildAnnotationFromDrag(
    pageIndex: Int,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    pageWidth: Float,
    pageHeight: Float,
    color: Color = PdfViewerAnnotation.DefaultHighlightColor,
): PdfViewerAnnotation? {
    val left = minOf(startX, endX).coerceIn(0f, pageWidth)
    val top = minOf(startY, endY).coerceIn(0f, pageHeight)
    val right = maxOf(startX, endX).coerceIn(0f, pageWidth)
    val bottom = maxOf(startY, endY).coerceIn(0f, pageHeight)
    val width = right - left
    val height = bottom - top
    if (width <= 0f || height <= 0f) return null
    return PdfViewerAnnotation(
        pageIndex = pageIndex,
        x = left,
        y = top,
        width = width,
        height = height,
        color = color,
    )
}
