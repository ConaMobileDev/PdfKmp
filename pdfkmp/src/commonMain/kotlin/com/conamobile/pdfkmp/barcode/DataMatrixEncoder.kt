package com.conamobile.pdfkmp.barcode

/**
 * An immutable square grid of Data Matrix modules.
 *
 * `true` is a dark (foreground) module, `false` light. The matrix already
 * includes the finder ("L") and timing patterns and has **no** quiet zone —
 * renderers should add the standard 1-module light border themselves.
 *
 * @property size side length in modules (the symbol size, e.g. 10..52).
 */
public class DataMatrix(
    public val size: Int,
    private val modules: BooleanArray,
) {
    /**
     * Whether the module at column [x] / row [y] is dark. Origin top-left,
     * matching the convention used throughout PdfKmp.
     *
     * @throws IndexOutOfBoundsException if [x] or [y] is outside `0 until size`.
     */
    public operator fun get(x: Int, y: Int): Boolean {
        if (x < 0 || x >= size || y < 0 || y >= size) {
            throw IndexOutOfBoundsException("($x, $y) out of bounds for size $size")
        }
        return modules[y * size + x]
    }
}

/**
 * Pure-Kotlin Data Matrix (ECC 200) encoder.
 *
 * Implements the square ECC 200 symbols from 10×10 up to 52×52 using ASCII
 * encodation, Reed-Solomon error correction over GF(256) with the Data Matrix
 * primitive polynomial 0x12D, and the standard ECC 200 symbol-character
 * placement (the "bit-walk" algorithm from ISO/IEC 16022 Annex F, including the
 * four corner special cases). The finder "L" pattern and the alternating timing
 * tracks are drawn into the mapping matrix as the module grid is assembled.
 *
 * **Scope.** ASCII encodation only — digits are still packed two-per-codeword
 * via the standard digit-pair rule (codeword `value = 10·d1 + d2 + 130`), and
 * any other byte 0..127 is encoded as `value + 1`. Bytes above 127 (extended
 * ASCII / arbitrary binary) are out of scope and rejected; callers needing
 * binary payloads should pre-encode them. Rectangular symbols and the C40 /
 * Text / X12 / EDIFACT / Base-256 modes are intentionally not implemented.
 */
public object DataMatrixEncoder {

    /**
     * One square ECC 200 symbol specification.
     *
     * @property size full symbol side in modules (including finder/timing).
     * @property dataRegion side of one square data region (excludes the 2-module
     *   finder/timing overhead per region).
     * @property regions number of data regions per side (1 for small symbols, 2
     *   for the larger ones that are split into a 2×2 block of regions).
     * @property dataCodewords number of data codewords the symbol carries.
     * @property errorCodewords number of Reed-Solomon error codewords.
     */
    private class Symbol(
        val size: Int,
        val dataRegion: Int,
        val regions: Int,
        val dataCodewords: Int,
        val errorCodewords: Int,
    ) {
        val totalCodewords: Int get() = dataCodewords + errorCodewords
    }

    /**
     * The square ECC 200 symbols, smallest first. Numbers come straight from the
     * ISO/IEC 16022 capacity table; only the square sizes are listed.
     */
    private val SYMBOLS: List<Symbol> = listOf(
        // size, dataRegion, regions, dataCw, errCw
        Symbol(10, 8, 1, 3, 5),
        Symbol(12, 10, 1, 5, 7),
        Symbol(14, 12, 1, 8, 10),
        Symbol(16, 14, 1, 12, 12),
        Symbol(18, 16, 1, 18, 14),
        Symbol(20, 18, 1, 22, 18),
        Symbol(22, 20, 1, 30, 20),
        Symbol(24, 22, 1, 36, 24),
        Symbol(26, 24, 1, 44, 28),
        Symbol(32, 14, 2, 62, 36),
        Symbol(36, 16, 2, 86, 42),
        Symbol(40, 18, 2, 114, 48),
        Symbol(44, 20, 2, 144, 56),
        Symbol(48, 22, 2, 174, 68),
        Symbol(52, 24, 2, 204, 84),
    )

