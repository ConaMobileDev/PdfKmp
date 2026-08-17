package com.conamobile.pdfkmp.image

/**
 * Tunables for how PdfKmp treats the encoded image bytes handed to
 * `image(bytes)`.
 *
 * The defaults are chosen for the untrusted case — a document assembled
 * from server-supplied or user-supplied images — so an app that only ever
 * embeds its own assets never has to touch this object. Apps that legitimately
 * embed very large imagery (A0 plans, high-resolution scans) raise the
 * ceiling once at startup:
 *
 * ```
 * PdfImagePolicy.maxDecodePixels = 250_000_000L
 * ```
 */
public object PdfImagePolicy {

    /**
     * Default value of [maxDecodePixels]: 50 megapixels, roughly 200 MB of
     * decoded ARGB pixels. Comfortably above any ordinary document
     * illustration and survivable on a phone.
     */
    public const val DEFAULT_MAX_DECODE_PIXELS: Long = 50_000_000L

    /**
     * Pixel ceiling applied to an image's *declared* header dimensions
     * before a decode allocates any pixel memory.
     *
     * A hostile file can claim enormous dimensions in a tiny header; without
     * a pre-decode ceiling the platform decoder obliges with a multi-gigabyte
     * allocation and kills the process. Rather than refuse such an image, every
     * backend sub-samples it down until it fits — so raising this value trades
     * peak memory for fidelity, and lowering it trades fidelity for headroom.
     *
     * @throws IllegalArgumentException when set to a non-positive value.
     */
    public var maxDecodePixels: Long = DEFAULT_MAX_DECODE_PIXELS
        set(value) {
            require(value > 0L) { "maxDecodePixels must be positive; got $value" }
            field = value
        }
}
