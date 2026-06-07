package com.conamobile.pdfkmp.barcode

/**
 * The bar/space module widths of an encoded Code 128 symbol, in drawing order.
 *
 * The list always starts with a bar and strictly alternates bar, space, bar,
 * space, ... A renderer walks [modules] left to right, drawing a filled bar for
 * even indices and leaving a gap (space) for odd indices, each [n] modules wide.
 *
 * Each value is 1..4 modules wide. Every standard symbol (start code, data,
 * checksum) is 11 modules (3 bars + 3 spaces); the trailing stop pattern is 13
 * modules (4 bars + 3 spaces) because it carries the terminating bar.
 *
 * @property modules ordered bar/space widths, first element is a bar.
 */
public class Code128Barcode internal constructor(
    public val modules: List<Int>,
) {
    /** Total width of the symbol in modules — the sum of every entry in [modules]. */
    public val totalModules: Int = modules.sum()
}

/**
 * Pure-Kotlin Code 128 barcode encoder (code sets B and C).
 *
 * Encodes a string into the module-width pattern of a Code 128 symbol, including
 * the start code, weighted mod-103 checksum and stop pattern. Subset selection is
 * automatic: digit runs of four or more are packed two-per-symbol in code set C
 * (the standard density optimisation) while everything else uses code set B.
 *
 * **Scope.** Only the printable ASCII range (32..126) is supported. Code set A
 * (control characters, ASCII 0..31) and the special function characters (FNC1-4)
 * are intentionally out of scope; any character outside 32..126 — including an
 * empty input, which cannot produce a valid symbol — is rejected with
 * [IllegalArgumentException].
 */
public object Code128Encoder {

    /**
     * Symbol value of `Start Code B` (code set B selects printable ASCII, one
     * character per symbol).
     */
    private const val START_B: Int = 104

    /**
     * Symbol value of `Start Code C` (code set C packs two decimal digits per
     * symbol for higher density).
     */
    private const val START_C: Int = 105

    /** Symbol value of the in-band `Code A` shift — unused here but kept for table parity. */
    @Suppress("unused")
    private const val CODE_A: Int = 101

    /** Symbol value that switches the running code set to B mid-symbol. */
    private const val CODE_B: Int = 100

    /** Symbol value that switches the running code set to C mid-symbol. */
    private const val CODE_C: Int = 99

    /** Symbol value of the stop pattern that terminates every Code 128 symbol. */
    private const val STOP: Int = 106

    /**
     * Lowest ASCII code understood by code set B. Code set B value `v` maps to
     * ASCII `v + 32`, so subtracting this offset converts a printable character
     * back to its symbol value.
     */
    private const val ASCII_OFFSET: Int = 32

    /** Inclusive upper bound of the supported printable-ASCII range. */
    private const val ASCII_MAX: Int = 126

    /**
     * A digit run shorter than this stays in code set B: switching to C costs a
     * `Code C` symbol, so it only pays off once a run can be packed densely
     * enough to come out ahead. Four is the threshold the Code 128 standard
     * recommends for interior runs.
     */
    private const val MIN_C_RUN: Int = 4

