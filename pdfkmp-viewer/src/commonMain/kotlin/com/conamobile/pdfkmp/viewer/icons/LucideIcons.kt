package com.conamobile.pdfkmp.viewer.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Inline Lucide-style icons used by the bundled [com.conamobile.pdfkmp.viewer.PdfViewerTopBar]
 * variants.
 *
 * The library deliberately avoids a dependency on
 * `compose-material-icons-extended` (or any Lucide / SF-Symbols
 * runtime) — the icons here are 24×24 viewBox, stroke 2, and the
 * `fill = transparent + stroke = SolidColor(black)` recipe matches
 * Lucide's outlined drawing convention. Tint via the consuming
 * `Icon(tint = …)` parameter.
 */

/** `arrow-left` — Minimal Mono back chip. */
public val LucideArrowLeftIcon: ImageVector = lucide("LucideArrowLeft") {
    moveTo(19f, 12f)
    horizontalLineTo(5f)
    moveTo(12f, 19f)
    lineTo(5f, 12f)
    lineTo(12f, 5f)
}

/** `chevron-left` — Classic iOS native back glyph. */
public val LucideChevronLeftIcon: ImageVector = lucide(
    name = "LucideChevronLeft",
    strokeWidth = 2.4f,
) {
    moveTo(15f, 18f)
    lineTo(9f, 12f)
    lineTo(15f, 6f)
}

/** `search` — magnifying glass for both topbar variants. */
public val LucideSearchIcon: ImageVector = lucide("LucideSearch") {
    // Circle (cx=11, cy=11, r=8) approximated with cubic curves.
    moveTo(11f, 3f)
    curveToRelative(4.418f, 0f, 8f, 3.582f, 8f, 8f)
    reflectiveCurveToRelative(-3.582f, 8f, -8f, 8f)
    reflectiveCurveToRelative(-8f, -3.582f, -8f, -8f)
    reflectiveCurveToRelative(3.582f, -8f, 8f, -8f)
    close()
    // Tail line from (16.65, 16.65) to (21, 21).
    moveTo(21f, 21f)
    lineTo(16.65f, 16.65f)
}

/** `download` — Lucide outlined version (sharper than the filled [com.conamobile.pdfkmp.viewer.PdfSaveIcon]). */
public val LucideDownloadIcon: ImageVector = lucide("LucideDownload") {
    // Tray base.
    moveTo(21f, 15f)
    verticalLineToRelative(4f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
    horizontalLineTo(5f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
    verticalLineToRelative(-4f)
    // Arrow shaft.
    moveTo(7f, 10f)
    lineToRelative(5f, 5f)
    lineToRelative(5f, -5f)
    // Arrow stem.
    moveTo(12f, 15f)
    verticalLineTo(3f)
}

/** `share-2` — Lucide outlined node graph variant of share. */
public val LucideShareIcon: ImageVector = lucide("LucideShare") {
    // Top right node (circle approx, r=3, cx=18, cy=5).
    moveTo(18f, 2f)
    curveToRelative(1.657f, 0f, 3f, 1.343f, 3f, 3f)
    reflectiveCurveToRelative(-1.343f, 3f, -3f, 3f)
    reflectiveCurveToRelative(-3f, -1.343f, -3f, -3f)
    reflectiveCurveToRelative(1.343f, -3f, 3f, -3f)
    close()
    // Bottom right node (cx=18, cy=19).
    moveTo(18f, 16f)
    curveToRelative(1.657f, 0f, 3f, 1.343f, 3f, 3f)
    reflectiveCurveToRelative(-1.343f, 3f, -3f, 3f)
    reflectiveCurveToRelative(-3f, -1.343f, -3f, -3f)
    reflectiveCurveToRelative(1.343f, -3f, 3f, -3f)
    close()
    // Left node (cx=6, cy=12).
    moveTo(6f, 9f)
    curveToRelative(1.657f, 0f, 3f, 1.343f, 3f, 3f)
    reflectiveCurveToRelative(-1.343f, 3f, -3f, 3f)
    reflectiveCurveToRelative(-3f, -1.343f, -3f, -3f)
    reflectiveCurveToRelative(1.343f, -3f, 3f, -3f)
    close()
    // Connecting lines.
    moveTo(8.59f, 13.51f)
    lineTo(15.42f, 17.49f)
    moveTo(15.41f, 6.51f)
    lineTo(8.59f, 10.49f)
}

private inline fun lucide(
    name: String,
    strokeWidth: Float = 2f,
    crossinline body: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        // Lucide is stroke-only — `fill` stays null, `stroke` carries the
        // glyph. SolidColor(Black) is the conventional default; consuming
        // `Icon` composables paint with the real tint colour.
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero,
        pathBuilder = body,
    )
}.build()
