package com.conamobile.pdfkmp.viewer

/**
 * Takes a quarter of the JVM heap as the cache ceiling — the same fraction
 * the Android backend uses. Falls back to 256 MB if `maxMemory()` reports an
 * unbounded heap (`Long.MAX_VALUE`); desktop JVMs typically run with a larger
 * heap than mobile, so the fallback is more generous than Android's 64 MB.
 */
internal actual fun bitmapCacheBudgetBytes(): Long {
    val heap = Runtime.getRuntime().maxMemory()
    return if (heap == Long.MAX_VALUE) {
        256L * 1024L * 1024L
    } else {
        heap / 4L
    }
}
