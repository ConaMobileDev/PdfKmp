package com.conamobile.pdfkmp.image

/**
 * Upper bound on the sub-sample factor any backend will apply. Reaching it
 * means the declared dimensions are so far past the budget that no
 * power-of-two reduction helps; the guard exists so a hostile header can
 * never spin the doubling loop or overflow the factor.
 */
private const val MAX_SAMPLE_FACTOR: Int = 1 shl 16

/**
 * Smallest power-of-two sub-sample factor that brings a [widthPx] × [heightPx]
 * decode under [PdfImagePolicy.maxDecodePixels]. Returns `1` when the image
 * already fits, so callers can pass the result to their platform decoder
 * unconditionally.
 *
 * Every backend that owns a real decoder routes through this instead of
 * refusing an oversized image: a 2.5-gigapixel dimension bomb collapses into
 * a few hundred harmless pixels, while a legitimate 139-megapixel A0 scan
 * still renders — just sampled. Refusing on one platform and sampling on
 * another would make the same document produce different pages, which is
 * exactly what a multiplatform renderer exists to prevent.
 */
internal fun decodeSampleFactorFor(widthPx: Int, heightPx: Int): Int {
    if (widthPx <= 0 || heightPx <= 0) return 1
    val budget = PdfImagePolicy.maxDecodePixels
    var sample = 1
    while (
        sample < MAX_SAMPLE_FACTOR &&
        (widthPx / sample).toLong() * (heightPx / sample).toLong() > budget
    ) {
        sample *= 2
    }
    return sample
}

/**
 * Longest edge a decode may produce while staying inside
 * [PdfImagePolicy.maxDecodePixels], for decoders that take a
 * "largest dimension" bound rather than a sub-sample factor (ImageIO's
 * thumbnail API on Apple platforms).
 *
 * Derived from the sub-sample factor so the two spellings agree: whatever
 * [decodeSampleFactorFor] would have produced is what this bound reproduces.
 */
internal fun maxDecodeEdgeFor(widthPx: Int, heightPx: Int): Int {
    val sample = decodeSampleFactorFor(widthPx, heightPx)
    val longest = maxOf(widthPx, heightPx)
    return (longest / sample).coerceAtLeast(1)
}

/**
 * Returns `true` when [bytes] carry a recognizable image header whose
 * declared dimensions exceed [PdfImagePolicy.maxDecodePixels].
 *
 * Only for backends that *cannot* sample — the pure-Kotlin writer embeds
 * an encoded stream verbatim and owns no decoder, so refusing is the only
 * lever it has. Backends with a decoder call [decodeSampleFactorFor]
 * instead. Unrecognized headers return `false`; the platform decoder
 * remains the last line of defense for formats [readImageInfo] cannot parse.
 */
internal fun exceedsDecodeBudget(bytes: ByteArray): Boolean {
    val info = readImageInfo(bytes) ?: return false
    return info.widthPx.toLong() * info.heightPx.toLong() > PdfImagePolicy.maxDecodePixels
}
