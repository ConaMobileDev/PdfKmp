package com.conamobile.pdfkmp.kmpwriter

/**
 * A pure-Kotlin DEFLATE compressor wrapped in the zlib container, used to apply
 * `/FlateDecode` to PDF content streams (and the ToUnicode CMap) in the
 * from-scratch backend.
 *
 * ## Why hand-rolled
 *
 * The pure-Kotlin backend targets wasmJs and native, where `java.util.zip` does
 * not exist; the PDF spec's most universal stream filter is `/FlateDecode`
 * (zlib/DEFLATE), so a small self-contained compressor is the only way to ship
 * compressed output everywhere. Every PDF viewer's inflater is the standard one,
 * so as long as this produces a *valid* DEFLATE stream the choice of how
 * aggressively it compresses is purely a size/speed trade-off, invisible to
 * consumers.
 *
 * ## Algorithm
 *
 * LZ77 with a 32 KB sliding window finds back-references (greedy longest-match,
 * the simplest correct strategy), emitted into **fixed-Huffman** DEFLATE blocks
 * (RFC 1951 §3.2.6): the literal/length and distance codes use the predefined
 * static code tables, so no per-block Huffman tree has to be built or
 * transmitted. This is a deliberate simplicity-over-ratio choice — dynamic
 * Huffman would shave a few more percent but at a large code-complexity cost the
 * backend doesn't need. The DEFLATE payload is framed per RFC 1950: a `0x78 0x9C`
 * zlib header and a trailing big-endian Adler-32 of the *uncompressed* data.
 *
 * The whole input is emitted as a single fixed-Huffman block (with `BFINAL` set);
 * for the kilobytes-sized content streams PdfKmp produces this is well within the
 * format's limits and keeps the bit-writer trivial.
 */
internal object Deflate {

    /** Sliding-window size DEFLATE allows back-references within (RFC 1951). */
    private const val WINDOW_SIZE = 32 * 1024

    /** Longest back-reference DEFLATE can encode in one length symbol. */
    private const val MAX_MATCH = 258

    /** Shortest run worth encoding as a back-reference instead of literals. */
    private const val MIN_MATCH = 3

    /** Hash-chain table size; a power of two so masking replaces modulo. */
    private const val HASH_SIZE = 1 shl 15

    /**
     * Compresses [input] into a zlib stream (`/FlateDecode`-compatible). Returns a
     * fresh byte array; [input] is not modified.
     */
    fun zlibCompress(input: ByteArray): ByteArray {
        val writer = BitWriter()
        deflateFixed(input, writer)
        val deflated = writer.toByteArray()

        val out = ByteArray(2 + deflated.size + 4)
        // zlib header: CMF=0x78 (CM=8 deflate, CINFO=7 → 32 KB window),
        // FLG=0x9C (FCHECK so (CMF<<8|FLG) % 31 == 0, default compression level).
        out[0] = 0x78
        out[1] = 0x9C.toByte()
        deflated.copyInto(out, 2)

        val adler = adler32(input)
        val tail = 2 + deflated.size
        out[tail] = ((adler ushr 24) and 0xFF).toByte()
        out[tail + 1] = ((adler ushr 16) and 0xFF).toByte()
        out[tail + 2] = ((adler ushr 8) and 0xFF).toByte()
        out[tail + 3] = (adler and 0xFF).toByte()
        return out
    }

    // -- DEFLATE (fixed Huffman, greedy LZ77) -----------------------------

