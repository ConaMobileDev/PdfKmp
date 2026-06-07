package com.conamobile.pdfkmp.barcode

/**
 * Pure-Kotlin EAN-13 / UPC-A barcode encoder.
 *
 * Encodes a 12- or 13-digit numeric string into the 95-module bar/space
 * pattern of an EAN-13 symbol. The output is a [Code128Barcode] so the
 * existing 1D rendering path (alternating bar/space module widths, starting
 * with a bar) can draw it unchanged.
 *
 * **Check digit.** A 12-digit input is treated as the first twelve digits and
 * the mod-10 check digit is computed and appended. A 13-digit input is treated
 * as a complete symbol and its trailing check digit is *verified*; a mismatch
 * is rejected.
 *
 * **UPC-A.** UPC-A is the 12-digit subset of EAN-13: a 12-digit UPC-A code is
 * the same symbol as the EAN-13 code formed by prefixing a leading `0`. Callers
 * wanting UPC-A pass the 12 UPC digits to [encodeUpcA], which prepends the `0`
 * and encodes the resulting EAN-13.
 *
 * **Structure.** An EAN-13 symbol is exactly 95 modules: a 3-module start
 * guard (bar-space-bar), six left-hand digits (7 modules each), a 5-module
 * centre guard (space-bar-space-bar-space), six right-hand digits (7 modules
 * each), and a 3-module end guard (bar-space-bar). The thirteenth (leading)
 * digit is not drawn as bars — it is encoded implicitly by the L/G parity
 * pattern chosen for the six left-hand digits.
 */
public object Ean13Encoder {

    /** Modules in a complete EAN-13 symbol: 3 + 6·7 + 5 + 6·7 + 3. */
    public const val TOTAL_MODULES: Int = 95

    /**
     * L-code (odd parity) 7-module patterns for digits 0..9, as bit strings
     * where `1` is a bar and `0` a space. These are the standard EAN/UPC
     * left-hand odd-parity encodings.
     */
    private val L_CODES: IntArray = intArrayOf(
        0b0001101, // 0
        0b0011001, // 1
        0b0010011, // 2
        0b0111101, // 3
        0b0100011, // 4
        0b0110001, // 5
        0b0101111, // 6
        0b0111011, // 7
        0b0110111, // 8
        0b0001011, // 9
    )

    /**
     * The first-digit parity table: for leading digit `d`, the parity of the
     * six left-hand digits, where bit 5 is the first left digit. `1` selects
     * the L (odd) code, `0` selects the G (even) code. Right-hand digits always
     * use the R (even/complement) code, so they need no table.
     */
    private val PARITY: IntArray = intArrayOf(
        0b111111, // 0
        0b110100, // 1
        0b110010, // 2
        0b110001, // 3
        0b101100, // 4
        0b100110, // 5
        0b100011, // 6
        0b101010, // 7
        0b101001, // 8
        0b100101, // 9
    )

    /**
     * Encodes a 12- or 13-digit EAN-13 payload.
     *
     * @param data 12 digits (check digit computed) or 13 digits (check digit
     *   verified). Non-digit characters and other lengths are rejected.
     * @return the 95-module bar/space pattern, starting with a bar.
     * @throws IllegalArgumentException if [data] is not 12 or 13 digits, or if a
     *   13-digit input carries an incorrect check digit.
     */
    public fun encode(data: String): Code128Barcode {
        require(data.length == 12 || data.length == 13) {
            "EAN-13 input must be 12 digits (check computed) or 13 digits (check verified); got ${data.length}."
        }
        data.forEach { ch ->
            require(ch in '0'..'9') { "EAN-13 input must be all digits; found '$ch'." }
        }

        val digits = IntArray(13)
        for (i in 0 until 12) digits[i] = data[i] - '0'
        val computed = checkDigit(digits, 12)
        if (data.length == 13) {
            val provided = data[12] - '0'
            require(provided == computed) {
                "EAN-13 check digit mismatch: expected $computed, got $provided."
            }
            digits[12] = provided
        } else {
            digits[12] = computed
        }

        return Code128Barcode(buildModules(digits))
    }

