package com.conamobile.pdfkmp.viewer

/**
 * Take a quarter of the JVM heap as the cache ceiling. 25 % is the
 * same fraction the AOSP `LruCache` examples recommend — it keeps
 * the cache useful for warm scrolling without starving the rest of
 * the app of allocations. Falls back to 64 MB on the off chance
 * `maxMemory()` returns `Long.MAX_VALUE` (sometimes happens in
 * unbounded test JVMs).
 */
internal actual fun bitmapCacheBudgetBytes(): Long {
    val heap = Runtime.getRuntime().maxMemory()
    return if (heap == Long.MAX_VALUE) {
        64L * 1024L * 1024L
    } else {
        heap / 4L
    }
}