    private fun deflateFixed(input: ByteArray, writer: BitWriter) {
        // BFINAL = 1 (last block), BTYPE = 01 (fixed Huffman). These are written
        // LSB-first like every DEFLATE bit field.
        writer.writeBits(1, 1) // BFINAL
        writer.writeBits(1, 2) // BTYPE = 01

        if (input.isEmpty()) {
            writeFixedLiteralOrLength(writer, 256) // end-of-block
            return
        }

        // head[hash] = most recent position with that 3-byte hash; prev[pos] =
        // the position before that — together a hash chain per the zlib design.
        val head = IntArray(HASH_SIZE) { -1 }
        val prev = IntArray(input.size) { -1 }

        var pos = 0
        while (pos < input.size) {
            val match = if (pos + MIN_MATCH <= input.size) {
                findLongestMatch(input, pos, head, prev)
            } else {
                null
            }
            if (match != null && match.length >= MIN_MATCH) {
                writeMatch(writer, match.length, match.distance)
                // Insert every position the match covers into the hash chains so
                // later matches can reference inside it.
                val end = pos + match.length
                while (pos < end) {
                    if (pos + MIN_MATCH <= input.size) insertHash(input, pos, head, prev)
                    pos++
                }
            } else {
                writeFixedLiteralOrLength(writer, input[pos].toInt() and 0xFF)
                if (pos + MIN_MATCH <= input.size) insertHash(input, pos, head, prev)
                pos++
            }
        }
        writeFixedLiteralOrLength(writer, 256) // end-of-block
    }

    private class Match(val length: Int, val distance: Int)

    private fun hash3(input: ByteArray, pos: Int): Int {
        val a = input[pos].toInt() and 0xFF
        val b = input[pos + 1].toInt() and 0xFF
        val c = input[pos + 2].toInt() and 0xFF
        // A cheap multiplicative hash spreading three bytes across the table.
        return ((a shl 10) xor (b shl 5) xor c) and (HASH_SIZE - 1)
    }

    private fun insertHash(input: ByteArray, pos: Int, head: IntArray, prev: IntArray) {
        val h = hash3(input, pos)
        prev[pos] = head[h]
        head[h] = pos
    }

    /**
     * Walks the hash chain for the 3-byte prefix at [pos] and returns the longest
     * back-reference into the 32 KB window, or `null` if none reaches [MIN_MATCH].
     * The chain walk is capped so pathological inputs stay fast (the standard
     * zlib "max chain length" guard); on PdfKmp's small streams this rarely bites.
     */
    private fun findLongestMatch(input: ByteArray, pos: Int, head: IntArray, prev: IntArray): Match? {
        val h = hash3(input, pos)
        var candidate = head[h]
        val limit = (pos - WINDOW_SIZE).coerceAtLeast(0)
        val maxLen = (input.size - pos).coerceAtMost(MAX_MATCH)
        if (maxLen < MIN_MATCH) return null

        var bestLen = MIN_MATCH - 1
        var bestDist = 0
        var chain = 256 // chain-length cap
        while (candidate >= limit && candidate >= 0 && chain-- > 0) {
            // Quick reject: only extend if the byte past the current best differs.
            if (input[candidate + bestLen] == input[pos + bestLen]) {
                var len = 0
                while (len < maxLen && input[candidate + len] == input[pos + len]) len++
                if (len > bestLen) {
                    bestLen = len
                    bestDist = pos - candidate
                    if (len >= maxLen) break
                }
            }
            candidate = prev[candidate]
        }
        return if (bestLen >= MIN_MATCH) Match(bestLen, bestDist) else null
    }

    // -- Fixed-Huffman symbol emission ------------------------------------

    /**
     * Writes a literal byte (0..255) or the end-of-block symbol (256) using the
     * fixed literal/length code (RFC 1951 §3.2.6): values 0..143 are 8-bit codes
     * `0x30..0xBF`, 144..255 are 9-bit `0x190..0x1FF`, 256..279 are 7-bit
     * `0x00..0x17`, 280..287 are 8-bit `0xC0..0xC7`. Codes are written MSB-first
     * within the symbol, which is how Huffman codes (unlike DEFLATE's integer
     * fields) go onto the bit stream.
     */
    private fun writeFixedLiteralOrLength(writer: BitWriter, symbol: Int) {
        when {
            symbol <= 143 -> writer.writeHuffman(0x30 + symbol, 8)
            symbol <= 255 -> writer.writeHuffman(0x190 + (symbol - 144), 9)
            symbol <= 279 -> writer.writeHuffman(0x00 + (symbol - 256), 7)
            else -> writer.writeHuffman(0xC0 + (symbol - 280), 8)
        }
    }

