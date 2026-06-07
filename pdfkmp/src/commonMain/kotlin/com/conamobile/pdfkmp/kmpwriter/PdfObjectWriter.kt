package com.conamobile.pdfkmp.kmpwriter

/**
 * Accumulates PDF indirect objects into a byte buffer and serialises the whole
 * file — header, body, cross-reference table, and trailer — with byte-exact
 * offsets.
 *
 * The writer assembles raw bytes, never characters, for one specific reason:
 * image XObjects carry binary stream payloads (JPEG / PNG-IDAT bytes passed
 * through verbatim) that are not valid text, and the `xref` table records the
 * *byte* index of every object. Tracking a running byte length as objects are
 * appended — rather than counting characters in an accumulated `String` — is the
 * only way those offsets stay correct once non-ASCII stream bytes are in the mix.
 *
 * Usage: reserve every object number up front with [allocate] so cross-references
 * can be wired before bodies are known, fill each one with [writeObject] (or
 * [writeStreamObject] for a dictionary + stream), then call [build] with the
 * catalog object number to get the finished document.
 */
internal class PdfObjectWriter {

    /** One serialised indirect object awaiting placement in the body. */
    private class Entry(val number: Int, val body: ByteArray)

    private val entries = ArrayList<Entry>()

    /** Next free object number. Object 0 is reserved for the free-list head. */
    private var nextNumber = 1

    /** Reserves and returns a fresh object number without writing a body yet. */
    fun allocate(): Int = nextNumber++

    /**
     * Writes the body of object [number] from already-encoded ASCII [body]
     * (a dictionary, array, or other non-stream object — no `N 0 obj` /
     * `endobj` wrapper, which [build] adds). Most objects use this; binary
     * streams use [writeStreamObject].
     */
    fun writeObject(number: Int, body: String) {
        entries.add(Entry(number, body.encodeToByteArray()))
    }

    /**
     * Writes a stream object: the [dictionary] body (its `<< … >>` text, which
     * must already declare every entry *except* `/Length`, added here from the
     * payload size) followed by the raw [stream] bytes wrapped in
     * `stream` / `endstream`. Used for content streams and image XObjects, where
     * [stream] may be arbitrary binary.
     */
    fun writeStreamObject(number: Int, dictionary: String, stream: ByteArray) {
        val head = StringBuilder()
        // Splice /Length into the dictionary just before its closing >>.
        val close = dictionary.lastIndexOf(">>")
        val withLength = if (close >= 0) {
            dictionary.substring(0, close) + "/Length ${stream.size} " + dictionary.substring(close)
        } else {
            dictionary
        }
        head.append(withLength)
        head.append("\nstream\n")
        val tail = "\nendstream"

        val headBytes = head.toString().encodeToByteArray()
        val tailBytes = tail.encodeToByteArray()
        val body = ByteArray(headBytes.size + stream.size + tailBytes.size)
        headBytes.copyInto(body, 0)
        stream.copyInto(body, headBytes.size)
        tailBytes.copyInto(body, headBytes.size + stream.size)
        entries.add(Entry(number, body))
    }

    /**
     * Serialises the document. [rootObject] is the catalog object number written
     * into the trailer `/Root`; [infoObject] (if any) becomes `/Info`. Returns
     * the complete PDF byte array.
     *
     * The cross-reference table lists object 0 as the free-list head followed by
     * objects 1..N in number order; any object number that was [allocate]d but
     * never written (an inert forward reference, say) is emitted as a free entry
     * so the table stays dense and the trailer `/Size` stays consistent.
     */
    fun build(rootObject: Int, infoObject: Int?): ByteArray {
        val out = ByteBuffer()

        // Header. The binary-comment line (4 bytes ≥ 0x80) marks the file as
        // containing binary data so naive ASCII transfer tools don't mangle it.
        out.append("%PDF-1.7\n")
        out.appendBytes(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte()))
        out.append("\n")

        // Body: each object at its recorded byte offset.
        val byNumber = entries.associateBy { it.number }
        val total = nextNumber // object numbers 0 .. nextNumber-1
        val offsets = IntArray(total)
        for (number in 1 until total) {
            val entry = byNumber[number] ?: continue
            offsets[number] = out.size
            out.append("$number 0 obj\n")
            out.appendBytes(entry.body)
            out.append("\nendobj\n")
        }

        // Cross-reference table.
        val xrefOffset = out.size
        out.append("xref\n")
        out.append("0 $total\n")
        // Object 0 is the head of the free list: generation 65535, free.
        out.append("0000000000 65535 f \n")
        for (number in 1 until total) {
            if (byNumber.containsKey(number)) {
                out.append(padOffset(offsets[number]) + " 00000 n \n")
            } else {
                // Unwritten reserved number: a free entry keeps the table dense.
                out.append("0000000000 00000 f \n")
            }
        }

        // Trailer.
        out.append("trailer\n<< /Size $total /Root $rootObject 0 R")
        if (infoObject != null) out.append(" /Info $infoObject 0 R")
        out.append(" >>\n")
        out.append("startxref\n$xrefOffset\n%%EOF\n")

        return out.toByteArray()
    }

    /** 10-digit zero-padded byte offset, the fixed xref-entry field width. */
    private fun padOffset(offset: Int): String {
        val s = offset.toString()
        return if (s.length >= 10) s else "0".repeat(10 - s.length) + s
    }
}

/**
 * A growable byte buffer used to assemble the document. Kept tiny and
 * stdlib-only (no `java.io`, no `kotlin.io` channels) so it compiles for the
 * wasmJs target this writer ultimately targets. ASCII text is appended through
 * [append]; raw binary through [appendBytes].
 */
internal class ByteBuffer(initialCapacity: Int = 64 * 1024) {

    private var data = ByteArray(initialCapacity)

    /** Current length in bytes — also the offset the next append lands at. */
    var size: Int = 0
        private set

    private fun ensure(extra: Int) {
        val needed = size + extra
        if (needed <= data.size) return
        var newCapacity = data.size * 2
        while (newCapacity < needed) newCapacity *= 2
        data = data.copyOf(newCapacity)
    }

    /** Appends [text] as UTF-8 / ASCII bytes (PDF syntax is ASCII). */
    fun append(text: String) {
        appendBytes(text.encodeToByteArray())
    }

    /** Appends raw [bytes] verbatim. */
    fun appendBytes(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(data, size)
        size += bytes.size
    }

    /** Returns an exact-length copy of the accumulated bytes. */
    fun toByteArray(): ByteArray = data.copyOf(size)
}
