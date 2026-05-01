package com.conamobile.pdfkmp.composeresources

import com.conamobile.pdfkmp.dsl.ContainerScope
import com.conamobile.pdfkmp.geometry.ContentScale
import com.conamobile.pdfkmp.node.ImageNode
import com.conamobile.pdfkmp.node.LazyNode
import com.conamobile.pdfkmp.node.VectorNode
import com.conamobile.pdfkmp.node.VectorStrokeMode
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.Dp
import org.jetbrains.compose.resources.DrawableResource

/**
 * Embeds a [PdfDrawable] in the document, dispatching to `vector(...)`
 * or `image(...)` based on the variant. Useful when the call site loads
 * a resource via [DrawableResource.toPdfDrawable] without knowing in
 * advance whether the asset is XML or a raster.
 *
 * Parameters that apply to only one variant are silently ignored for
 * the other — [tint] and [strokeMode] take effect for vector drawables
 * only, and [contentScale] takes effect for raster drawables only.
 *
 * @param drawable the resource loaded via [toPdfDrawable].
 * @param width rendered width on the page; `null` lets the engine
 *   derive it from intrinsic dimensions (vector) or from the supplied
 *   [height] (raster, when [height] is non-null).
 * @param height same logic mirrored for the vertical axis.
 * @param tint vector-only — uniform colour applied to every fill.
 * @param contentScale raster-only — how the bitmap fills the box.
 *   Defaults to [ContentScale.Fit].
 * @param strokeMode vector-only — whether the stroke is inherited,
 *   suppressed, or recoloured at draw time.
 */
public fun ContainerScope.drawable(
    drawable: PdfDrawable,
    width: Dp? = null,
    height: Dp? = null,
    tint: PdfColor? = null,
    contentScale: ContentScale = ContentScale.Fit,
    strokeMode: VectorStrokeMode = VectorStrokeMode.Inherit,
    allowDownScale: Boolean = true,
) {
    when (drawable) {
        is PdfDrawable.Vector -> vector(
            image = drawable.image,
            width = width,
            height = height,
            tint = tint,
            strokeMode = strokeMode,
        )

        is PdfDrawable.Raster -> when {
            width != null && height != null -> image(
                bytes = drawable.bytes,
                width = width,
                height = height,
                contentScale = contentScale,
                allowDownScale = allowDownScale,
            )

            width != null -> image(
                bytes = drawable.bytes,
                width = width,
                contentScale = contentScale,
                allowDownScale = allowDownScale,
            )

            else -> image(
                bytes = drawable.bytes,
                contentScale = contentScale,
                allowDownScale = allowDownScale,
            )
        }
    }
}

/**
 * Embeds a typed Compose Multiplatform [DrawableResource] inline inside
 * the synchronous `pdf { }` DSL — auto-detects vector vs raster from the
 * file's leading bytes at preflight time.
 *
 * Returns immediately. The resource bytes are read during the suspend
 * preflight pass that `pdfAsync { }` runs before layout, so the call
 * site never has to be `suspend` itself. **Build the document with
 * [com.conamobile.pdfkmp.pdfAsync] instead of
 * [com.conamobile.pdfkmp.pdf]** when using this overload — the
 * synchronous entry point has no preflight pass and throws a clear
 * error when it sees a deferred node.
 *
 * Parameter semantics match the eager [drawable] overload above —
 * [tint] / [strokeMode] apply only when the resource turns out to be a
 * vector, [contentScale] only when it turns out to be a raster.
 *
 * @param resource typed `Res.drawable.*` reference produced by the
 *   Compose Multiplatform Resources plugin in the consumer project.
 */
public fun ContainerScope.drawable(
    resource: DrawableResource,
    width: Dp? = null,
    height: Dp? = null,
    tint: PdfColor? = null,
    contentScale: ContentScale = ContentScale.Fit,
    strokeMode: VectorStrokeMode = VectorStrokeMode.Inherit,
    allowDownScale: Boolean = true,
) {
    addNode(LazyNode {
        when (val pd = resource.toPdfDrawable()) {
            is PdfDrawable.Vector -> VectorNode(
                image = pd.image,
                width = width,
                height = height,
                tint = tint,
                strokeOverride = strokeMode,
            )

            is PdfDrawable.Raster -> ImageNode(
                bytes = pd.bytes,
                width = width,
                height = height,
                contentScale = contentScale,
                allowDownScale = allowDownScale,
            )
        }
    })
}

/**
 * Inline overload of `vector(...)` that takes a typed [DrawableResource]
 * pointing at `<vector>` or `<svg>` XML. Resolves through the suspend
 * preflight pass — call inside [com.conamobile.pdfkmp.pdfAsync].
 *
 * Use this when you know the resource is XML and want vector-specific
 * options ([tint], [strokeMode]) at the call site without the auto-detect
 * machinery from [drawable].
 */
public fun ContainerScope.vector(
    resource: DrawableResource,
    width: Dp? = null,
    height: Dp? = null,
    tint: PdfColor? = null,
    strokeMode: VectorStrokeMode = VectorStrokeMode.Inherit,
) {
    addNode(LazyNode {
        VectorNode(
            image = resource.toVectorImage(),
            width = width,
            height = height,
            tint = tint,
            strokeOverride = strokeMode,
        )
    })
}

/**
 * Inline overload of `image(...)` that takes a typed [DrawableResource]
 * pointing at a raster bitmap (PNG / JPEG / WEBP / HEIF). Resolves through
 * the suspend preflight pass — call inside [com.conamobile.pdfkmp.pdfAsync].
 */
public fun ContainerScope.image(
    resource: DrawableResource,
    width: Dp? = null,
    height: Dp? = null,
    contentScale: ContentScale = ContentScale.Fit,
    allowDownScale: Boolean = true,
) {
    addNode(LazyNode {
        ImageNode(
            bytes = resource.toBytes(),
            width = width,
            height = height,
            contentScale = contentScale,
            allowDownScale = allowDownScale,
        )
    })
}
