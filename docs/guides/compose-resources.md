# Compose Resources

The optional `io.github.conamobiledev:pdfkmp-compose-resources` module bridges
Compose Multiplatform's typed `Res.drawable.*` references straight into the PdfKmp
DSL — no manual byte loading, no `VectorImage.parse(...)` boilerplate. The module
is pure common, so it adds no platform code of its own.

Install it alongside the core dependency:

```kotlin
implementation("io.github.conamobiledev:pdfkmp:1.3.0")
implementation("io.github.conamobiledev:pdfkmp-compose-resources:1.3.0")
```

!!! warning "Build with `pdfAsync { }`"
    Every typed `Res.drawable.*` overload reads the resource bytes during the
    suspend **preflight pass** that `pdfAsync { }` runs before layout. The
    synchronous `pdf { }` entry point has no preflight pass and throws a clear
    error when it sees a deferred node, so build the document with
    [`pdfAsync`](../getting-started.md#pdf-vs-pdfasync) whenever you use these.

## Typed DSL overloads

`drawable(...)` auto-detects vector vs raster from the file's leading bytes, so
you don't have to know whether the asset is `<vector>` / `<svg>` XML or a PNG /
JPEG:

```kotlin
import com.conamobile.pdfkmp.pdfAsync
import com.conamobile.pdfkmp.composeresources.drawable

suspend fun buildReport(): PdfDocument = pdfAsync {
    page {
        drawable(Res.drawable.logo, width = 64.dp, tint = PdfColor.Blue)
        drawable(Res.drawable.cover_photo, width = 480.dp)
    }
}
```

`tint` / `strokeMode` take effect only when the resource turns out to be a
vector; `contentScale` only when it turns out to be a raster. The full signature:

```kotlin
fun ContainerScope.drawable(
    resource: DrawableResource,
    width: Dp? = null,
    height: Dp? = null,
    tint: PdfColor? = null,                              // vector only
    contentScale: ContentScale = ContentScale.Fit,      // raster only
    strokeMode: VectorStrokeMode = VectorStrokeMode.Inherit, // vector only
    allowDownScale: Boolean = true,                     // raster only
)
```

When you already know the variant, reach for the type-specific overloads:

```kotlin
import com.conamobile.pdfkmp.composeresources.vector
import com.conamobile.pdfkmp.composeresources.image

vector(Res.drawable.star, width = 48.dp, tint = PdfColor.Red)
image(Res.drawable.banner, width = 480.dp, contentScale = ContentScale.Crop)
```

## Loading bytes yourself

When you'd rather pull the raw bytes (or a parsed `VectorImage`) out and feed
them to the core `image(...)` / `vector(...)` DSL — for example to parse a vector
once and draw it many times — three `suspend` extensions on `DrawableResource`
do that:

| Extension | Returns | Use for |
|---|---|---|
| `toBytes()` | `ByteArray` | Raster bytes for `image(bytes = …)`, or raw XML for `toVectorImage`. |
| `toVectorImage()` | `VectorImage` | Parse a `<vector>` / `<svg>` resource once, reuse across call sites. |
| `toPdfDrawable()` | `PdfDrawable` | Format-agnostic — sniffs the leading bytes and returns `Vector` or `Raster`. |

```kotlin
import com.conamobile.pdfkmp.composeresources.toBytes
import com.conamobile.pdfkmp.composeresources.toVectorImage
import com.conamobile.pdfkmp.composeresources.toPdfDrawable

// Inside a coroutine / LaunchedEffect / Swift Task { } — these are suspend:
val logo = Res.drawable.logo.toVectorImage()   // parse once
val photoBytes = Res.drawable.photo.toBytes()
val either = Res.drawable.icon_or_photo.toPdfDrawable()

val doc = pdf {
    page {
        vector(image = logo, width = 64.dp)        // core DSL, synchronous
        image(bytes = photoBytes, width = 200.dp)
        drawable(either, width = 64.dp)            // eager PdfDrawable overload — no preflight needed
    }
}
```

!!! note "Eager `drawable(PdfDrawable)` works inside `pdf { }`"
    Once you've resolved a resource into a `PdfDrawable` yourself (via
    `toPdfDrawable()` from a coroutine), the eager `drawable(drawable, …)`
    overload embeds it with no deferred node — so it works inside the synchronous
    `pdf { }`. Only the **typed `Res.drawable.*`** overloads require `pdfAsync`.

`toVectorImage()` throws `VectorParseException` when the resource exists but isn't
a valid Android-Vector / SVG payload.

## See also

- [Images & vectors](images-vectors.md) — the core `image(...)` / `vector(...)` DSL.
- [Getting started](../getting-started.md#pdf-vs-pdfasync) — `pdf { }` vs `pdfAsync { }`.