    /** ASCII padding codeword (value 129) used to fill out the data region. */
    private const val PAD: Int = 129

    /**
     * Encodes [data] into a square ECC 200 Data Matrix.
     *
     * The smallest square symbol whose capacity fits the ASCII encodation of
     * [data] is selected automatically.
     *
     * @param data payload; ASCII bytes 0..127 only.
     * @throws IllegalArgumentException if [data] is empty, contains a byte above
     *   127, or is too large for the 52×52 symbol.
     */
    public fun encode(data: String): DataMatrix {
        require(data.isNotEmpty()) { "Data Matrix input must not be empty." }
        val bytes = data.encodeToByteArray()
        bytes.forEach { b ->
            require(b.toInt() and 0xFF <= 127) {
                "Data Matrix ASCII encodation supports bytes 0..127 only; found ${b.toInt() and 0xFF}."
            }
        }

        val codewords = encodeAscii(bytes)
        val symbol = chooseSymbol(codewords.size)
        val padded = padCodewords(codewords, symbol.dataCodewords)
        val full = padded + reedSolomon(padded, symbol.errorCodewords)
        return placeModules(full, symbol)
    }

    /**
     * ASCII encodation (ISO/IEC 16022 §5.2.3). Pairs of consecutive digits pack
     * into one codeword (`10·d1 + d2 + 130`); any other byte `b` becomes `b+1`.
     * Visible to tests.
     */
    internal fun encodeAscii(bytes: ByteArray): IntArray {
        val out = ArrayList<Int>(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val c = bytes[i].toInt() and 0xFF
            if (c in '0'.code..'9'.code &&
                i + 1 < bytes.size &&
                (bytes[i + 1].toInt() and 0xFF) in '0'.code..'9'.code
            ) {
                val d1 = c - '0'.code
                val d2 = (bytes[i + 1].toInt() and 0xFF) - '0'.code
                out.add(d1 * 10 + d2 + 130)
                i += 2
            } else {
                out.add(c + 1)
                i += 1
            }
        }
        return out.toIntArray()
    }

    private fun chooseSymbol(dataCount: Int): Symbol {
        // A symbol needs room for the data codewords plus at least the implicit
        // capacity; the padding step adds the unlatch/randomised pad bytes.
        for (s in SYMBOLS) if (dataCount <= s.dataCodewords) return s
        throw IllegalArgumentException(
            "Encoded data of $dataCount codewords exceeds the largest square ECC 200 symbol (52×52).",
        )
    }

    /**
     * Pads [codewords] up to [capacity] using the ECC 200 scheme: the first pad
     * is the 129 unlatch codeword, and every subsequent pad is randomised by the
     * "253-state" algorithm so long runs of identical modules are avoided.
     */
    private fun padCodewords(codewords: IntArray, capacity: Int): IntArray {
        if (codewords.size >= capacity) return codewords.copyOf(capacity)
        val out = IntArray(capacity)
        codewords.copyInto(out)
        var pos = codewords.size
        out[pos] = PAD
        pos++
        while (pos < capacity) {
            // 253-state randomising algorithm from ISO/IEC 16022 §5.2.4.2.
            val r = ((149 * (pos + 1)) % 253) + 1
            var v = PAD + r
            if (v > 254) v -= 254
            out[pos] = v
            pos++
        }
        return out
    }

