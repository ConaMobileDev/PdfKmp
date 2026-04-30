package com.conamobile.pdfkmp.vector

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 3×3 affine transformation in homogeneous coordinates, used to apply SVG
 * `transform="..."` attributes to vector paths.
 *
 * The matrix is stored row-major:
 * ```
 * [ scaleX   skewX   translateX ]
 * [ skewY   scaleY   translateY ]
 * [   0       0          1      ]
 * ```
 *
 * Operations [translate], [scale], [rotate], [skewX], [skewY] return a new
 * matrix without mutating the receiver — fits the Kotlin pattern of
 * immutable value types and makes composition naturally chainable.
 */
internal data class AffineTransform(
    val scaleX: Float,
    val skewX: Float,
    val translateX: Float,
    val skewY: Float,
    val scaleY: Float,
    val translateY: Float,
) {

    /** Returns `this × other` — applying [other] first, then this transform. */
    fun multiply(other: AffineTransform): AffineTransform = AffineTransform(
        scaleX = scaleX * other.scaleX + skewX * other.skewY,
        skewX = scaleX * other.skewX + skewX * other.scaleY,
        translateX = scaleX * other.translateX + skewX * other.translateY + translateX,
        skewY = skewY * other.scaleX + scaleY * other.skewY,
        scaleY = skewY * other.skewX + scaleY * other.scaleY,
        translateY = skewY * other.translateX + scaleY * other.translateY + translateY,
    )

    /** Maps the point `(x, y)` through this transform. */
    fun mapX(x: Float, y: Float): Float = scaleX * x + skewX * y + translateX

    fun mapY(x: Float, y: Float): Float = skewY * x + scaleY * y + translateY

    companion object {
        val Identity: AffineTransform = AffineTransform(
            scaleX = 1f, skewX = 0f, translateX = 0f,
            skewY = 0f, scaleY = 1f, translateY = 0f,
        )

        fun translate(tx: Float, ty: Float = 0f): AffineTransform =
            AffineTransform(1f, 0f, tx, 0f, 1f, ty)

        fun scale(sx: Float, sy: Float = sx): AffineTransform =
            AffineTransform(sx, 0f, 0f, 0f, sy, 0f)

        fun rotate(angleDegrees: Float, pivotX: Float = 0f, pivotY: Float = 0f): AffineTransform {
            val theta = angleDegrees * PI / 180.0
            val c = cos(theta).toFloat()
            val s = sin(theta).toFloat()
            val rotation = AffineTransform(c, -s, 0f, s, c, 0f)
            if (pivotX == 0f && pivotY == 0f) return rotation
            return translate(pivotX, pivotY)
                .multiply(rotation)
                .multiply(translate(-pivotX, -pivotY))
        }

        fun skewX(angleDegrees: Float): AffineTransform {
            val t = tan(angleDegrees * PI / 180.0).toFloat()
            return AffineTransform(1f, t, 0f, 0f, 1f, 0f)
        }

        fun skewY(angleDegrees: Float): AffineTransform {
            val t = tan(angleDegrees * PI / 180.0).toFloat()
            return AffineTransform(1f, 0f, 0f, t, 1f, 0f)
        }

        fun fromMatrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): AffineTransform =
            AffineTransform(a, c, e, b, d, f)
    }
}

/**
 * Parser for SVG / Android `transform="..."` strings.
 *
 * Honours the standard SVG transform functions: `translate`, `scale`,
 * `rotate`, `skewX`, `skewY`, `matrix`. Functions are composed left-to-
 * right, matching the SVG specification (`translate(10) scale(2)` first
 * translates and then scales the translated coordinates).
 *
 * Argument syntax accepts either commas or whitespace as separators and
 * allows the second argument of `translate` and `scale` to be omitted (in
 * which case it defaults to `0` for `translate` and the first argument
 * for `scale`).
 */
internal object TransformParser {

