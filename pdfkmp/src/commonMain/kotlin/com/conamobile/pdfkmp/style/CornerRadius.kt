package com.conamobile.pdfkmp.style

import com.conamobile.pdfkmp.unit.Dp

/**
 * Per-corner radius spec for a container.
 *
 * Most layouts only need the simple `cornerRadius: Dp` parameter on
 * [com.conamobile.pdfkmp.dsl.column] / [com.conamobile.pdfkmp.dsl.box] /
 * etc. — that produces a uniformly rounded rectangle. Use this type for
 * the rare case where corners differ (e.g. a tab control that's rounded
 * on top and flat on the bottom).
 *
 * Pass via the `cornerRadiusEach` parameter; when set, it overrides the
 * uniform `cornerRadius` argument.
 */
public data class CornerRadius(
    val topLeft: Dp = Dp.Zero,
    val topRight: Dp = Dp.Zero,
    val bottomLeft: Dp = Dp.Zero,
    val bottomRight: Dp = Dp.Zero,
) {
    public companion object {
        public val Zero: CornerRadius = CornerRadius()

        /** Uniform shorthand — every corner shares [value]. */
        public fun all(value: Dp): CornerRadius = CornerRadius(value, value, value, value)

        /** Top-only — top corners rounded, bottom flat. Common for tabs. */
        public fun top(value: Dp): CornerRadius = CornerRadius(topLeft = value, topRight = value)

        /** Bottom-only — bottom corners rounded, top flat. */
        public fun bottom(value: Dp): CornerRadius = CornerRadius(bottomLeft = value, bottomRight = value)
    }
}

/**
 * Per-side border spec for a container.
 *
 * Pass via the `borderEach` parameter; when set, it overrides the
 * uniform `border` argument. Each side is independent — leave any
 * `null` to skip drawing it.
 */
public data class BorderSides(
    val top: BorderStroke? = null,
    val right: BorderStroke? = null,
    val bottom: BorderStroke? = null,
    val left: BorderStroke? = null,
) {
    public companion object {
        public val None: BorderSides = BorderSides()

        /** Uniform shorthand — every side shares [stroke]. */
        public fun all(stroke: BorderStroke): BorderSides =
            BorderSides(stroke, stroke, stroke, stroke)
    }
}
