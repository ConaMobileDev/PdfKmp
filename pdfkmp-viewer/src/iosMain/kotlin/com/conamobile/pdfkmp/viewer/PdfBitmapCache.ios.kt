package com.conamobile.pdfkmp.viewer

/**
 * 200 MB cap for the bitmap cache on iOS. UIKit doesn't expose a
 * predictable per-app heap ceiling, so we pick a value comfortably
 * below the OOM-killer thresholds that ship on modern devices while
 * leaving the cache big enough for a few dozen A4 pages at 2× zoom.
 */
internal actual fun bitmapCacheBudgetBytes(): Long =
    200L * 1024L * 1024L