    /**
     * The Code 128 pattern table: symbol value (index 0..106) to its bar/space
     * widths. Entries 0..105 are 11-module symbols (six widths summing to 11);
     * entry 106 is the 13-module stop pattern (the only seven-width entry). The
     * encoding of a value is identical across code sets A/B/C — only the meaning
     * of the value differs.
     */
    private val PATTERNS: Array<IntArray> = arrayOf(
        intArrayOf(2, 1, 2, 2, 2, 2), // 0
        intArrayOf(2, 2, 2, 1, 2, 2), // 1
        intArrayOf(2, 2, 2, 2, 2, 1), // 2
        intArrayOf(1, 2, 1, 2, 2, 3), // 3
        intArrayOf(1, 2, 1, 3, 2, 2), // 4
        intArrayOf(1, 3, 1, 2, 2, 2), // 5
        intArrayOf(1, 2, 2, 2, 1, 3), // 6
        intArrayOf(1, 2, 2, 3, 1, 2), // 7
        intArrayOf(1, 3, 2, 2, 1, 2), // 8
        intArrayOf(2, 2, 1, 2, 1, 3), // 9
        intArrayOf(2, 2, 1, 3, 1, 2), // 10
        intArrayOf(2, 3, 1, 2, 1, 2), // 11
        intArrayOf(1, 1, 2, 2, 3, 2), // 12
        intArrayOf(1, 2, 2, 1, 3, 2), // 13
        intArrayOf(1, 2, 2, 2, 3, 1), // 14
        intArrayOf(1, 1, 3, 2, 2, 2), // 15
        intArrayOf(1, 2, 3, 1, 2, 2), // 16
        intArrayOf(1, 2, 3, 2, 2, 1), // 17
        intArrayOf(2, 2, 3, 2, 1, 1), // 18
        intArrayOf(2, 2, 1, 1, 3, 2), // 19
        intArrayOf(2, 2, 1, 2, 3, 1), // 20
        intArrayOf(2, 1, 3, 2, 1, 2), // 21
        intArrayOf(2, 2, 3, 1, 1, 2), // 22
        intArrayOf(3, 1, 2, 1, 3, 1), // 23
        intArrayOf(3, 1, 1, 2, 2, 2), // 24
        intArrayOf(3, 2, 1, 1, 2, 2), // 25
        intArrayOf(3, 2, 1, 2, 2, 1), // 26
        intArrayOf(3, 1, 2, 2, 1, 2), // 27
        intArrayOf(3, 2, 2, 1, 1, 2), // 28
        intArrayOf(3, 2, 2, 2, 1, 1), // 29
        intArrayOf(2, 1, 2, 1, 2, 3), // 30
        intArrayOf(2, 1, 2, 3, 2, 1), // 31
        intArrayOf(2, 3, 2, 1, 2, 1), // 32
        intArrayOf(1, 1, 1, 3, 2, 3), // 33
        intArrayOf(1, 3, 1, 1, 2, 3), // 34
        intArrayOf(1, 3, 1, 3, 2, 1), // 35
        intArrayOf(1, 1, 2, 3, 1, 3), // 36
        intArrayOf(1, 3, 2, 1, 1, 3), // 37
        intArrayOf(1, 3, 2, 3, 1, 1), // 38
        intArrayOf(2, 1, 1, 3, 1, 3), // 39
        intArrayOf(2, 3, 1, 1, 1, 3), // 40
        intArrayOf(2, 3, 1, 3, 1, 1), // 41
        intArrayOf(1, 1, 2, 1, 3, 3), // 42
        intArrayOf(1, 1, 2, 3, 3, 1), // 43
        intArrayOf(1, 3, 2, 1, 3, 1), // 44
        intArrayOf(1, 1, 3, 1, 2, 3), // 45
        intArrayOf(1, 1, 3, 3, 2, 1), // 46
        intArrayOf(1, 3, 3, 1, 2, 1), // 47
        intArrayOf(3, 1, 3, 1, 2, 1), // 48
        intArrayOf(2, 1, 1, 3, 3, 1), // 49
        intArrayOf(2, 3, 1, 1, 3, 1), // 50
        intArrayOf(2, 1, 3, 1, 1, 3), // 51
        intArrayOf(2, 1, 3, 3, 1, 1), // 52
        intArrayOf(2, 1, 3, 1, 3, 1), // 53
        intArrayOf(3, 1, 1, 1, 2, 3), // 54
        intArrayOf(3, 1, 1, 3, 2, 1), // 55
        intArrayOf(3, 3, 1, 1, 2, 1), // 56
        intArrayOf(3, 1, 2, 1, 1, 3), // 57
        intArrayOf(3, 1, 2, 3, 1, 1), // 58
        intArrayOf(3, 3, 2, 1, 1, 1), // 59
        intArrayOf(3, 1, 4, 1, 1, 1), // 60
        intArrayOf(2, 2, 1, 4, 1, 1), // 61
        intArrayOf(4, 3, 1, 1, 1, 1), // 62
        intArrayOf(1, 1, 1, 2, 2, 4), // 63
        intArrayOf(1, 1, 1, 4, 2, 2), // 64
        intArrayOf(1, 2, 1, 1, 2, 4), // 65
        intArrayOf(1, 2, 1, 4, 2, 1), // 66
        intArrayOf(1, 4, 1, 1, 2, 2), // 67
        intArrayOf(1, 4, 1, 2, 2, 1), // 68
        intArrayOf(1, 1, 2, 2, 1, 4), // 69
        intArrayOf(1, 1, 2, 4, 1, 2), // 70
        intArrayOf(1, 2, 2, 1, 1, 4), // 71
        intArrayOf(1, 2, 2, 4, 1, 1), // 72
        intArrayOf(1, 4, 2, 1, 1, 2), // 73
        intArrayOf(1, 4, 2, 2, 1, 1), // 74
        intArrayOf(2, 4, 1, 2, 1, 1), // 75
        intArrayOf(2, 2, 1, 1, 1, 4), // 76
        intArrayOf(4, 1, 3, 1, 1, 1), // 77
        intArrayOf(2, 4, 1, 1, 1, 2), // 78
        intArrayOf(1, 3, 4, 1, 1, 1), // 79
        intArrayOf(1, 1, 1, 2, 4, 2), // 80
        intArrayOf(1, 2, 1, 1, 4, 2), // 81
        intArrayOf(1, 2, 1, 2, 4, 1), // 82
        intArrayOf(1, 1, 4, 2, 1, 2), // 83
        intArrayOf(1, 2, 4, 1, 1, 2), // 84
        intArrayOf(1, 2, 4, 2, 1, 1), // 85
        intArrayOf(4, 1, 1, 2, 1, 2), // 86
        intArrayOf(4, 2, 1, 1, 1, 2), // 87
        intArrayOf(4, 2, 1, 2, 1, 1), // 88
        intArrayOf(2, 1, 2, 1, 4, 1), // 89
        intArrayOf(2, 1, 4, 1, 2, 1), // 90
        intArrayOf(4, 1, 2, 1, 2, 1), // 91
        intArrayOf(1, 1, 1, 1, 4, 3), // 92
        intArrayOf(1, 1, 1, 3, 4, 1), // 93
        intArrayOf(1, 3, 1, 1, 4, 1), // 94
        intArrayOf(1, 1, 4, 1, 1, 3), // 95
        intArrayOf(1, 1, 4, 3, 1, 1), // 96
        intArrayOf(4, 1, 1, 1, 1, 3), // 97
        intArrayOf(4, 1, 1, 3, 1, 1), // 98
        intArrayOf(1, 1, 3, 1, 4, 1), // 99
        intArrayOf(1, 1, 4, 1, 3, 1), // 100
        intArrayOf(3, 1, 1, 1, 4, 1), // 101
        intArrayOf(4, 1, 1, 1, 3, 1), // 102
        intArrayOf(2, 1, 1, 4, 1, 2), // 103
        intArrayOf(2, 1, 1, 2, 1, 4), // 104
        intArrayOf(2, 1, 1, 2, 3, 2), // 105
        intArrayOf(2, 3, 3, 1, 1, 1, 2), // 106 — stop (13 modules, 4 bars + 3 spaces)
    )

