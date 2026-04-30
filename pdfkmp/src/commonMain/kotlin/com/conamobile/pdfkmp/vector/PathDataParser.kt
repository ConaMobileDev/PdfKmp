package com.conamobile.pdfkmp.vector

/**
 * Parser for the SVG / Android Vector path mini-language.
 *
 * Input is the contents of a `d="..."` (SVG) or `android:pathData="..."`
 * attribute. Output is a list of [PathCommand]s with all relative
 * coordinates resolved to absolute coordinates and all shorthands
 * (`H`, `V`, `S`, `T`) expanded to full lines / cubics / quadratics.
 *
 * Supported commands: `M m`, `L l`, `H h`, `V v`, `C c`, `S s`, `Q q`,
 * `T t`, `A a`, `Z z` — the full SVG path mini-language. Elliptical arcs
 * are converted to cubic Béziers via [ArcConverter].
 */
internal object PathDataParser {

    fun parse(data: String): List<PathCommand> {
        if (data.isBlank()) return emptyList()
        val tokens = Tokenizer(data)
        val out = mutableListOf<PathCommand>()

        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f
        var lastCubicC2x = 0f
        var lastCubicC2y = 0f
        var lastQuadCx = 0f
        var lastQuadCy = 0f
        var lastWasCubic = false
        var lastWasQuad = false
        var pendingCommand: Char? = null

        while (tokens.hasMore()) {
            val command = tokens.nextCommandOrImplicit(pendingCommand)
                ?: throw VectorParseException("Unexpected token in path data: ${tokens.preview()}")
            pendingCommand = command.implicitFollowup()
            val isRelative = command.isLowerCase()

            when (command.uppercaseChar()) {
                'M' -> {
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.MoveTo(x, y)
                    currentX = x; currentY = y
                    startX = x; startY = y
                    lastWasCubic = false; lastWasQuad = false
                    while (tokens.hasMoreNumbers()) {
                        val lx = tokens.nextFloat() + if (isRelative) currentX else 0f
                        val ly = tokens.nextFloat() + if (isRelative) currentY else 0f
                        out += PathCommand.LineTo(lx, ly)
                        currentX = lx; currentY = ly
                    }
                }
                'L' -> while (true) {
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.LineTo(x, y)
                    currentX = x; currentY = y
                    lastWasCubic = false; lastWasQuad = false
                    if (!tokens.hasMoreNumbers()) break
                }
                'H' -> while (true) {
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    out += PathCommand.LineTo(x, currentY)
                    currentX = x
                    lastWasCubic = false; lastWasQuad = false
                    if (!tokens.hasMoreNumbers()) break
                }
                'V' -> while (true) {
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.LineTo(currentX, y)
                    currentY = y
                    lastWasCubic = false; lastWasQuad = false
                    if (!tokens.hasMoreNumbers()) break
                }
                'C' -> while (true) {
                    val c1x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val c1y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    val c2x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val c2y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
                    lastCubicC2x = c2x; lastCubicC2y = c2y
                    currentX = x; currentY = y
                    lastWasCubic = true; lastWasQuad = false
                    if (!tokens.hasMoreNumbers()) break
                }
                'S' -> while (true) {
                    val c1x: Float
                    val c1y: Float
                    if (lastWasCubic) {
                        c1x = 2f * currentX - lastCubicC2x
                        c1y = 2f * currentY - lastCubicC2y
                    } else {
                        c1x = currentX; c1y = currentY
                    }
                    val c2x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val c2y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
                    lastCubicC2x = c2x; lastCubicC2y = c2y
                    currentX = x; currentY = y
                    lastWasCubic = true; lastWasQuad = false
                    if (!tokens.hasMoreNumbers()) break
                }
                'Q' -> while (true) {
                    val cx = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val cy = tokens.nextFloat() + if (isRelative) currentY else 0f
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.QuadTo(cx, cy, x, y)
                    lastQuadCx = cx; lastQuadCy = cy
                    currentX = x; currentY = y
                    lastWasCubic = false; lastWasQuad = true
                    if (!tokens.hasMoreNumbers()) break
                }
                'T' -> while (true) {
                    val cx: Float
                    val cy: Float
                    if (lastWasQuad) {
                        cx = 2f * currentX - lastQuadCx
                        cy = 2f * currentY - lastQuadCy
                    } else {
                        cx = currentX; cy = currentY
                    }
                    val x = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val y = tokens.nextFloat() + if (isRelative) currentY else 0f
                    out += PathCommand.QuadTo(cx, cy, x, y)
                    lastQuadCx = cx; lastQuadCy = cy
                    currentX = x; currentY = y
                    lastWasCubic = false; lastWasQuad = true
                    if (!tokens.hasMoreNumbers()) break
                }
                'Z' -> {
                    out += PathCommand.Close
                    currentX = startX; currentY = startY
                    lastWasCubic = false; lastWasQuad = false
                }
                'A' -> while (true) {
                    val rx = tokens.nextFloat()
                    val ry = tokens.nextFloat()
                    val xAxisRotation = tokens.nextFloat()
                    val largeArc = tokens.nextFlag()
                    val sweep = tokens.nextFlag()
                    val ex = tokens.nextFloat() + if (isRelative) currentX else 0f
                    val ey = tokens.nextFloat() + if (isRelative) currentY else 0f
                    ArcConverter.appendArc(
                        out = out,
                        x0 = currentX, y0 = currentY,
                        rx = rx, ry = ry,
                        xAxisRotationDeg = xAxisRotation,
                        largeArc = largeArc, sweep = sweep,
                        x = ex, y = ey,
                    )
                    currentX = ex; currentY = ey
                    lastWasCubic = false; lastWasQuad = false
                    if (!tokens.hasMoreNumbers()) break
                }
                else -> throw VectorParseException("Unknown path command '$command'")
            }
        }

        return out
    }

