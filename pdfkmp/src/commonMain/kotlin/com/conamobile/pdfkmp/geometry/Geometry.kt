package com.conamobile.pdfkmp.geometry

import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.unit.dp

/**
 * Page dimensions. Common ISO sizes are exposed as constants; use [custom] for
 * arbitrary sizes.
 */
public data class PageSize(
    val width: Dp,
    val height: Dp,
) {
    public companion object {
        public val A4: PageSize = PageSize(595.dp, 842.dp)
        public val A5: PageSize = PageSize(420.dp, 595.dp)
        public val A3: PageSize = PageSize(842.dp, 1191.dp)
        public val Letter: PageSize = PageSize(612.dp, 792.dp)
        public val Legal: PageSize = PageSize(612.dp, 1008.dp)

        public fun custom(width: Dp, height: Dp): PageSize = PageSize(width, height)
    }
}

/**
 * Insets applied around content.
 */
public data class Padding(
    val left: Dp = Dp.Zero,
    val top: Dp = Dp.Zero,
    val right: Dp = Dp.Zero,
    val bottom: Dp = Dp.Zero,
) {
    public companion object {
        public val Zero: Padding = Padding()

        /**
         * Sensible default page padding for printed-document layouts: 40 PDF
         * points (~14 mm / ~0.55 inch) on every side. Matches the convention
         * used by word processors when no margin is configured.
         *
         * Used as the default for [com.conamobile.pdfkmp.dsl.DocumentScope.defaultPagePadding]
         * — override it document-wide there or per-page on the page builder.
         */
        public val Default: Padding = Padding(40.dp, 40.dp, 40.dp, 40.dp)

        public fun all(value: Dp): Padding = Padding(value, value, value, value)

        public fun symmetric(horizontal: Dp = Dp.Zero, vertical: Dp = Dp.Zero): Padding =
            Padding(horizontal, vertical, horizontal, vertical)
    }
}

/**
 * Layout constraint passed down during measurement. A value of
 * [Float.POSITIVE_INFINITY] means unconstrained in that dimension.
 */
public data class Constraints(
    val maxWidth: Float,
    val maxHeight: Float = Float.POSITIVE_INFINITY,
)

/**
 * Result size of a measured node, in PDF points.
 */
public data class Size(
    val width: Float,
    val height: Float,
)
