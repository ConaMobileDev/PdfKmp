package com.conamobile.pdfkmp.viewer

import kotlin.random.Random

/**
 * Process-local registry that lets [KmpPdfLauncher] hand a non-
 * primitive [PdfSource] (a `PdfDocument`'s text runs / hyperlinks,
 * a large byte array, …) across the imperative → hosted-screen hop
 * without serialising it through Intent extras (Android) or
 * userInfo dictionaries (iOS).
 *
 * The launcher [put]s the payload, gets a short token, and embeds
 * the token in the platform-native launch primitive. The hosted
 * shell [take]s the payload back when its content composes.
 *
 * Tokens are one-shot — `take` removes the entry — so the registry
 * stays bounded even if the user kills the hosted screen mid-render.
 * If a payload is never claimed (the launch was cancelled before
 * the activity / view-controller mounted) it lingers until the
 * process dies; this is acceptable because the per-payload weight
 * is tiny (one [PdfSource] holding bytes the caller already had).
 */
internal object KmpPdfLauncherRegistry {

    // No mutex: every call site we ship runs on the platform's UI
    // thread (Android main, iOS main run-loop). If a future caller
    // wants to put / take from a worker thread they should marshal
    // back to the main dispatcher first.
    private val payloads = mutableMapOf<String, PdfSource>()

    /** Stores [source] and returns the lookup token to embed in the launch. */
    fun put(source: PdfSource): String {
        val token = "kpdf-${Random.nextLong().toString(radix = 36)}"
        payloads[token] = source
        return token
    }

    /** Pops the payload paired with [token], or `null` if it isn't registered. */
    fun take(token: String): PdfSource? = payloads.remove(token)
}
