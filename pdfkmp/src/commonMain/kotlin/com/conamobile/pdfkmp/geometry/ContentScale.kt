package com.conamobile.pdfkmp.geometry

/**
 * Strategy used to fit an image's intrinsic size into the requested
 * destination rectangle.
 *
 * Modeled after the Compose API of the same name so cross-domain Compose
 * developers can carry their intuition straight over to PdfKmp.
 */
public enum class ContentScale {

    /**
     * Scale the image so it fits inside the destination rectangle while
     * preserving its aspect ratio. The image is centered; any unused space
     * on one axis is left blank (letterboxing or pillarboxing). This is the
     * safe default for arbitrary photos.
     */
    Fit,

    /**
     * Scale the image so it fully covers the destination rectangle while
     * preserving its aspect ratio. Excess content on one axis is cropped
     * symmetrically. Useful for hero images and avatars where empty space
     * looks worse than a tight crop.
     */
    Crop,

    /**
     * Stretch the image to exactly match the destination rectangle, even if
     * that distorts its aspect ratio. Use only when the source is already
     * sized for the target rectangle (e.g. a generated chart) — otherwise
     * the image will look squished.
     */
    FillBounds,
}
