package com.conamobile.pdfkmp.kmpwriter

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial chunk-length handling in the pure-Kotlin PNG embedder: a
 * declared chunk length near [Int.MAX_VALUE] must not overflow the bounds
 * arithmetic into an out-of-bounds read or a giant allocation.
 */
class KmpImageEmbedderTest {

    @Test
    fun pngWithHonestChunks_embeds() {
        val embedded = KmpImageEmbedder.embed(png(width = 1, height = 1, idat = byteArrayOf(0x78, 0x9C.toByte())))
        assertNotNull(embedded)
        assertTrue(embedded.def.stream.isNotEmpty())
    }

    @Test
    fun pngWithOverflowingChunkLength_returnsNullInsteadOfThrowing() {
        val hostileChunkLength = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val embedded = KmpImageEmbedder.embed(
            png(width = 1, height = 1, chunkLengthOverride = hostileChunkLength),
        )
        assertNull(embedded)
    }

    private fun png(
        width: Int,
        height: Int,
        idat: ByteArray = byteArrayOf(0x42),
        chunkLengthOverride: ByteArray? = null,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out += byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        out += u32(13)
        out += byteArrayOf(0x49, 0x48, 0x44, 0x52)
        out += u32(width)
        out += u32(height)
        out += byteArrayOf(8, 2, 0, 0, 0)
        out += u32(0)
        out += (chunkLengthOverride ?: u32(idat.size))
        out += byteArrayOf(0x49, 0x44, 0x41, 0x54)
        out += idat
        out += u32(0)
        out += u32(0)
        out += byteArrayOf(0x49, 0x45, 0x4E, 0x44)
        return out.toByteArray()
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private operator fun ArrayList<Byte>.plusAssign(bytes: ByteArray) {
        bytes.forEach { add(it) }
    }
}
