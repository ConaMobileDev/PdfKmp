package com.conamobile.pdfkmp.kmpwriter

import java.util.zip.Inflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trips the pure-Kotlin [Deflate] compressor through the JVM's standard
 * zlib inflater (`java.util.zip.Inflater`) — the same inflater every PDF viewer
 * uses under the hood. If the produced zlib stream inflates back to the original
 * bytes, the header, the fixed-Huffman block, the LZ77 back-references, and the
 * Adler-32 trailer are all correct.
 *
 * `java.util.zip` is JVM-only; this is a *test*, where the task explicitly allows
 * `java.*`. The compressor itself stays pure stdlib Kotlin for the wasm/native
 * targets.
 */
class DeflateTest {

    private fun inflate(zlib: ByteArray): ByteArray {
        val inflater = Inflater() // expects a zlib header — matches our output.
        inflater.setInput(zlib)
        val out = ArrayList<Byte>()
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            for (i in 0 until n) out.add(buf[i])
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun assertRoundTrips(data: ByteArray, label: String) {
        val compressed = Deflate.zlibCompress(data)
        // zlib header byte: CM=8, CINFO=7.
        assertEquals(0x78.toByte(), compressed[0], "$label: wrong zlib CMF byte")
        val restored = inflate(compressed)
        assertTrue(data.contentEquals(restored), "$label: round-trip mismatch (${data.size} -> ${restored.size})")
    }

    @Test
    fun emptyInputRoundTrips() {
        assertRoundTrips(ByteArray(0), "empty")
    }

    @Test
    fun singleByteRoundTrips() {
        assertRoundTrips(byteArrayOf(0x41), "single")
    }

    @Test
    fun shortAsciiRoundTrips() {
        assertRoundTrips("Hello, world! BT /F0 12 Tf (text) Tj ET".encodeToByteArray(), "ascii")
    }

    @Test
    fun repetitivePayloadRoundTrips() {
        // Highly repetitive: stresses long LZ77 back-references near MAX_MATCH.
        val data = "ABCABCABC".repeat(5000).encodeToByteArray()
        assertRoundTrips(data, "repetitive")
        val compressed = Deflate.zlibCompress(data)
        // A 45 KB highly-repetitive payload must compress dramatically.
        assertTrue(
            compressed.size < data.size / 5,
            "repetitive data barely compressed: ${data.size} -> ${compressed.size}",
        )
    }

    @Test
    fun randomPayloadRoundTrips() {
        val rng = Random(0xC0FFEE)
        val data = ByteArray(40_000) { rng.nextInt(256).toByte() }
        assertRoundTrips(data, "random")
    }

    @Test
    fun mixedRunsAndLiteralsRoundTrip() {
        // Alternating long runs and random noise exercises the match/literal
        // decision boundary in the greedy matcher.
        val rng = Random(7)
        val sb = StringBuilder()
        repeat(200) {
            sb.append("x".repeat(rng.nextInt(1, 300)))
            repeat(rng.nextInt(0, 50)) { sb.append(('a' + rng.nextInt(26))) }
        }
        assertRoundTrips(sb.toString().encodeToByteArray(), "mixed")
    }

    @Test
    fun allByteValuesRoundTrip() {
        // Every byte 0..255 repeated, so the literal codes across both fixed
        // Huffman ranges (0..143 8-bit, 144..255 9-bit) are all exercised.
        val data = ByteArray(256 * 20) { (it % 256).toByte() }
        assertRoundTrips(data, "all-bytes")
    }

    @Test
    fun windowSpanningMatchRoundTrips() {
        // A payload longer than several KB with a repeated block far apart forces
        // a back-reference across a large distance (distance-code path).
        val block = ByteArray(1000) { (it % 64).toByte() }
        val filler = ByteArray(20_000) { ((it * 7) % 251).toByte() }
        val data = block + filler + block
        assertRoundTrips(data, "window-spanning")
    }
}