    fun parse(value: String?): AffineTransform {
        if (value.isNullOrBlank()) return AffineTransform.Identity
        var current = AffineTransform.Identity
        var index = 0
        while (index < value.length) {
            while (index < value.length && (value[index].isWhitespace() || value[index] == ',')) index++
            if (index >= value.length) break
            val nameStart = index
            while (index < value.length && value[index].isLetter()) index++
            if (index == nameStart) {
                throw VectorParseException("Expected transform function name at position $nameStart")
            }
            val name = value.substring(nameStart, index)
            while (index < value.length && value[index].isWhitespace()) index++
            if (index >= value.length || value[index] != '(') {
                throw VectorParseException("Expected '(' after '$name' at position $index")
            }
            index++ // consume '('
            val argsStart = index
            while (index < value.length && value[index] != ')') index++
            if (index >= value.length) throw VectorParseException("Unterminated $name(...)")
            val args = value.substring(argsStart, index).split(Regex("[\\s,]+"))
                .filter { it.isNotEmpty() }
                .map { it.toFloatOrNull() ?: throw VectorParseException("Invalid number in $name(): $it") }
            index++ // consume ')'

            val transform = when (name) {
                "translate" -> when (args.size) {
                    1 -> AffineTransform.translate(args[0], 0f)
                    2 -> AffineTransform.translate(args[0], args[1])
                    else -> throw VectorParseException("translate() expects 1 or 2 args, got ${args.size}")
                }
                "scale" -> when (args.size) {
                    1 -> AffineTransform.scale(args[0])
                    2 -> AffineTransform.scale(args[0], args[1])
                    else -> throw VectorParseException("scale() expects 1 or 2 args, got ${args.size}")
                }
                "rotate" -> when (args.size) {
                    1 -> AffineTransform.rotate(args[0])
                    3 -> AffineTransform.rotate(args[0], args[1], args[2])
                    else -> throw VectorParseException("rotate() expects 1 or 3 args, got ${args.size}")
                }
                "skewX" -> when (args.size) {
                    1 -> AffineTransform.skewX(args[0])
                    else -> throw VectorParseException("skewX() expects 1 arg")
                }
                "skewY" -> when (args.size) {
                    1 -> AffineTransform.skewY(args[0])
                    else -> throw VectorParseException("skewY() expects 1 arg")
                }
                "matrix" -> when (args.size) {
                    6 -> AffineTransform.fromMatrix(args[0], args[1], args[2], args[3], args[4], args[5])
                    else -> throw VectorParseException("matrix() expects 6 args")
                }
                else -> throw VectorParseException("Unknown transform function: $name")
            }
            current = current.multiply(transform)
        }
        return current
    }
}

/** Applies [transform] to every coordinate carried by [command]. */
internal fun applyTransform(command: PathCommand, transform: AffineTransform): PathCommand {
    if (transform == AffineTransform.Identity) return command
    return when (command) {
        is PathCommand.MoveTo -> PathCommand.MoveTo(
            transform.mapX(command.x, command.y),
            transform.mapY(command.x, command.y),
        )
        is PathCommand.LineTo -> PathCommand.LineTo(
            transform.mapX(command.x, command.y),
            transform.mapY(command.x, command.y),
        )
        is PathCommand.CubicTo -> PathCommand.CubicTo(
            c1x = transform.mapX(command.c1x, command.c1y),
            c1y = transform.mapY(command.c1x, command.c1y),
            c2x = transform.mapX(command.c2x, command.c2y),
            c2y = transform.mapY(command.c2x, command.c2y),
            x = transform.mapX(command.x, command.y),
            y = transform.mapY(command.x, command.y),
        )
        is PathCommand.QuadTo -> PathCommand.QuadTo(
            cx = transform.mapX(command.cx, command.cy),
            cy = transform.mapY(command.cx, command.cy),
            x = transform.mapX(command.x, command.y),
            y = transform.mapY(command.x, command.y),
        )
        PathCommand.Close -> command
    }
}