    // ---------------------------------------------------------------------
    // Reed-Solomon over GF(256), Data Matrix primitive polynomial 0x12D.
    // ---------------------------------------------------------------------

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x12D
        }
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    /**
     * Generator polynomial of [degree] (coefficients high → low, leading 1).
     *
     * Data Matrix ECC 200 uses consecutive generator roots starting at α^1
     * (`(x − α¹)(x − α²)…(x − α^degree)`), unlike QR which starts at α^0 — so
     * the factor for step `i` is `α^(i+1)`.
     */
    private fun rsGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], GF_EXP[i + 1])
            }
            poly = next
        }
        return poly
    }

    /** The [ecCount] Reed-Solomon error codewords for [data]. Visible to tests. */
    internal fun reedSolomon(data: IntArray, ecCount: Int): IntArray {
        val generator = rsGenerator(ecCount)
        val remainder = IntArray(ecCount)
        for (b in data) {
            val factor = b xor remainder[0]
            for (i in 0 until ecCount - 1) remainder[i] = remainder[i + 1]
            remainder[ecCount - 1] = 0
            if (factor != 0) {
                for (i in 0 until ecCount) {
                    remainder[i] = remainder[i] xor gfMul(generator[i + 1], factor)
                }
            }
        }
        return remainder
    }

    // ---------------------------------------------------------------------
    // ECC 200 symbol-character placement (ISO/IEC 16022 Annex F) and the
    // finder / timing pattern overlay.
    // ---------------------------------------------------------------------

    private fun placeModules(codewords: IntArray, symbol: Symbol): DataMatrix {
        // Step 1: lay the codeword bits into the "mapping matrix" — the data
        // region grid WITHOUT finder/timing patterns. Its side is the symbol
        // size minus 2 modules of finder/timing per region.
        val mappingRows = symbol.dataRegion * symbol.regions
        val mappingCols = symbol.dataRegion * symbol.regions
        val grid = Array(mappingRows) { IntArray(mappingCols) { -1 } }
        ecc200Placement(grid, codewords, mappingRows, mappingCols)

        // Step 2: assemble the full symbol — copy each data region into its slot
        // and draw the surrounding finder ("L") and timing tracks.
        val full = BooleanArray(symbol.size * symbol.size)
        val regionData = symbol.dataRegion
        val regionTotal = regionData + 2 // 2 modules of finder/timing per region.
        for (ry in 0 until symbol.regions) {
            for (rx in 0 until symbol.regions) {
                val baseX = rx * regionTotal
                val baseY = ry * regionTotal
                // Finder: solid left column and solid bottom row of the region.
                for (i in 0 until regionTotal) {
                    full[(baseY + regionTotal - 1) * symbol.size + (baseX + i)] = true // bottom solid
                    full[(baseY + i) * symbol.size + baseX] = true // left solid
                }
                // Timing: alternating top row and right column of the region.
                for (i in 0 until regionTotal) {
                    full[baseY * symbol.size + (baseX + i)] = (i % 2 == 0) // top alternating, dark at even
                    full[(baseY + i) * symbol.size + (baseX + regionTotal - 1)] = (i % 2 == 1)
                }
                // Data region body: copy from the mapping matrix.
                for (dy in 0 until regionData) {
                    for (dx in 0 until regionData) {
                        val mappedX = rx * regionData + dx
                        val mappedY = ry * regionData + dy
                        val dark = grid[mappedY][mappedX] == 1
                        // Inside the region the body starts one module in from
                        // the left/top finder & timing tracks.
                        val fx = baseX + 1 + dx
                        val fy = baseY + 1 + dy
                        full[fy * symbol.size + fx] = dark
                    }
                }
            }
        }
        return DataMatrix(symbol.size, full)
    }

    /**
     * The ECC 200 default bit placement (ISO/IEC 16022 Annex F). Walks the
     * mapping matrix in the standard diagonal pattern, writing the eight bits of
     * each codeword as a utah-shaped cluster, with the four corner special
     * cases handled explicitly. Cells left `-1` after the walk are the special
     * "unfilled corner" that is forced dark per the spec.
     */
    private fun ecc200Placement(grid: Array<IntArray>, codewords: IntArray, rows: Int, cols: Int) {
        // Places one bit, wrapping coordinates and skipping already-set cells.
        fun module(rRow: Int, rCol: Int, cw: Int, bit: Int) {
            var row = rRow
            var col = rCol
            if (row < 0) { row += rows; col += 4 - ((rows + 4) % 8) }
            if (col < 0) { col += cols; row += 4 - ((cols + 4) % 8) }
            if (row >= rows) row -= rows
            grid[row][col] = (codewords.getOrElse(cw) { 0 } ushr (7 - bit)) and 1
        }

        // The standard utah-shape: eight modules of one codeword.
        fun utah(row: Int, col: Int, cw: Int) {
            module(row - 2, col - 2, cw, 0)
            module(row - 2, col - 1, cw, 1)
            module(row - 1, col - 2, cw, 2)
            module(row - 1, col - 1, cw, 3)
            module(row - 1, col, cw, 4)
            module(row, col - 2, cw, 5)
            module(row, col - 1, cw, 6)
            module(row, col, cw, 7)
        }

        fun corner1(cw: Int) {
            module(rows - 1, 0, cw, 0)
            module(rows - 1, 1, cw, 1)
            module(rows - 1, 2, cw, 2)
            module(0, cols - 2, cw, 3)
            module(0, cols - 1, cw, 4)
            module(1, cols - 1, cw, 5)
            module(2, cols - 1, cw, 6)
            module(3, cols - 1, cw, 7)
        }

        fun corner2(cw: Int) {
            module(rows - 3, 0, cw, 0)
            module(rows - 2, 0, cw, 1)
            module(rows - 1, 0, cw, 2)
            module(0, cols - 4, cw, 3)
            module(0, cols - 3, cw, 4)
            module(0, cols - 2, cw, 5)
            module(0, cols - 1, cw, 6)
            module(1, cols - 1, cw, 7)
        }

        fun corner3(cw: Int) {
            module(rows - 3, 0, cw, 0)
            module(rows - 2, 0, cw, 1)
            module(rows - 1, 0, cw, 2)
            module(0, cols - 2, cw, 3)
            module(0, cols - 1, cw, 4)
            module(1, cols - 1, cw, 5)
            module(2, cols - 1, cw, 6)
            module(3, cols - 1, cw, 7)
        }

        fun corner4(cw: Int) {
            module(rows - 1, 0, cw, 0)
            module(rows - 1, cols - 1, cw, 1)
            module(0, cols - 3, cw, 2)
            module(0, cols - 2, cw, 3)
            module(0, cols - 1, cw, 4)
            module(1, cols - 3, cw, 5)
            module(1, cols - 2, cw, 6)
            module(1, cols - 1, cw, 7)
        }

        var cw = 0
        var row = 4
        var col = 0
        do {
            // Corner special cases come first when the cursor lands on them.
            if (row == rows && col == 0) corner1(cw++)
            if (row == rows - 2 && col == 0 && cols % 4 != 0) corner2(cw++)
            if (row == rows - 2 && col == 0 && cols % 8 == 4) corner3(cw++)
            if (row == rows + 4 && col == 2 && cols % 8 == 0) corner4(cw++)

            // Sweep upward and to the right.
            do {
                if (row < rows && col >= 0 && grid[row][col] == -1) utah(row, col, cw++)
                row -= 2
                col += 2
            } while (row >= 0 && col < cols)
            row += 1
            col += 3

            // Sweep downward and to the left.
            do {
                if (row >= 0 && col < cols && grid[row][col] == -1) utah(row, col, cw++)
                row += 2
                col -= 2
            } while (row < rows && col >= 0)
            row += 3
            col += 1
        } while (row < rows || col < cols)

        // The lone unfilled corner (bottom-right of the mapping matrix) is set
        // to the fixed checkerboard the spec mandates.
        if (grid[rows - 1][cols - 1] == -1) {
            grid[rows - 1][cols - 1] = 1
            grid[rows - 2][cols - 2] = 1
            grid[rows - 1][cols - 2] = 0
            grid[rows - 2][cols - 1] = 0
        }
    }
}
