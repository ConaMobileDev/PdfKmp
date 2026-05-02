package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material 3 "download" / "save" icon expressed as an inline
 * [ImageVector] for the same reason as [PdfShareIcon] — keeps the
 * library free of `compose-material-icons-extended`.
 *
 * Public so consumers can reuse the glyph in their own toolbars or
 * custom overlays alongside or instead of [PdfSaveFab].
 *
 * Path data lifted verbatim from the official Material Symbols set
 * (`download`, filled, 24dp baseline).
 */
public val PdfSaveIcon: ImageVector = ImageVector.Builder(
    name = "PdfKmpViewerSave",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(19f, 9f)
        horizontalLineToRelative(-4f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineToRelative(6f)
        horizontalLineTo(5f)
        lineToRelative(7f, 7f)
        lineToRelative(7f, -7f)
        close()
        moveTo(5f, 18f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        close()
    }
}.build()
