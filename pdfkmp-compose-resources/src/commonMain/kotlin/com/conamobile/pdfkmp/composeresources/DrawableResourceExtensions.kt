package com.conamobile.pdfkmp.composeresources

import com.conamobile.pdfkmp.vector.VectorImage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Reads the bytes of a Compose Multiplatform [DrawableResource] from the
 * platform's default resource environment.
 *
 * Use this from a `suspend` context (a coroutine, a `LaunchedEffect`, or a
 * Swift `Task { }`), then hand the result to the PdfKmp DSL — for example
 * `image(bytes = Res.drawable.photo.toBytes(), width = 200.dp)`. Suitable
 * for raster formats accepted by `image(...)` (PNG, JPEG, and any format
 * the running platform decodes), and also for the raw XML / SVG payload
 * consumed by [toVectorImage].
 *
 * @receiver the typed `Res.drawable.*` reference produced by the Compose
 *   Multiplatform resources plugin.
 * @return the file's raw bytes exactly as packaged inside the consumer
 *   project's `composeResources/` tree.
 */
@OptIn(ExperimentalResourceApi::class)
public suspend fun DrawableResource.toBytes(): ByteArray =
    getDrawableResourceBytes(getSystemResourceEnvironment(), this)

/**
 * Loads a Compose Multiplatform [DrawableResource] containing Android
 * `<vector>` XML or W3C `<svg>` and parses it into a [VectorImage] ready
 * to be drawn by `vector(image = ...)`.
 *
 * Parse once, draw many times — when the same icon appears in multiple
 * places in the document, hold on to the returned [VectorImage] and pass
 * it to every `vector(...)` call site instead of re-parsing.
 *
 * Throws [com.conamobile.pdfkmp.vector.VectorParseException] when the
 * resource exists but isn't a valid Android-Vector / SVG payload.
 *
 * @receiver a `Res.drawable.*` reference pointing at an XML drawable.
 */
public suspend fun DrawableResource.toVectorImage(): VectorImage =
    VectorImage.parse(toBytes().decodeToString())

/**
 * Loads a Compose Multiplatform [DrawableResource] without caring whether
 * it's XML (parsed into a [VectorImage]) or a raster bitmap (kept as raw
 * bytes). The leading bytes of the file are inspected — anything that
 * looks like XML is parsed as a vector, everything else is returned as a
 * [PdfDrawable.Raster].
 *
 * Pair this with the `drawable(...)` DSL extension to render a resource
 * regardless of its underlying format:
 *
 * ```kotlin
 * val any = Res.drawable.icon_or_photo.toPdfDrawable()
 * pdf {
 *     page { drawable(any, width = 64.dp) }
 * }
 * ```
 *
 * Falls back to the [toBytes] / [toVectorImage] path when the call site
 * already knows which variant to expect.
 */
public suspend fun DrawableResource.toPdfDrawable(): PdfDrawable {
    val bytes = toBytes()
    return if (bytes.looksLikeXml()) {
        PdfDrawable.Vector(VectorImage.parse(bytes.decodeToString()))
    } else {
        PdfDrawable.Raster(bytes)
    }
}
