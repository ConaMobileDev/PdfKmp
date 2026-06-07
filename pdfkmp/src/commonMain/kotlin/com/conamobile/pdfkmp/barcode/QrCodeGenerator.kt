package com.conamobile.pdfkmp.barcode

/**
 * QR Code error-correction level, in ascending order of recoverable damage.
 *
 * Higher levels devote more of the symbol to redundancy, which means a larger
 * version (and therefore a larger matrix) is needed to hold the same payload.
 *
 * - [L] recovers ~7% of codewords,
 * - [M] ~15%,
 * - [Q] ~25%,
 * - [H] ~30%.
 */
public enum class QrErrorCorrection {
    /** Low — ~7% recovery. */
    L,

    /** Medium — ~15% recovery. The QR spec default. */
    M,

    /** Quartile — ~25% recovery. */
    Q,

    /** High — ~30% recovery. */
    H,
}

/**
 * An immutable square grid of QR modules.
 *
 * A "module" is one cell of the code; `true` is a dark (foreground) module and
 * `false` is a light (background) one. The matrix has no quiet zone — callers
 * that render it should add the standard 4-module light border themselves.
 *
 * @property size the side length in modules (always `17 + 4 * version`, so 21..177).
 */
public class QrMatrix(
    public val size: Int,
    private val modules: BooleanArray,
) {
    /**
     * Returns whether the module at [x] (column) / [y] (row) is dark.
     *
     * Coordinates are zero-based with the origin at the top-left, matching the
     * convention used throughout PdfKmp.
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
 * Pure-Kotlin QR Code (Model 2, ISO/IEC 18004) encoder.
 *
 * Implements byte-mode (UTF-8) encoding across versions 1–40 with full
 * Reed-Solomon error correction over GF(256), per-version block interleaving,
 * all eight data masks with penalty-based selection, and BCH-protected format
 * and version information. There are no platform dependencies, so the encoder
 * runs on every PdfKmp target.
 */
public object QrCodeGenerator {

    /**
     * Encodes [data] as a QR Code at the given [errorCorrection] level.
     *
     * The smallest version (1–40) whose byte-mode capacity fits the UTF-8
     * encoding of [data] at [errorCorrection] is selected automatically.
     *
     * @throws IllegalArgumentException if [data] is too large to fit version 40
     *   at the requested error-correction level.
     */
    public fun encode(
        data: String,
        errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    ): QrMatrix {
        val bytes = data.encodeToByteArray()
        val version = chooseVersion(bytes.size, errorCorrection)

        val bitBuffer = buildDataBits(bytes, version, errorCorrection)
        val codewords = bitBuffer.toCodewords(totalDataCodewords(version, errorCorrection))
        val finalSequence = interleaveWithEc(codewords, version, errorCorrection)

        return placeAndMask(finalSequence, version, errorCorrection)
    }

    // ---------------------------------------------------------------------
    // Capacity tables.
    //
    // ecBlocks[ec][version] = list of (count, totalCodewordsPerBlock,
    // dataCodewordsPerBlock) groups. The numbers come straight from the
    // ISO/IEC 18004 block-layout tables; they are duplicated here rather than
    // computed because the spec defines them by enumeration, not formula.
    // ---------------------------------------------------------------------

    /** One Reed-Solomon block specification: how many blocks, and their sizes. */
    private class BlockSpec(val count: Int, val totalCodewords: Int, val dataCodewords: Int)

    /**
     * Per-(EC level, version) block layouts. Index 0 of each inner array is a
     * placeholder so versions index naturally (`[version]` rather than
     * `[version - 1]`).
     */
    private val EC_BLOCKS: Map<QrErrorCorrection, Array<Array<IntArray>>> by lazy {
        mapOf(
            QrErrorCorrection.L to L_BLOCKS,
            QrErrorCorrection.M to M_BLOCKS,
            QrErrorCorrection.Q to Q_BLOCKS,
            QrErrorCorrection.H to H_BLOCKS,
        )
    }

    private fun blockSpecs(version: Int, ec: QrErrorCorrection): List<BlockSpec> {
        val raw = EC_BLOCKS.getValue(ec)[version]
        val specs = ArrayList<BlockSpec>(raw.size)
        for (group in raw) {
            specs.add(BlockSpec(group[0], group[1], group[2]))
        }
        return specs
    }

    private fun totalDataCodewords(version: Int, ec: QrErrorCorrection): Int {
        var total = 0
        for (spec in blockSpecs(version, ec)) total += spec.count * spec.dataCodewords
        return total
    }

    /** Byte-mode payload capacity (in bytes) accounting for mode + length headers. */
    private fun byteCapacity(version: Int, ec: QrErrorCorrection): Int {
        val dataBits = totalDataCodewords(version, ec) * 8
        // 4 mode-indicator bits + the byte-mode character-count field.
        val overheadBits = 4 + byteCountBits(version)
        return (dataBits - overheadBits) / 8
    }

    private fun chooseVersion(byteCount: Int, ec: QrErrorCorrection): Int {
        for (version in 1..40) {
            if (byteCount <= byteCapacity(version, ec)) return version
        }
        throw IllegalArgumentException(
            "Data of $byteCount bytes does not fit any QR version at EC level $ec",
        )
    }

    /** Byte-mode character-count indicator length, which widens with version. */
    private fun byteCountBits(version: Int): Int = when {
        version <= 9 -> 8
        else -> 16
    }

    // ---------------------------------------------------------------------
    // Bit stream construction.
    // ---------------------------------------------------------------------

    private class BitBuffer {
        val bits = ArrayList<Boolean>()

        fun appendBits(value: Int, length: Int) {
            // MSB first, as required by the QR bit ordering.
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1 == 1)
            }
        }

        /** Pads to a whole number of codewords, then converts to a byte array. */
        fun toCodewords(dataCodewordCount: Int): IntArray {
            val capacityBits = dataCodewordCount * 8

            // Terminator: up to four zero bits, but never past capacity.
            val terminator = minOf(4, capacityBits - bits.size)
            repeat(terminator) { bits.add(false) }

            // Pad to a byte boundary with zeros.
            while (bits.size % 8 != 0) bits.add(false)

            // Fill any remaining codewords with the alternating pad bytes.
            val padBytes = intArrayOf(0xEC, 0x11)
            var padIndex = 0
            while (bits.size < capacityBits) {
                appendBits(padBytes[padIndex], 8)
                padIndex = padIndex xor 1
            }

            val out = IntArray(dataCodewordCount)
            for (i in 0 until dataCodewordCount) {
                var b = 0
                for (j in 0 until 8) {
                    b = (b shl 1) or (if (bits[i * 8 + j]) 1 else 0)
                }
                out[i] = b
            }
            return out
        }
    }

    private fun buildDataBits(
        bytes: ByteArray,
        version: Int,
        ec: QrErrorCorrection,
    ): BitBuffer {
        val buffer = BitBuffer()
        // Mode indicator 0100 = byte mode.
        buffer.appendBits(0b0100, 4)
        buffer.appendBits(bytes.size, byteCountBits(version))
        for (b in bytes) buffer.appendBits(b.toInt() and 0xFF, 8)
        // Sanity: the caller already sized the version, so this must fit.
        require(buffer.bits.size <= totalDataCodewords(version, ec) * 8) {
            "Encoded data overflows version $version capacity"
        }
        return buffer
    }

    // ---------------------------------------------------------------------
    // Reed-Solomon over GF(256), primitive polynomial 0x11D.
    // ---------------------------------------------------------------------

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            // Reduce modulo the primitive polynomial when the degree overflows.
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        // Mirror the table so multiplication can index without modulo.
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    /** Builds the RS generator polynomial of [degree] as coefficients (high → low). */
    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            // Multiply by (x - α^i); subtraction is XOR in GF(2^n).
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], GF_EXP[i])
            }
            poly = next
        }
        return poly
    }

    /** Computes the [ecCount] error-correction codewords for one data block. */
    private fun rsEncode(data: IntArray, ecCount: Int): IntArray {
        val generator = rsGeneratorPoly(ecCount)
        // Remainder of data·x^ecCount divided by the generator polynomial.
        val remainder = IntArray(ecCount)
        for (b in data) {
            val factor = b xor remainder[0]
            // Shift the remainder left by one codeword.
            for (i in 0 until ecCount - 1) remainder[i] = remainder[i + 1]
            remainder[ecCount - 1] = 0
            if (factor != 0) {
                // generator[0] is always 1, so start the term loop at index 1.
                for (i in 0 until ecCount) {
                    remainder[i] = remainder[i] xor gfMul(generator[i + 1], factor)
                }
            }
        }
        return remainder
    }

    /**
     * Splits [dataCodewords] into blocks, appends RS codewords, then interleaves
     * data and EC codewords in the order the QR spec lays them into the matrix.
     */
    private fun interleaveWithEc(
        dataCodewords: IntArray,
        version: Int,
        ec: QrErrorCorrection,
    ): IntArray {
        val specs = blockSpecs(version, ec)

        val dataBlocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0
        for (spec in specs) {
            val ecPerBlock = spec.totalCodewords - spec.dataCodewords
            repeat(spec.count) {
                val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewords)
                offset += spec.dataCodewords
                dataBlocks.add(block)
                ecBlocks.add(rsEncode(block, ecPerBlock))
            }
        }

        val result = ArrayList<Int>()
        // Interleave data codewords column-wise across blocks.
        val maxData = dataBlocks.maxOf { it.size }
        for (i in 0 until maxData) {
            for (block in dataBlocks) {
                if (i < block.size) result.add(block[i])
            }
        }
        // Then interleave EC codewords the same way.
        val maxEc = ecBlocks.maxOf { it.size }
        for (i in 0 until maxEc) {
            for (block in ecBlocks) {
                if (i < block.size) result.add(block[i])
            }
        }
        return result.toIntArray()
    }

    // ---------------------------------------------------------------------
    // Matrix construction: function patterns, data placement, masking.
    // ---------------------------------------------------------------------

    private class Grid(val size: Int) {
        val modules = BooleanArray(size * size)
        // Tracks which cells are function patterns and must not hold data or mask.
        val reserved = BooleanArray(size * size)

        fun set(x: Int, y: Int, dark: Boolean, isFunction: Boolean = true) {
            modules[y * size + x] = dark
            if (isFunction) reserved[y * size + x] = true
        }

        fun get(x: Int, y: Int): Boolean = modules[y * size + x]
        fun isReserved(x: Int, y: Int): Boolean = reserved[y * size + x]
    }

    private fun placeAndMask(
        finalSequence: IntArray,
        version: Int,
        ec: QrErrorCorrection,
    ): QrMatrix {
        val size = 17 + 4 * version

        // Build the function-pattern skeleton once; masks reuse it.
        val skeleton = Grid(size)
        placeFinderPatterns(skeleton)
        placeSeparators(skeleton)
        placeTimingPatterns(skeleton)
        placeAlignmentPatterns(skeleton, version)
        reserveFormatAndVersionAreas(skeleton, version)
        // The lone always-dark module beside the bottom-left finder.
        skeleton.set(8, size - 8, true)

        // Lay the interleaved codeword bit stream into the free cells.
        placeData(skeleton, finalSequence)

        // Evaluate all eight masks and keep the lowest-penalty one.
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = skeleton
        for (mask in 0 until 8) {
            val candidate = copyGrid(skeleton)
            applyMask(candidate, mask)
            drawFormatInfo(candidate, ec, mask)
            if (version >= 7) drawVersionInfo(candidate, version)
            val penalty = penalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }
        // bestMask is retained for clarity; bestGrid already carries its info bits.

        return QrMatrix(size, bestGrid.modules.copyOf())
    }

    private fun copyGrid(src: Grid): Grid {
        val g = Grid(src.size)
        src.modules.copyInto(g.modules)
        src.reserved.copyInto(g.reserved)
        return g
    }

    private fun placeFinderPattern(g: Grid, originX: Int, originY: Int) {
        for (dy in 0 until 7) {
            for (dx in 0 until 7) {
                val isBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
                val isCenter = dx in 2..4 && dy in 2..4
                g.set(originX + dx, originY + dy, isBorder || isCenter)
            }
        }
    }

    private fun placeFinderPatterns(g: Grid) {
        placeFinderPattern(g, 0, 0)
        placeFinderPattern(g, g.size - 7, 0)
        placeFinderPattern(g, 0, g.size - 7)
    }

    private fun placeSeparators(g: Grid) {
        val s = g.size
        // White separator strips around each finder.
        for (i in 0 until 8) {
            // Top-left.
            g.set(i, 7, false); g.set(7, i, false)
            // Top-right.
            g.set(s - 8, i, false); g.set(s - 1 - i, 7, false)
            // Bottom-left.
            g.set(i, s - 8, false); g.set(7, s - 1 - i, false)
        }
    }

    private fun placeTimingPatterns(g: Grid) {
        // Alternating dark/light runs connecting the finders along row/col 6.
        for (i in 8 until g.size - 8) {
            val dark = i % 2 == 0
            g.set(i, 6, dark)
            g.set(6, i, dark)
        }
    }

    private fun placeAlignmentPatterns(g: Grid, version: Int) {
        if (version < 2) return
        val centers = ALIGNMENT_CENTERS[version]
        for (cy in centers) {
            for (cx in centers) {
                // Skip the three positions that collide with finder patterns.
                if (isNearFinder(g.size, cx, cy)) continue
                placeAlignmentPattern(g, cx, cy)
            }
        }
    }

    private fun isNearFinder(size: Int, cx: Int, cy: Int): Boolean {
        val topLeft = cx <= 7 && cy <= 7
        val topRight = cx >= size - 8 && cy <= 7
        val bottomLeft = cx <= 7 && cy >= size - 8
        return topLeft || topRight || bottomLeft
    }

    private fun placeAlignmentPattern(g: Grid, cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                // Dark border (ring 2) and dark center (ring 0); ring 1 is light.
                g.set(cx + dx, cy + dy, ring != 1)
            }
        }
    }

    private fun reserveFormatAndVersionAreas(g: Grid, version: Int) {
        val s = g.size
        // Format info runs around the top-left finder and mirrors near the others.
        for (i in 0..8) {
            if (!g.isReserved(i, 8)) g.set(i, 8, false)
            if (!g.isReserved(8, i)) g.set(8, i, false)
        }
        for (i in 0..7) {
            g.set(s - 1 - i, 8, false)
            g.set(8, s - 1 - i, false)
        }
        // Version info blocks (versions 7+) sit by the top-right/bottom-left finders.
        if (version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    g.set(s - 11 + j, i, false)
                    g.set(i, s - 11 + j, false)
                }
            }
        }
    }

    private fun placeData(g: Grid, sequence: IntArray) {
        val s = g.size
        var bitIndex = 0
        val totalBits = sequence.size * 8

        fun bitAt(index: Int): Boolean {
            if (index >= totalBits) return false // Remainder bits are zero.
            val codeword = sequence[index / 8]
            val bit = 7 - (index % 8)
            return (codeword ushr bit) and 1 == 1
        }

        var col = s - 1
        var upward = true
        while (col > 0) {
            // Column 6 is the vertical timing pattern; skip it entirely.
            if (col == 6) col--
            for (rowStep in 0 until s) {
                val row = if (upward) s - 1 - rowStep else rowStep
                for (c in 0..1) {
                    val x = col - c
                    if (g.isReserved(x, row)) continue
                    g.set(x, row, bitAt(bitIndex), isFunction = false)
                    bitIndex++
                }
            }
            upward = !upward
            col -= 2
        }
    }

    private fun maskCondition(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (y / 2 + x / 3) % 2 == 0
        5 -> (x * y) % 2 + (x * y) % 3 == 0
        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
        7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
        else -> false
    }

    private fun applyMask(g: Grid, mask: Int) {
        val s = g.size
        for (y in 0 until s) {
            for (x in 0 until s) {
                if (g.isReserved(x, y)) continue
                if (maskCondition(mask, x, y)) {
                    g.modules[y * s + x] = !g.modules[y * s + x]
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Format / version information with BCH error correction.
    // ---------------------------------------------------------------------

    /** Two-bit EC level field as defined by the spec (L=01, M=00, Q=11, H=10). */
    private fun ecBits(ec: QrErrorCorrection): Int = when (ec) {
        QrErrorCorrection.L -> 0b01
        QrErrorCorrection.M -> 0b00
        QrErrorCorrection.Q -> 0b11
        QrErrorCorrection.H -> 0b10
    }

    private fun drawFormatInfo(g: Grid, ec: QrErrorCorrection, mask: Int) {
        val data = (ecBits(ec) shl 3) or mask // 5-bit format data.
        val bch = bchFormat(data)
        // 15-bit format string, XOR-masked with the fixed pattern 0x5412.
        val format = ((data shl 10) or bch) xor 0x5412

        val s = g.size
        // First copy: around the top-left finder.
        for (i in 0..5) g.set(8, i, bit(format, i))
        g.set(8, 7, bit(format, 6))
        g.set(8, 8, bit(format, 7))
        g.set(7, 8, bit(format, 8))
        for (i in 9..14) g.set(14 - i, 8, bit(format, i))

        // Second copy: split across the top-right and bottom-left finders.
        for (i in 0..7) g.set(s - 1 - i, 8, bit(format, i))
        for (i in 8..14) g.set(8, s - 15 + i, bit(format, i))
    }

    private fun drawVersionInfo(g: Grid, version: Int) {
        val bch = bchVersion(version)
        val info = (version shl 12) or bch // 18-bit version string.
        val s = g.size
        for (i in 0 until 18) {
            val on = bit(info, i)
            val a = i / 3
            val b = i % 3
            // Bottom-left block.
            g.set(a, s - 11 + b, on)
            // Top-right block (transposed).
            g.set(s - 11 + b, a, on)
        }
    }

    private fun bit(value: Int, index: Int): Boolean = (value ushr index) and 1 == 1

    /** BCH(15,5) for format info using generator 0x537. */
    private fun bchFormat(data: Int): Int {
        var d = data shl 10
        val g = 0x537
        while (bitLength(d) >= 11) {
            d = d xor (g shl (bitLength(d) - 11))
        }
        return d
    }

    /** BCH(18,6) for version info using generator 0x1F25. */
    private fun bchVersion(version: Int): Int {
        var d = version shl 12
        val g = 0x1F25
        while (bitLength(d) >= 13) {
            d = d xor (g shl (bitLength(d) - 13))
        }
        return d
    }

    private fun bitLength(value: Int): Int {
        var v = value
        var len = 0
        while (v != 0) {
            len++
            v = v ushr 1
        }
        return len
    }

    // ---------------------------------------------------------------------
    // Mask penalty scoring (ISO/IEC 18004 §8.8.2).
    // ---------------------------------------------------------------------

    private fun penalty(g: Grid): Int =
        penaltyRunsAndBlocks(g) + penaltyFinderLike(g) + penaltyDarkRatio(g)

    private fun penaltyRunsAndBlocks(g: Grid): Int {
        val s = g.size
        var score = 0

        // Rule 1: runs of five-or-more same-coloured modules in a row/column.
        for (y in 0 until s) {
            var runColor = g.get(0, y)
            var run = 1
            for (x in 1 until s) {
                val c = g.get(x, y)
                if (c == runColor) {
                    run++
                } else {
                    if (run >= 5) score += 3 + (run - 5)
                    runColor = c
                    run = 1
                }
            }
            if (run >= 5) score += 3 + (run - 5)
        }
        for (x in 0 until s) {
            var runColor = g.get(x, 0)
            var run = 1
            for (y in 1 until s) {
                val c = g.get(x, y)
                if (c == runColor) {
                    run++
                } else {
                    if (run >= 5) score += 3 + (run - 5)
                    runColor = c
                    run = 1
                }
            }
            if (run >= 5) score += 3 + (run - 5)
        }

        // Rule 2: 2×2 blocks of one colour.
        for (y in 0 until s - 1) {
            for (x in 0 until s - 1) {
                val c = g.get(x, y)
                if (c == g.get(x + 1, y) && c == g.get(x, y + 1) && c == g.get(x + 1, y + 1)) {
                    score += 3
                }
            }
        }
        return score
    }

    private fun penaltyFinderLike(g: Grid): Int {
        val s = g.size
        var score = 0
        // Rule 3: the 1:1:3:1:1 finder-like pattern, with light padding either side.
        val pattern = booleanArrayOf(true, false, true, true, true, false, true)

        for (y in 0 until s) {
            for (x in 0..s - 11) {
                if (matchesFinderRow(g, x, y, pattern, horizontal = true)) score += 40
            }
        }
        for (x in 0 until s) {
            for (y in 0..s - 11) {
                if (matchesFinderRow(g, x, y, pattern, horizontal = false)) score += 40
            }
        }
        return score
    }

    private fun matchesFinderRow(
        g: Grid,
        x: Int,
        y: Int,
        pattern: BooleanArray,
        horizontal: Boolean,
    ): Boolean {
        // The core 1:1:3:1:1 pattern must be followed by 4 light modules,
        // matching the spec's "pattern preceded or followed by light area".
        for (i in pattern.indices) {
            val cell = if (horizontal) g.get(x + i, y) else g.get(x, y + i)
            if (cell != pattern[i]) return false
        }
        for (i in 7 until 11) {
            val cell = if (horizontal) g.get(x + i, y) else g.get(x, y + i)
            if (cell) return false
        }
        return true
    }

    private fun penaltyDarkRatio(g: Grid): Int {
        val s = g.size
        var dark = 0
        for (i in g.modules.indices) if (g.modules[i]) dark++
        val total = s * s
        val percent = dark * 100 / total
        // Rule 4: 10 points per 5% deviation from 50% darkness.
        val lower = (percent / 5) * 5
        val upper = lower + 5
        val a = kotlin.math.abs(lower - 50) / 5
        val b = kotlin.math.abs(upper - 50) / 5
        return minOf(a, b) * 10
    }

    // ---------------------------------------------------------------------
    // Static tables.
    // ---------------------------------------------------------------------

    /** Alignment-pattern centre coordinates per version (index 0/1 unused/empty). */
    private val ALIGNMENT_CENTERS: Array<IntArray> = arrayOf(
        intArrayOf(),                              // 0 (unused)
        intArrayOf(),                              // 1 (no alignment patterns)
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
        intArrayOf(6, 30, 54),
        intArrayOf(6, 32, 58),
        intArrayOf(6, 34, 62),
        intArrayOf(6, 26, 46, 66),
        intArrayOf(6, 26, 48, 70),
        intArrayOf(6, 26, 50, 74),
        intArrayOf(6, 30, 54, 78),
        intArrayOf(6, 30, 56, 82),
        intArrayOf(6, 30, 58, 86),
        intArrayOf(6, 34, 62, 90),
        intArrayOf(6, 28, 50, 72, 94),
        intArrayOf(6, 26, 50, 74, 98),
        intArrayOf(6, 30, 54, 78, 102),
        intArrayOf(6, 28, 54, 80, 106),
        intArrayOf(6, 32, 58, 84, 110),
        intArrayOf(6, 30, 58, 86, 114),
        intArrayOf(6, 34, 62, 90, 118),
        intArrayOf(6, 26, 50, 74, 98, 122),
        intArrayOf(6, 30, 54, 78, 102, 126),
        intArrayOf(6, 26, 52, 78, 104, 130),
        intArrayOf(6, 30, 56, 82, 108, 134),
        intArrayOf(6, 34, 60, 86, 112, 138),
        intArrayOf(6, 30, 58, 86, 114, 142),
        intArrayOf(6, 34, 62, 90, 118, 146),
        intArrayOf(6, 30, 54, 78, 102, 126, 150),
        intArrayOf(6, 24, 50, 76, 102, 128, 154),
        intArrayOf(6, 28, 54, 80, 106, 132, 158),
        intArrayOf(6, 32, 58, 84, 110, 136, 162),
        intArrayOf(6, 26, 54, 82, 110, 138, 166),
        intArrayOf(6, 30, 58, 86, 114, 142, 170),
    )

    // Block-layout tables. Each entry: arrayOf(intArrayOf(count, total, data), ...).
    // Index 0 is an empty placeholder so [version] indexes directly.

    private val L_BLOCKS: Array<Array<IntArray>> = arrayOf(
        arrayOf(),
        arrayOf(intArrayOf(1, 26, 19)),
        arrayOf(intArrayOf(1, 44, 34)),
        arrayOf(intArrayOf(1, 70, 55)),
        arrayOf(intArrayOf(1, 100, 80)),
        arrayOf(intArrayOf(1, 134, 108)),
        arrayOf(intArrayOf(2, 86, 68)),
        arrayOf(intArrayOf(2, 98, 78)),
        arrayOf(intArrayOf(2, 121, 97)),
        arrayOf(intArrayOf(2, 146, 116)),
        arrayOf(intArrayOf(2, 86, 68), intArrayOf(2, 87, 69)),
        arrayOf(intArrayOf(4, 101, 81)),
        arrayOf(intArrayOf(2, 116, 92), intArrayOf(2, 117, 93)),
        arrayOf(intArrayOf(4, 133, 107)),
        arrayOf(intArrayOf(3, 145, 115), intArrayOf(1, 146, 116)),
        arrayOf(intArrayOf(5, 109, 87), intArrayOf(1, 110, 88)),
        arrayOf(intArrayOf(5, 122, 98), intArrayOf(1, 123, 99)),
        arrayOf(intArrayOf(1, 135, 107), intArrayOf(5, 136, 108)),
        arrayOf(intArrayOf(5, 150, 120), intArrayOf(1, 151, 121)),
        arrayOf(intArrayOf(3, 141, 113), intArrayOf(4, 142, 114)),
        arrayOf(intArrayOf(3, 135, 107), intArrayOf(5, 136, 108)),
        arrayOf(intArrayOf(4, 144, 116), intArrayOf(4, 145, 117)),
        arrayOf(intArrayOf(2, 139, 111), intArrayOf(7, 140, 112)),
        arrayOf(intArrayOf(4, 151, 121), intArrayOf(5, 152, 122)),
        arrayOf(intArrayOf(6, 147, 117), intArrayOf(4, 148, 118)),
        arrayOf(intArrayOf(8, 132, 106), intArrayOf(4, 133, 107)),
        arrayOf(intArrayOf(10, 142, 114), intArrayOf(2, 143, 115)),
        arrayOf(intArrayOf(8, 152, 122), intArrayOf(4, 153, 123)),
        arrayOf(intArrayOf(3, 147, 117), intArrayOf(10, 148, 118)),
        arrayOf(intArrayOf(7, 146, 116), intArrayOf(7, 147, 117)),
        arrayOf(intArrayOf(5, 145, 115), intArrayOf(10, 146, 116)),
        arrayOf(intArrayOf(13, 145, 115), intArrayOf(3, 146, 116)),
        arrayOf(intArrayOf(17, 145, 115)),
        arrayOf(intArrayOf(17, 145, 115), intArrayOf(1, 146, 116)),
        arrayOf(intArrayOf(13, 145, 115), intArrayOf(6, 146, 116)),
        arrayOf(intArrayOf(12, 151, 121), intArrayOf(7, 152, 122)),
        arrayOf(intArrayOf(6, 151, 121), intArrayOf(14, 152, 122)),
        arrayOf(intArrayOf(17, 152, 122), intArrayOf(4, 153, 123)),
        arrayOf(intArrayOf(4, 152, 122), intArrayOf(18, 153, 123)),
        arrayOf(intArrayOf(20, 147, 117), intArrayOf(4, 148, 118)),
        arrayOf(intArrayOf(19, 148, 118), intArrayOf(6, 149, 119)),
    )

    private val M_BLOCKS: Array<Array<IntArray>> = arrayOf(
        arrayOf(),
        arrayOf(intArrayOf(1, 26, 16)),
        arrayOf(intArrayOf(1, 44, 28)),
        arrayOf(intArrayOf(1, 70, 44)),
        arrayOf(intArrayOf(2, 50, 32)),
        arrayOf(intArrayOf(2, 67, 43)),
        arrayOf(intArrayOf(4, 43, 27)),
        arrayOf(intArrayOf(4, 49, 31)),
        arrayOf(intArrayOf(2, 60, 38), intArrayOf(2, 61, 39)),
        arrayOf(intArrayOf(3, 58, 36), intArrayOf(2, 59, 37)),
        arrayOf(intArrayOf(4, 69, 43), intArrayOf(1, 70, 44)),
        arrayOf(intArrayOf(1, 80, 50), intArrayOf(4, 81, 51)),
        arrayOf(intArrayOf(6, 58, 36), intArrayOf(2, 59, 37)),
        arrayOf(intArrayOf(8, 59, 37), intArrayOf(1, 60, 38)),
        arrayOf(intArrayOf(4, 64, 40), intArrayOf(5, 65, 41)),
        arrayOf(intArrayOf(5, 65, 41), intArrayOf(5, 66, 42)),
        arrayOf(intArrayOf(7, 73, 45), intArrayOf(3, 74, 46)),
        arrayOf(intArrayOf(10, 74, 46), intArrayOf(1, 75, 47)),
        arrayOf(intArrayOf(9, 69, 43), intArrayOf(4, 70, 44)),
        arrayOf(intArrayOf(3, 70, 44), intArrayOf(11, 71, 45)),
        arrayOf(intArrayOf(3, 67, 41), intArrayOf(13, 68, 42)),
        arrayOf(intArrayOf(17, 68, 42)),
        arrayOf(intArrayOf(17, 74, 46)),
        arrayOf(intArrayOf(4, 75, 47), intArrayOf(14, 76, 48)),
        arrayOf(intArrayOf(6, 73, 45), intArrayOf(14, 74, 46)),
        arrayOf(intArrayOf(8, 75, 47), intArrayOf(13, 76, 48)),
        arrayOf(intArrayOf(19, 74, 46), intArrayOf(4, 75, 47)),
        arrayOf(intArrayOf(22, 73, 45), intArrayOf(3, 74, 46)),
        arrayOf(intArrayOf(3, 73, 45), intArrayOf(23, 74, 46)),
        arrayOf(intArrayOf(21, 73, 45), intArrayOf(7, 74, 46)),
        arrayOf(intArrayOf(19, 75, 47), intArrayOf(10, 76, 48)),
        arrayOf(intArrayOf(2, 74, 46), intArrayOf(29, 75, 47)),
        arrayOf(intArrayOf(10, 74, 46), intArrayOf(23, 75, 47)),
        arrayOf(intArrayOf(14, 74, 46), intArrayOf(21, 75, 47)),
        arrayOf(intArrayOf(14, 74, 46), intArrayOf(23, 75, 47)),
        arrayOf(intArrayOf(12, 75, 47), intArrayOf(26, 76, 48)),
        arrayOf(intArrayOf(6, 75, 47), intArrayOf(34, 76, 48)),
        arrayOf(intArrayOf(29, 74, 46), intArrayOf(14, 75, 47)),
        arrayOf(intArrayOf(13, 74, 46), intArrayOf(32, 75, 47)),
        arrayOf(intArrayOf(40, 75, 47), intArrayOf(7, 76, 48)),
        arrayOf(intArrayOf(18, 75, 47), intArrayOf(31, 76, 48)),
    )

    private val Q_BLOCKS: Array<Array<IntArray>> = arrayOf(
        arrayOf(),
        arrayOf(intArrayOf(1, 26, 13)),
        arrayOf(intArrayOf(1, 44, 22)),
        arrayOf(intArrayOf(2, 35, 17)),
        arrayOf(intArrayOf(2, 50, 24)),
        arrayOf(intArrayOf(2, 33, 15), intArrayOf(2, 34, 16)),
        arrayOf(intArrayOf(4, 43, 19)),
        arrayOf(intArrayOf(2, 32, 14), intArrayOf(4, 33, 15)),
        arrayOf(intArrayOf(4, 40, 18), intArrayOf(2, 41, 19)),
        arrayOf(intArrayOf(4, 36, 16), intArrayOf(4, 37, 17)),
        arrayOf(intArrayOf(6, 43, 19), intArrayOf(2, 44, 20)),
        arrayOf(intArrayOf(4, 50, 22), intArrayOf(4, 51, 23)),
        arrayOf(intArrayOf(4, 46, 20), intArrayOf(6, 47, 21)),
        arrayOf(intArrayOf(8, 44, 20), intArrayOf(4, 45, 21)),
        arrayOf(intArrayOf(11, 36, 16), intArrayOf(5, 37, 17)),
        arrayOf(intArrayOf(5, 54, 24), intArrayOf(7, 55, 25)),
        arrayOf(intArrayOf(15, 43, 19), intArrayOf(2, 44, 20)),
        arrayOf(intArrayOf(1, 50, 22), intArrayOf(15, 51, 23)),
        arrayOf(intArrayOf(17, 50, 22), intArrayOf(1, 51, 23)),
        arrayOf(intArrayOf(17, 47, 21), intArrayOf(4, 48, 22)),
        arrayOf(intArrayOf(15, 54, 24), intArrayOf(5, 55, 25)),
        arrayOf(intArrayOf(17, 50, 22), intArrayOf(6, 51, 23)),
        arrayOf(intArrayOf(7, 54, 24), intArrayOf(16, 55, 25)),
        arrayOf(intArrayOf(11, 54, 24), intArrayOf(14, 55, 25)),
        arrayOf(intArrayOf(11, 54, 24), intArrayOf(16, 55, 25)),
        arrayOf(intArrayOf(7, 54, 24), intArrayOf(22, 55, 25)),
        arrayOf(intArrayOf(28, 50, 22), intArrayOf(6, 51, 23)),
        arrayOf(intArrayOf(8, 53, 23), intArrayOf(26, 54, 24)),
        arrayOf(intArrayOf(4, 54, 24), intArrayOf(31, 55, 25)),
        arrayOf(intArrayOf(1, 53, 23), intArrayOf(37, 54, 24)),
        arrayOf(intArrayOf(15, 54, 24), intArrayOf(25, 55, 25)),
        arrayOf(intArrayOf(42, 54, 24), intArrayOf(1, 55, 25)),
        arrayOf(intArrayOf(10, 54, 24), intArrayOf(35, 55, 25)),
        arrayOf(intArrayOf(29, 54, 24), intArrayOf(19, 55, 25)),
        arrayOf(intArrayOf(44, 54, 24), intArrayOf(7, 55, 25)),
        arrayOf(intArrayOf(39, 54, 24), intArrayOf(14, 55, 25)),
        arrayOf(intArrayOf(46, 54, 24), intArrayOf(10, 55, 25)),
        arrayOf(intArrayOf(49, 54, 24), intArrayOf(10, 55, 25)),
        arrayOf(intArrayOf(48, 54, 24), intArrayOf(14, 55, 25)),
        arrayOf(intArrayOf(43, 54, 24), intArrayOf(22, 55, 25)),
        arrayOf(intArrayOf(34, 54, 24), intArrayOf(34, 55, 25)),
    )

    private val H_BLOCKS: Array<Array<IntArray>> = arrayOf(
        arrayOf(),
        arrayOf(intArrayOf(1, 26, 9)),
        arrayOf(intArrayOf(1, 44, 16)),
        arrayOf(intArrayOf(2, 35, 13)),
        arrayOf(intArrayOf(4, 25, 9)),
        arrayOf(intArrayOf(2, 33, 11), intArrayOf(2, 34, 12)),
        arrayOf(intArrayOf(4, 43, 15)),
        arrayOf(intArrayOf(4, 39, 13), intArrayOf(1, 40, 14)),
        arrayOf(intArrayOf(4, 40, 14), intArrayOf(2, 41, 15)),
        arrayOf(intArrayOf(4, 36, 12), intArrayOf(4, 37, 13)),
        arrayOf(intArrayOf(6, 43, 15), intArrayOf(2, 44, 16)),
        arrayOf(intArrayOf(3, 36, 12), intArrayOf(8, 37, 13)),
        arrayOf(intArrayOf(7, 42, 14), intArrayOf(4, 43, 15)),
        arrayOf(intArrayOf(12, 33, 11), intArrayOf(4, 34, 12)),
        arrayOf(intArrayOf(11, 36, 12), intArrayOf(5, 37, 13)),
        arrayOf(intArrayOf(11, 36, 12), intArrayOf(7, 37, 13)),
        arrayOf(intArrayOf(3, 45, 15), intArrayOf(13, 46, 16)),
        arrayOf(intArrayOf(2, 42, 14), intArrayOf(17, 43, 15)),
        arrayOf(intArrayOf(2, 42, 14), intArrayOf(19, 43, 15)),
        arrayOf(intArrayOf(9, 39, 13), intArrayOf(16, 40, 14)),
        arrayOf(intArrayOf(15, 43, 15), intArrayOf(10, 44, 16)),
        arrayOf(intArrayOf(19, 46, 16), intArrayOf(6, 47, 17)),
        arrayOf(intArrayOf(34, 37, 13)),
        arrayOf(intArrayOf(16, 45, 15), intArrayOf(14, 46, 16)),
        arrayOf(intArrayOf(30, 46, 16), intArrayOf(2, 47, 17)),
        arrayOf(intArrayOf(22, 45, 15), intArrayOf(13, 46, 16)),
        arrayOf(intArrayOf(33, 46, 16), intArrayOf(4, 47, 17)),
        arrayOf(intArrayOf(12, 45, 15), intArrayOf(28, 46, 16)),
        arrayOf(intArrayOf(11, 45, 15), intArrayOf(31, 46, 16)),
        arrayOf(intArrayOf(19, 45, 15), intArrayOf(26, 46, 16)),
        arrayOf(intArrayOf(23, 45, 15), intArrayOf(25, 46, 16)),
        arrayOf(intArrayOf(23, 45, 15), intArrayOf(28, 46, 16)),
        arrayOf(intArrayOf(19, 45, 15), intArrayOf(35, 46, 16)),
        arrayOf(intArrayOf(11, 45, 15), intArrayOf(46, 46, 16)),
        arrayOf(intArrayOf(59, 46, 16), intArrayOf(1, 47, 17)),
        arrayOf(intArrayOf(22, 45, 15), intArrayOf(41, 46, 16)),
        arrayOf(intArrayOf(2, 45, 15), intArrayOf(64, 46, 16)),
        arrayOf(intArrayOf(24, 45, 15), intArrayOf(46, 46, 16)),
        arrayOf(intArrayOf(42, 45, 15), intArrayOf(32, 46, 16)),
        arrayOf(intArrayOf(10, 45, 15), intArrayOf(67, 46, 16)),
        arrayOf(intArrayOf(20, 45, 15), intArrayOf(61, 46, 16)),
    )
}