    /** Path commands of the form `M`, `L`, `C` etc. trigger an implicit follow-up
     *  command when more coordinates appear without a fresh letter. The implicit
     *  command is `L` after `M` and the same letter after every other command. */
    private fun Char.implicitFollowup(): Char = when (this.uppercaseChar()) {
        'M' -> if (isLowerCase()) 'l' else 'L'
        'Z' -> this // unused, but keep type happy
        else -> this
    }

    /** Hand-rolled tokenizer for path data. */
    private class Tokenizer(private val data: String) {
        private var index = 0

        fun hasMore(): Boolean {
            skipSeparators()
            return index < data.length
        }

        fun preview(): String {
            val end = (index + 12).coerceAtMost(data.length)
            return "'${data.substring(index, end)}'"
        }

        fun nextCommandOrImplicit(pending: Char?): Char? {
            skipSeparators()
            if (index >= data.length) return null
            val c = data[index]
            if (c.isLetter()) {
                index++
                return c
            }
            return pending
        }

        fun hasMoreNumbers(): Boolean {
            skipSeparators()
            if (index >= data.length) return false
            val c = data[index]
            return c == '+' || c == '-' || c == '.' || c.isDigit()
        }

        /**
         * Reads the single-digit flag (0 or 1) used by SVG arc commands.
         *
         * SVG specifies that arc flags are unprefixed single digits — so
         * `0,0` is two flags rather than one number. We honour this by
         * consuming exactly one digit and rejecting anything else.
         */
        fun nextFlag(): Boolean {
            skipSeparators()
            if (index >= data.length) {
                throw VectorParseException("Expected arc flag at position $index")
            }
            val c = data[index]
            if (c != '0' && c != '1') {
                throw VectorParseException("Arc flag must be '0' or '1' at position $index, got '$c'")
            }
            index++
            return c == '1'
        }

        fun nextFloat(): Float {
            skipSeparators()
            val start = index
            if (index < data.length && (data[index] == '+' || data[index] == '-')) index++
            var sawDigit = false
            while (index < data.length && data[index].isDigit()) {
                index++; sawDigit = true
            }
            if (index < data.length && data[index] == '.') {
                index++
                while (index < data.length && data[index].isDigit()) {
                    index++; sawDigit = true
                }
            }
            if (index < data.length && (data[index] == 'e' || data[index] == 'E')) {
                index++
                if (index < data.length && (data[index] == '+' || data[index] == '-')) index++
                while (index < data.length && data[index].isDigit()) index++
            }
            if (!sawDigit) {
                throw VectorParseException("Expected number at position $start in path data")
            }
            return data.substring(start, index).toFloat()
        }

        private fun skipSeparators() {
            while (index < data.length) {
                val c = data[index]
                if (c.isWhitespace() || c == ',') index++ else break
            }
        }
    }
}