    /**
     * Encodes a 12-digit UPC-A payload as the equivalent EAN-13 symbol.
     *
     * UPC-A is EAN-13 with an implicit leading `0`, so this prepends `0` and
     * delegates to [encode], which then computes/verifies the check digit.
     *
     * @param data 12 digits: 11 data digits plus the UPC-A check digit, or 11
     *   data digits when the check digit should be computed. Because EAN-13's
     *   own rules apply after prefixing, pass the full 11-digit body plus the
     *   check digit (12 total) to verify, or 11 digits to have it computed.
     * @throws IllegalArgumentException if the input is not 11 or 12 digits, or
     *   if a 12-digit input carries an incorrect check digit.
     */
    public fun encodeUpcA(data: String): Code128Barcode {
        require(data.length == 11 || data.length == 12) {
            "UPC-A input must be 11 digits (check computed) or 12 digits (check verified); got ${data.length}."
        }
        return encode("0$data")
    }

    /**
     * The mod-10 check digit over the first [count] digits, using the
     * EAN-13 weighting (odd positions ×1, even positions ×3, counting from
     * the left starting at position 1). Visible to tests.
     */
    internal fun checkDigit(digits: IntArray, count: Int): Int {
        var sum = 0
        for (i in 0 until count) {
            // Positions are 1-based; the 1st, 3rd, ... digits weigh 1, the
            // 2nd, 4th, ... weigh 3.
            sum += if (i % 2 == 0) digits[i] else digits[i] * 3
        }
        return (10 - sum % 10) % 10
    }

    /**
     * Builds the 95-module alternating bar/space width list from the 13 digits.
     * The list always starts with a bar; consecutive same-colour modules are
     * merged into a single width so the renderer's even=bar/odd=space contract
     * holds. Visible to tests.
     */
    internal fun buildModules(digits: IntArray): List<Int> {
        // Assemble the full 95-bit module bitmap (1 = bar), then run-length
        // encode it into alternating widths starting with a bar.
        val bits = BooleanArray(TOTAL_MODULES)
        var p = 0

        // Start guard: bar, space, bar (101).
        bits[p++] = true; bits[p++] = false; bits[p++] = true

        // Six left-hand digits, parity chosen by the leading digit.
        val parity = PARITY[digits[0]]
        for (i in 0 until 6) {
            val digit = digits[1 + i]
            val odd = (parity ushr (5 - i)) and 1 == 1
            // L code = odd parity bits; G code = reverse of the R code, which is
            // the bitwise complement of the L code read in the same order.
            val pattern = if (odd) L_CODES[digit] else reverse7(L_CODES[digit].inv() and 0x7F)
            for (b in 6 downTo 0) bits[p++] = (pattern ushr b) and 1 == 1
        }

        // Centre guard: space, bar, space, bar, space (01010).
        bits[p++] = false; bits[p++] = true; bits[p++] = false; bits[p++] = true; bits[p++] = false

        // Six right-hand digits, always R code (complement of L, i.e. even parity).
        for (i in 0 until 6) {
            val digit = digits[7 + i]
            val pattern = L_CODES[digit].inv() and 0x7F // R = complement of L.
            for (b in 6 downTo 0) bits[p++] = (pattern ushr b) and 1 == 1
        }

        // End guard: bar, space, bar (101).
        bits[p++] = true; bits[p++] = false; bits[p++] = true

        return runLengths(bits)
    }

    /** Reverses the low 7 bits of [value] — turns an R pattern into a G pattern. */
    private fun reverse7(value: Int): Int {
        var out = 0
        for (b in 0 until 7) {
            if ((value ushr b) and 1 == 1) out = out or (1 shl (6 - b))
        }
        return out
    }

    /**
     * Run-length encodes a module bitmap into alternating widths. The first
     * module is always a bar (the start guard), so the first run is a bar and
     * the list satisfies the even=bar/odd=space contract.
     */
    private fun runLengths(bits: BooleanArray): List<Int> {
        val widths = ArrayList<Int>()
        var current = bits[0]
        var run = 1
        // The bitmap always starts with a bar; a defensive lead-in space is
        // never needed because the start guard guarantees it.
        for (i in 1 until bits.size) {
            if (bits[i] == current) {
                run++
            } else {
                widths.add(run)
                current = bits[i]
                run = 1
            }
        }
        widths.add(run)
        return widths
    }
}