    /**
     * Encodes a length/distance back-reference: the length symbol (257..285) with
     * its extra bits, then the distance symbol (0..29) with its extra bits, all
     * per the RFC 1951 length/distance code tables.
     */
    private fun writeMatch(writer: BitWriter, length: Int, distance: Int) {
        val li = lengthIndex(length)
        writeFixedLiteralOrLength(writer, 257 + li)
        val lExtra = LENGTH_EXTRA_BITS[li]
        if (lExtra > 0) writer.writeBits(length - LENGTH_BASE[li], lExtra)

        val di = distanceIndex(distance)
        // Distance codes are 5-bit, written MSB-first like other Huffman codes.
        writer.writeHuffman(di, 5)
        val dExtra = DIST_EXTRA_BITS[di]
        if (dExtra > 0) writer.writeBits(distance - DIST_BASE[di], dExtra)
    }

    private fun lengthIndex(length: Int): Int {
        // Find the largest base <= length. The table is short; a linear scan from
        // the top is both correct and trivially fast.
        for (i in LENGTH_BASE.indices.reversed()) {
            if (length >= LENGTH_BASE[i]) return i
        }
        return 0
    }

    private fun distanceIndex(distance: Int): Int {
        for (i in DIST_BASE.indices.reversed()) {
            if (distance >= DIST_BASE[i]) return i
        }
        return 0
    }

    // -- Adler-32 ---------------------------------------------------------

    /** Adler-32 checksum of [data] (RFC 1950), the zlib trailer. */
    private fun adler32(data: ByteArray): Int {
        val mod = 65521
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % mod
            b = (b + a) % mod
        }
        return (b shl 16) or a
    }

    // -- RFC 1951 length/distance tables ----------------------------------

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA_BITS = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
        8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA_BITS = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
}

/**
 * A DEFLATE bit writer. DEFLATE packs bits LSB-first into bytes, but Huffman
 * codes are defined MSB-first within the code — the two paths
 * ([writeBits] vs [writeHuffman]) keep that distinction explicit so callers
 * don't have to reverse bits by hand at every call site.
 */
private class BitWriter {
    private var buffer = ByteArray(1024)
    private var size = 0
    private var bitBuffer = 0
    private var bitCount = 0

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var cap = buffer.size * 2
        while (cap < size + extra) cap *= 2
        buffer = buffer.copyOf(cap)
    }

    /**
     * Writes the low [count] bits of [value] LSB-first — the order DEFLATE uses
     * for its integer fields (block header, extra bits).
     */
    fun writeBits(value: Int, count: Int) {
        var v = value
        var n = count
        while (n > 0) {
            bitBuffer = bitBuffer or ((v and 1) shl bitCount)
            v = v ushr 1
            bitCount++
            n--
            if (bitCount == 8) flushByte()
        }
    }

    /**
     * Writes a Huffman [code] of [length] bits MSB-first (the high bit of the
     * code goes onto the stream first), reusing the LSB-first [writeBits] by
     * peeling bits from the top.
     */
    fun writeHuffman(code: Int, length: Int) {
        for (i in length - 1 downTo 0) {
            writeBits((code ushr i) and 1, 1)
        }
    }

    private fun flushByte() {
        ensure(1)
        buffer[size++] = (bitBuffer and 0xFF).toByte()
        bitBuffer = 0
        bitCount = 0
    }

    /** Flushes any partial final byte (zero-padded) and returns the stream bytes. */
    fun toByteArray(): ByteArray {
        if (bitCount > 0) flushByte()
        return buffer.copyOf(size)
    }
}