    /**
     * Encodes [data] into a [Code128Barcode].
     *
     * @param data printable-ASCII text to encode (every character must be in 32..126).
     * @return the bar/space module pattern of the complete symbol.
     * @throws IllegalArgumentException if [data] is empty or contains a character
     *   outside the printable-ASCII range 32..126.
     */
    public fun encode(data: String): Code128Barcode {
        require(data.isNotEmpty()) { "Code 128 input must not be empty." }
        data.forEach { ch ->
            val code = ch.code
            require(code in ASCII_OFFSET..ASCII_MAX) {
                // Code set A (control chars) and FNC functions are out of scope.
                "Unsupported character '$ch' (code $code); only printable ASCII 32..126 is supported."
            }
        }

        val values = buildValueSequence(data)
        val checksum = computeChecksum(values)

        // Drawing order: start, data, checksum, stop. The checksum and stop are
        // appended only here so they never participate in subset planning above.
        val symbols = ArrayList<Int>(values.size + 2)
        symbols.addAll(values)
        symbols.add(checksum)
        symbols.add(STOP)

        val modules = ArrayList<Int>(symbols.size * 6 + 2)
        symbols.forEach { value ->
            PATTERNS[value].forEach { width -> modules.add(width) }
        }
        return Code128Barcode(modules)
    }

    /**
     * Builds the start code plus the data symbol values, choosing code sets B and
     * C so that long digit runs are packed densely. Visible to tests so the
     * planned value stream (before checksum/stop) can be asserted directly.
     */
    internal fun buildValueSequence(data: String): List<Int> {
        val values = ArrayList<Int>()

        // A leading digit run only earns Start C when it can be packed without a
        // leftover digit, i.e. an even-length run; otherwise start in B.
        var inCodeC = startsInCodeC(data)
        values.add(if (inCodeC) START_C else START_B)

        var i = 0
        while (i < data.length) {
            if (inCodeC) {
                // C packs pairs; a lone trailing digit (or a non-digit) ends C.
                if (i + 1 < data.length && data[i].isDigit() && data[i + 1].isDigit()) {
                    values.add((data[i] - '0') * 10 + (data[i + 1] - '0'))
                    i += 2
                } else {
                    values.add(CODE_B)
                    inCodeC = false
                }
            } else {
                // Switch into C when a sufficiently long even-aligned digit run starts.
                if (shouldEnterCodeC(data, i)) {
                    values.add(CODE_C)
                    inCodeC = true
                } else {
                    values.add(data[i].code - ASCII_OFFSET)
                    i += 1
                }
            }
        }
        return values
    }

    /**
     * Whether encoding should begin in code set C: an all-digit even-length input
     * packs entirely in C, and a long enough leading digit run also justifies it.
     */
    private fun startsInCodeC(data: String): Boolean {
        val run = digitRunLength(data, 0)
        if (run == data.length && run % 2 == 0) return true // entire payload is digit pairs
        return run >= MIN_C_RUN && run % 2 == 0
    }

    /**
     * Whether a switch to code set C pays off at position [start]. Requires a digit
     * run of at least [MIN_C_RUN]; an odd-length interior run is fine because the
     * trailing odd digit simply falls back to B via [CODE_B].
     */
    private fun shouldEnterCodeC(data: String, start: Int): Boolean {
        return digitRunLength(data, start) >= MIN_C_RUN
    }

    /** Length of the consecutive run of decimal digits beginning at [start]. */
    private fun digitRunLength(data: String, start: Int): Int {
        var n = start
        while (n < data.length && data[n].isDigit()) n++
        return n - start
    }

    /**
     * The weighted mod-103 checksum. The start value carries weight 1, then each
     * subsequent data symbol is weighted by its 1-based position.
     */
    private fun computeChecksum(values: List<Int>): Int {
        var sum = values[0] // start code, weight 1
        for (index in 1 until values.size) {
            sum += values[index] * index
        }
        return sum % 103
    }
}
