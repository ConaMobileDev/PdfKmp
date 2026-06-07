package com.conamobile.pdfkmp.ttf

/**
 * A forward/random-access reader over a TrueType font [ByteArray] in the
 * big-endian byte order every SFNT structure uses.
 *
 * TrueType (and the OpenType SFNT container it shares) stores every multi-byte
 * integer most-significant-byte first, regardless of host platform. A reader
 * that always reassembles big-endian keeps the parser free of per-field byte
 * juggling and, crucially, stays platform-neutral so the same code runs on the
 * JVM, native, and wasm targets the pure-Kotlin backend serves.
 *
 * The cursor ([position]) advances on the sequential `read*` calls; the
 * absolute `*At` helpers leave it untouched, which the table parsers rely on to
 * follow offsets (e.g. a cmap subtable pointer) without losing their place.
 */
internal class TtfReader(private val data: ByteArray) {

    /** Current read cursor, in bytes from the start of the font. */
    var position: Int = 0

    /** Total length of the underlying font data. */
    val size: Int get() = data.size

    /** Moves the cursor to [offset] for subsequent sequential reads. */
    fun seek(offset: Int) {
        position = offset
    }

    /** Advances the cursor by [count] bytes without reading. */
    fun skip(count: Int) {
        position += count
    }

    /** Reads an unsigned byte (0..255) and advances one byte. */
    fun readUInt8(): Int = data[position++].toInt() and 0xFF

    /** Reads a signed byte (-128..127) and advances one byte. */
    fun readInt8(): Int = data[position++].toInt()

    /** Reads a big-endian unsigned 16-bit value (0..65535) and advances two bytes. */
    fun readUInt16(): Int {
        val hi = data[position++].toInt() and 0xFF
        val lo = data[position++].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    /** Reads a big-endian signed 16-bit value (-32768..32767) and advances two bytes. */
    fun readInt16(): Int {
        val v = readUInt16()
        return if (v >= 0x8000) v - 0x10000 else v
    }

    /** Reads a big-endian unsigned 32-bit value (as [Long] to stay unsigned) and advances four bytes. */
    fun readUInt32(): Long {
        val b0 = (data[position++].toLong() and 0xFF)
        val b1 = (data[position++].toLong() and 0xFF)
        val b2 = (data[position++].toLong() and 0xFF)
        val b3 = (data[position++].toLong() and 0xFF)
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /** Reads a big-endian signed 32-bit value and advances four bytes. */
    fun readInt32(): Int {
        val b0 = (data[position++].toInt() and 0xFF)
        val b1 = (data[position++].toInt() and 0xFF)
        val b2 = (data[position++].toInt() and 0xFF)
        val b3 = (data[position++].toInt() and 0xFF)
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /** Reads a four-byte SFNT tag as its ASCII string (e.g. `"glyf"`). */
    fun readTag(): String {
        val sb = StringBuilder(4)
        repeat(4) { sb.append((data[position++].toInt() and 0xFF).toChar()) }
        return sb.toString()
    }

    // -- Absolute reads (cursor-preserving) -------------------------------

    /** Reads a big-endian unsigned 16-bit value at absolute [offset] without moving the cursor. */
    fun uint16At(offset: Int): Int {
        val hi = data[offset].toInt() and 0xFF
        val lo = data[offset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    /** Reads a big-endian unsigned 32-bit value at absolute [offset] without moving the cursor. */
    fun uint32At(offset: Int): Long {
        val b0 = (data[offset].toLong() and 0xFF)
        val b1 = (data[offset + 1].toLong() and 0xFF)
        val b2 = (data[offset + 2].toLong() and 0xFF)
        val b3 = (data[offset + 3].toLong() and 0xFF)
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}
