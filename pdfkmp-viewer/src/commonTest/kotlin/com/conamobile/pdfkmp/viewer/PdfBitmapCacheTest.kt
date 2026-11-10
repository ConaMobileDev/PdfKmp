package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression tests for the native crash in `PdfBitmapCache`: on
 * Kotlin/Native, a map entry's `value` getter re-checks the backing
 * map's modification count (stdlib `HashMap.EntryRef`), so reading
 * `entry.value` *after* the entry-set iterator's `remove()` — which
 * bumps that count — throws `ConcurrentModificationException` and took
 * the whole process down. It manifested in `trim()` (reached whenever a
 * platform render fails and the cache halves itself as recovery) and in
 * `evictUntilUnderBudget()` (reached whenever `put` overflows the
 * budget). Android/Desktop read the entry off
 * `java.util.LinkedHashMap`, whose getter performs no such check, so
 * both surfaces stayed green and only iOS device/simulator runs
 * reproduced the abort.
 *
 * These tests force `trim()` and budget eviction to run on every
 * surface. On a pre-fix iOS Simulator they fail with the
 * ConcurrentModificationException; on JVM they pin the LRU +
 * byte-accounting semantics that must not change. Visibility inside the
 * cache is deliberately not asserted through a `size` hook — existence
 * is observed via [PdfBitmapCache.get], the same way the viewer reads
 * it, so the production surface stays untouched by the test.
 */
class PdfBitmapCacheTest {

    /** Cheap `ImageBitmap` stand-in sized for `estimateBytes` accounting. */
    private class TestBitmap(
        override val width: Int,
        override val height: Int,
    ) : ImageBitmap {
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = true
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) {
        }
        override fun prepareToDraw() {}
    }

    private fun bytes(w: Int, h: Int): Long = w.toLong() * h.toLong() * 4L

    private fun stub(width: Int, height: Int): TestBitmap =
        TestBitmap(width, height)

    @Test
    fun trim_doesNotThrow_andKeepsMostRecentlyUsedHalf() = runBlocking {
        val cache = PdfBitmapCache(budgetBytes = Long.MAX_VALUE)
        val key: (Int) -> PdfPageCacheKey = { i -> PdfPageCacheKey(i, 1, false) }
        repeat(6) { i -> cache.put(key(i), stub(100, 100)) }
        cache.get(key(0)) // promote page 0 above the trim line

        cache.trim() // the previously-crashing call site

        // Precise half-rule: of the six entries, the three at the LRU
        // head go. With 0 promoted, the head order is 1, 2, 3 and the
        // remaining order is 4, 5, 0.
        assertTrue(cache.get(key(1)) == null, "least-recently-used page 1 must be dropped")
        assertTrue(cache.get(key(2)) == null, "next LRU page 2 must also be dropped")
        assertTrue(cache.get(key(3)) == null, "page 3 completes the dropped LRU head")
        assertTrue(cache.get(key(0)) != null, "promoted page 0 must survive the trim")
        assertTrue(cache.get(key(4)) != null, "page 4 must survive the trim")
        assertTrue(cache.get(key(5)) != null, "page 5 must survive the trim")
    }

    @Test
    fun trim_emptyCache_isAQuietNoOp() = runBlocking {
        val cache = PdfBitmapCache(budgetBytes = 1024)
        cache.trim()
        assertTrue(cache.get(PdfPageCacheKey(0, 1, false)) == null, "empty cache stays empty")
    }

    @Test
    fun trim_singleEntry_clearsTheCache() = runBlocking {
        val cache = PdfBitmapCache(budgetBytes = 4096)
        val only = PdfPageCacheKey(0, 1, false)
        cache.put(only, stub(10, 10))
        cache.trim()
        assertTrue(cache.get(only) == null, "trim() of a 1-entry cache must clear it")
    }

    @Test
    fun put_overflowingBudget_evictsOldestWithoutThrowing() = runBlocking {
        val page = stub(100, 100) // 40 000 bytes by the 4-bpp accounting
        val budget = bytes(100, 100) * 2
        val cache = PdfBitmapCache(budgetBytes = budget)
        val key: (Int) -> PdfPageCacheKey = { i -> PdfPageCacheKey(i, 1, false) }

        repeat(5) { i -> cache.put(key(i), page) } // forces evictUntilUnderBudget repeatedly

        assertTrue(cache.get(key(0)) == null, "page 0 was evicted long ago")
        assertTrue(cache.get(key(1)) == null, "page 1 was evicted long ago")
        assertTrue(cache.get(key(2)) == null, "page 2 was evicted too")
        assertTrue(cache.get(key(3)) != null, "page 3 must still be cached")
        assertTrue(cache.get(key(4)) != null, "page 4 (newest) must still be cached")
    }

    @Test
    fun put_overwritingKey_reclaimsBytesImmediately() = runBlocking {
        val page = stub(100, 100)
        val budget = bytes(100, 100) * 2
        val cache = PdfBitmapCache(budgetBytes = budget)
        val first = PdfPageCacheKey(7, 1, false)

        cache.put(first, page)
        cache.put(PdfPageCacheKey(8, 1, false), page) // 2 entries, cache full
        cache.put(first, stub(100, 100))              // same key again — must not double-count

        assertTrue(cache.get(first) != null, "re-put on an existing key must update in place")
        assertTrue(cache.get(PdfPageCacheKey(8, 1, false)) != null, "no eviction should fire: bytes were reclaimed")
    }

    @Test
    fun get_promotesEntryAboveEvictionWalk() = runBlocking {
        val page = stub(100, 100)
        val budget = bytes(100, 100) * 2
        val cache = PdfBitmapCache(budgetBytes = budget)
        val key: (Int) -> PdfPageCacheKey = { i -> PdfPageCacheKey(i, 1, false) }

        cache.put(key(0), page)
        cache.put(key(1), page)
        cache.get(key(0)) // promote 0: order is now 1 (oldest), 0 (newest)
        cache.put(key(2), page) // overflow → one eviction → 1 goes, 0 survives its promotion

        assertTrue(cache.get(key(0)) != null, "a read must promote an entry above the eviction walk")
        assertTrue(cache.get(key(1)) == null, "the unpromoted oldest entry must be the first eviction")
    }
}
