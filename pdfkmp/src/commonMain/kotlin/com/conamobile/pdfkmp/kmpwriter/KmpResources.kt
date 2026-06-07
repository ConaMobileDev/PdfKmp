package com.conamobile.pdfkmp.kmpwriter

/**
 * Per-page registry that hands the content stream stable resource names
 * (`/F0`, `/GS0`, `/Sh0`, `/Im0`) for every font face, alpha graphics state,
 * gradient shading, and image XObject it references, deduplicating so each
 * distinct resource is emitted once.
 *
 * The content stream can only refer to a resource by a name that the page's
 * `/Resources` dictionary maps to an object — but the objects themselves can't
 * be numbered until the whole document is being assembled. This registry bridges
 * the two phases: during drawing it assigns names and records the *definition*
 * of each resource (a face, an alpha value, a shading recipe, image bytes), and
 * at finish [KmpPdfDriver] turns those definitions into indirect objects and
 * builds the `/Resources` dictionary keyed by the same names.
 */
internal class KmpResources {

    /** Helvetica faces used on the page, in first-use order; index is the `/F<n>` suffix. */
    val fonts: MutableList<HelveticaFace> = ArrayList()
    private val fontNames = HashMap<HelveticaFace, String>()

    /** Distinct constant-alpha graphics states, keyed by rounded alpha value. */
    val alphaStates: MutableList<Float> = ArrayList()
    private val alphaNames = HashMap<Float, String>()

    /** Gradient shadings referenced by `sh`, in first-use order. */
    val shadings: MutableList<KmpShadingDef> = ArrayList()

    /** Image XObjects referenced by `Do`, in first-use order. */
    val images: MutableList<KmpImageDef> = ArrayList()

    /** Returns the `/F<n>` name for [face], registering it on first use. */
    fun fontName(face: HelveticaFace): String = fontNames.getOrPut(face) {
        val name = "F${fonts.size}"
        fonts.add(face)
        name
    }

    /**
     * Returns the `/GS<n>` name for a constant alpha, registering it on first
     * use. Alpha is rounded to three decimals so visually identical values share
     * one graphics state instead of multiplying near-duplicates.
     */
    fun alphaState(alpha: Float): String {
        val key = roundAlpha(alpha)
        return alphaNames.getOrPut(key) {
            val name = "GS${alphaStates.size}"
            alphaStates.add(key)
            name
        }
    }

    /** Registers [def] and returns its `/Sh<n>` name (shadings are never shared). */
    fun shadingName(def: KmpShadingDef): String {
        val name = "Sh${shadings.size}"
        shadings.add(def)
        return name
    }

    /** Registers [def] and returns its `/Im<n>` name (images are never shared). */
    fun imageName(def: KmpImageDef): String {
        val name = "Im${images.size}"
        images.add(def)
        return name
    }

    private fun roundAlpha(alpha: Float): Float {
        val clamped = alpha.coerceIn(0f, 1f)
        return (clamped * 1000f + 0.5f).toInt() / 1000f
    }
}

/**
 * A gradient shading recipe captured at draw time and serialised into a PDF
 * shading dictionary at finish. Coordinates are already in PDF bottom-left
 * space. [colorStops] are sorted by offset and treated as opaque (per-stop alpha
 * is folded into a constant graphics-state alpha by the caller, matching the JVM
 * backend's uniform-alpha handling).
 */
internal class KmpShadingDef(
    /** `true` for an axial (linear) shading, `false` for radial. */
    val axial: Boolean,
    /** Axial: [x0, y0, x1, y1]. Radial: [cx, cy, r]. */
    val coords: FloatArray,
    /** RGB triples (0..1) at each stop, in offset order. */
    val colors: List<FloatArray>,
    /** Stop offsets in 0..1, ascending, same length as [colors]. */
    val offsets: FloatArray,
)

/**
 * An image XObject recipe captured at draw time. [stream] is the raw,
 * already-encoded image data (JPEG bytes for `/DCTDecode`, concatenated PNG
 * IDAT bytes for `/FlateDecode`), and [dictionaryEntries] is the XObject
 * dictionary body *without* `/Length` (added by the writer from the stream
 * size). Passing the bytes through untouched keeps the embed lossless and the
 * writer wasm-friendly (no platform image codec needed).
 */
internal class KmpImageDef(
    val dictionaryEntries: String,
    val stream: ByteArray,
)
