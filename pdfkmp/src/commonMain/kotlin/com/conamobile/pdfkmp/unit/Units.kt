package com.conamobile.pdfkmp.unit

import kotlin.jvm.JvmInline

/**
 * Density-independent pixel.
 *
 * In PdfKmp `1.dp == 1 PDF point == 1/72 inch`. This differs from Android
 * Compose, where dp depends on screen density — PDF is a fixed-resolution
 * medium, so we anchor dp to the native PDF unit. Page sizes, padding, and
 * geometry are all expressed in [Dp].
 */
@JvmInline
public value class Dp(public val value: Float) {
    public operator fun plus(other: Dp): Dp = Dp(value + other.value)
    public operator fun minus(other: Dp): Dp = Dp(value - other.value)
    public operator fun times(scalar: Float): Dp = Dp(value * scalar)
    public operator fun times(scalar: Int): Dp = Dp(value * scalar)
    public operator fun div(scalar: Float): Dp = Dp(value / scalar)
    public operator fun unaryMinus(): Dp = Dp(-value)
    public operator fun compareTo(other: Dp): Int = value.compareTo(other.value)

    public companion object {
        public val Zero: Dp = Dp(0f)
    }
}

/**
 * Scale-independent pixel, used for font sizes.
 *
 * In PdfKmp `1.sp == 1 PDF point` (same as [Dp]). The separate type exists so
 * font size APIs read naturally and so we can introduce real scaling later
 * (e.g. user-controlled accessibility scaling) without an API break.
 */
@JvmInline
public value class Sp(public val value: Float) {
    public operator fun plus(other: Sp): Sp = Sp(value + other.value)
    public operator fun times(scalar: Float): Sp = Sp(value * scalar)
    public operator fun compareTo(other: Sp): Int = value.compareTo(other.value)

    public companion object {
        public val Zero: Sp = Sp(0f)
    }
}

public val Int.dp: Dp get() = Dp(this.toFloat())
public val Float.dp: Dp get() = Dp(this)
public val Double.dp: Dp get() = Dp(this.toFloat())

public val Int.sp: Sp get() = Sp(this.toFloat())
public val Float.sp: Sp get() = Sp(this)
public val Double.sp: Sp get() = Sp(this.toFloat())
