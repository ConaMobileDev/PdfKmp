package com.conamobile.pdfkmp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Identifies a cached bitmap. Keyed on the page index, a quantised
 * density bucket, *and* the colour-inversion flag so that a base-density
 * bitmap and a zoomed-in bitmap of the same page can coexist (the
 * viewer switches between them when the user starts / stops pinching),
 * and so toggling dark-mode ([invert]) never serves a stale,
 * wrong-polarity bitmap from the cache.
 *
 * Density is bucketed to the nearest 0.25 to keep the cache from
 * accumulating dozens of near-identical entries as the user fiddles
 * with zoom — see [bucketOf].
 */
internal data class PdfPageCacheKey(
    val index: Int,
    val densityBucket: Int,
    val invert: Boolean,
) {
    internal companion object {
        /**
         * Quantise [density] to a discrete bucket. 0.25-precision is
         * a deliberate compromise: it keeps the bucket count small
         * (zoom 1× → 2× spans 5 buckets) while staying close enough
         * to the requested density that text stays sharp without an
         * obvious quality jump.
         */
        fun bucketOf(density: Float): Int =
            max(1, (density * 4f).roundToInt())
    }
}

/**
 * LRU bitmap cache shared between every visible [PdfPageItem] inside
 * a [PdfViewer]. The cache is the layer that lets the viewer survive
 * the "scroll to page 30, scroll back to page 1" loop without
 * re-rendering everything — when the viewer was naïvely letting
 * `LazyColumn` dispose page state, every backwards scroll triggered
 * a fresh rasterisation.
 *
 * The cache is sized in **bytes**, not entries, so the budget stays
 * fair across very different page geometries (a tall PDF receipt vs
 * a wide landscape spread): a 2 MB bitmap counts twice as much as a
 * 1 MB one.
 *
 * Concurrency: the viewer issues prefetches from several coroutines
 * at once (the visible page plus its before / after neighbours), so
 * reads, writes and evictions are serialised through a [Mutex]. The
 * mutex is held only across map operations, never across the
 * actual render call.
 */
internal class PdfBitmapCache(public val budgetBytes: Long) {

    /**
     * Insertion-ordered map used as a poor-man's LRU: `get` removes
     * the entry and re-inserts it to push it to the tail, so the
     * head is always the least recently used. `LinkedHashMap`'s
     * access-order constructor flag is JVM-only, hence the manual
     * dance.
     */
    private val entries = LinkedHashMap<PdfPageCacheKey, ImageBitmap>()
    private val mutex = Mutex()
    private var currentBytes = 0L

    /**
     * Looks up [key]. Returns `null` on miss. On hit, the entry is
     * promoted to "most recently used" so subsequent eviction
     * walks discard it last.
     */
    suspend fun get(key: PdfPageCacheKey): ImageBitmap? = mutex.withLock {
        val bitmap = entries.remove(key) ?: return@withLock null
        entries[key] = bitmap
        bitmap
    }

    /**
     * Stores [bitmap] under [key] and evicts oldest entries until
     * the byte budget is satisfied. Replacing an existing key keeps
     * the most recent bitmap and updates the byte accounting in
     * place.
     */
    suspend fun put(key: PdfPageCacheKey, bitmap: ImageBitmap) {
        mutex.withLock {
            entries.remove(key)?.let { currentBytes -= estimateBytes(it) }
            entries[key] = bitmap
            currentBytes += estimateBytes(bitmap)
            evictUntilUnderBudget()
        }
    }

    /**
     * Drops the oldest half of the cache. Used as a last-ditch
     * recovery hook when the platform render call fails with an
     * out-of-memory error — see [renderAndCache].
     */
    suspend fun trim() {
        mutex.withLock {
            val keep = entries.size / 2
            val iter = entries.entries.iterator()
            var dropped = 0
            val toDrop = entries.size - keep
            while (iter.hasNext() && dropped < toDrop) {
                val entry = iter.next()
                iter.remove()
                currentBytes -= estimateBytes(entry.value)
                dropped += 1
            }
        }
    }

    /** Empties the cache. Called by the viewer's [DisposableEffect] on dispose. */
    suspend fun clear() {
        mutex.withLock {
            entries.clear()
            currentBytes = 0L
        }
    }

    private fun evictUntilUnderBudget() {
        if (currentBytes <= budgetBytes) return
        val iter = entries.entries.iterator()
        while (currentBytes > budgetBytes && iter.hasNext()) {
            val entry = iter.next()
            iter.remove()
            currentBytes -= estimateBytes(entry.value)
        }
    }
}

/**
 * Returns the approximate byte cost of [bitmap], assuming a 4-byte
 * per-pixel ARGB layout (matches both the Android and iOS rasterisers
 * the viewer uses).
 */
private fun estimateBytes(bitmap: ImageBitmap): Long =
    bitmap.width.toLong() * bitmap.height.toLong() * 4L

/**
 * Renders [index] at [density] through [renderer], routing the
 * result through [cache] so the next request for the same key is a
 * straight memory hit. [invert] flows into both the render call and
 * the cache key so a normal and a dark-mode bitmap of the same page
 * never overwrite each other. Catches platform OOMs (Android only —
 * iOS gets killed by the kernel) and retries once after halving the
 * cache, so the worst-case experience for an over-eager prefetch
 * window is "renders return null" rather than a process crash.
 */
internal suspend fun renderAndCache(
    renderer: PdfPageRenderer,
    cache: PdfBitmapCache,
    index: Int,
    density: Float,
    invert: Boolean,
): ImageBitmap? {
    val key = PdfPageCacheKey(index, PdfPageCacheKey.bucketOf(density), invert)
    cache.get(key)?.let { return it }

    return try {
        val rendered = renderer.renderPage(index, density, invert) ?: return null
        cache.put(key, rendered)
        rendered
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        cache.trim()
        try {
            val rendered = renderer.renderPage(index, density, invert) ?: return null
            cache.put(key, rendered)
            rendered
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Per-platform memory budget for the bitmap cache. Returned in
 * bytes; the cache evicts oldest entries until total usage stays
 * below this value.
 *
 * - Android — 25 % of `Runtime.getRuntime().maxMemory()` so the
 *   cache leaves headroom for the rest of the app.
 * - iOS — fixed 200 MB. iOS doesn't expose a heap ceiling we can
 *   query reliably, and 200 MB is well below typical OOM-killer
 *   thresholds while still big enough for a few dozen A4 pages at
 *   2× density.
 */
internal expect fun bitmapCacheBudgetBytes(): Long
